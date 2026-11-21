package com.api.finance.transaction.service;

import com.api.finance.account.exception.AccountNotFoundException;
import com.api.finance.account.model.Account;
import com.api.finance.account.repository.AccountRepository;
import com.api.finance.budget.service.BudgetService;
import com.api.finance.category.model.Category;
import com.api.finance.category.repository.CategoryRepository;
import com.api.finance.category.service.CategoryService;
import com.api.finance.config.AuthenticatedUser;
import com.api.finance.events.FinanceEvents;
import com.api.finance.shared.exception.ResourceNotFoundException;
import com.api.finance.subscription.service.SubscriptionService;
import com.api.finance.transaction.dto.CreateTransactionRequest;
import com.api.finance.transaction.domain.TransactionFingerprint;
import com.api.finance.transaction.dto.TransactionResponse;
import com.api.finance.transaction.dto.UpdateTransactionRequest;
import com.api.finance.transaction.exception.TransactionNotFoundException;
import com.api.finance.transaction.exception.DuplicateTransactionException;
import com.api.finance.transaction.model.Transaction;
import com.api.finance.transaction.model.TransactionStatus;
import com.api.finance.transaction.model.TransactionType;
import com.api.finance.transaction.repository.TransactionRepository;
import com.api.finance.user.application.port.UserDirectoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryService categoryService;
    private final UserDirectoryPort userDirectory;
    private final SubscriptionService subscriptionService;
    private final ApplicationEventPublisher eventPublisher;  // NOVO
    private final BudgetService budgetService;  // FIX: recalcula orçamento após mudanças em transações

    @Transactional(readOnly = true)
    public Page<TransactionResponse> listar(LocalDate de, LocalDate ate, Pageable pageable, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);
        LocalDate inicio = de != null ? de : LocalDate.now().withDayOfMonth(1);
        LocalDate fim = ate != null ? ate : LocalDate.now();
        return transactionRepository.findByUserIdAndPeriodo(userId, inicio, fim, pageable)
                .map(TransactionResponse::de);
    }

    @Transactional(readOnly = true)
    public TransactionResponse buscarPorId(UUID id, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);
        return transactionRepository.findByIdAndUserId(id, userId)
                .map(TransactionResponse::de)
                .orElseThrow(() -> new TransactionNotFoundException("Transação não encontrada: " + id));
    }

    @Transactional
    public TransactionResponse criar(CreateTransactionRequest req, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);

        LocalDate inicioMes = LocalDate.now().withDayOfMonth(1);
        LocalDate fimMes = inicioMes.withDayOfMonth(inicioMes.lengthOfMonth());
        long transacoesNoMes = transactionRepository.countByUserIdAndMes(userId, inicioMes, fimMes);
        subscriptionService.assertPodeCriarTransacao(userId, transacoesNoMes);

        UUID destinationAccountId = resolveDestinationAccountId(
                req.tipo(), req.accountId(), req.contaDestinoId());
        Map<UUID, Account> lockedAccounts = lockAccounts(userId, req.accountId(), destinationAccountId);
        Account account = lockedAccounts.get(req.accountId());
        Account destinationAccount = destinationAccountId == null ? null : lockedAccounts.get(destinationAccountId);

        if (!account.isActive()) {
            throw new IllegalArgumentException("Não é possível lançar transações em uma conta inativa.");
        }
        if (destinationAccount != null) {
            validarTransferenciaEntreContas(account, destinationAccount);
        }

        String fingerprint = TransactionFingerprint.of(
                userId, req.accountId(), req.valor(), req.data(), req.descricao()
        ).value();

        Category category = resolveCategoria(req.categoryId(), req.estabelecimento(), userId);
        TransactionStatus status = req.status() != null ? req.status() : TransactionStatus.CONFIRMADA;
        UUID transactionId = UUID.randomUUID();

        if (transactionRepository.existsByHashDeduplicacao(fingerprint)) {
            throw new DuplicateTransactionException();
        }

        boolean destinationBalanceApplied =
                destinationAccountId != null && status == TransactionStatus.CONFIRMADA;

        int inserted = transactionRepository.insertIfFingerprintAbsent(
                transactionId,
                userId,
                account.getId(),
                destinationAccountId,
                category != null ? category.getId() : null,
                req.tipo().name(),
                status.name(),
                req.valor(),
                req.descricao(),
                req.estabelecimento(),
                req.data(),
                fingerprint,
                destinationBalanceApplied
        );

        if (inserted == 0) {
            throw new IllegalStateException("Transação duplicada detectada.");
        }

        // Origem e destino são bloqueados em ordem determinística e atualizados
        // na mesma transação SQL do lançamento.
        if (status == TransactionStatus.CONFIRMADA) {
            ajustarSaldo(account, req.tipo(), req.valor());
            accountRepository.save(account);

            if (destinationBalanceApplied) {
                destinationAccount.setBalance(destinationAccount.getBalance().add(req.valor()));
                accountRepository.save(destinationAccount);
            }
        }

        Transaction saved = transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new IllegalStateException("Transação inserida, mas não foi possível recuperá-la."));
        log.info("Transação criada: id={} user={} tipo={}", saved.getId(), userId, req.tipo());

        String keycloakId = caller.id().toString();

        if (status == TransactionStatus.PENDENTE) {
            eventPublisher.publishEvent(new FinanceEvents.TransacaoPendenteEvent(
                    keycloakId, userId, saved.getId(), req.descricao(), req.valor()
            ));
        }

        if (status == TransactionStatus.CONFIRMADA
                && account.getBalance().compareTo(BigDecimal.ZERO) < 0) {
            eventPublisher.publishEvent(new FinanceEvents.ContaSaldoNegativoEvent(
                    keycloakId, userId, account.getId(), account.getName(), account.getBalance()
            ));
        }

        recalcularOrcamentoSeNecessario(userId, req.tipo(), status, req.data());
        return TransactionResponse.de(saved);
    }

    @Transactional
    public TransactionResponse atualizar(UUID id, UpdateTransactionRequest req, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);

        Transaction trx = transactionRepository.findByIdAndUserIdForUpdate(id, userId)
                .orElseThrow(() -> new TransactionNotFoundException("Transação não encontrada: " + id));

        boolean previousDestinationApplied = trx.isTransferDestinationApplied();
        if (previousDestinationApplied
                && (trx.getTipo() != TransactionType.TRANSFERENCIA
                || trx.getContaDestinoId() == null
                || trx.getContaDestinoId().equals(trx.getAccountId())
                || trx.getStatus() != TransactionStatus.CONFIRMADA)) {
            throw new IllegalStateException("Estado de saldo da transferência inconsistente; atualização bloqueada.");
        }

        UUID newDestinationAccountId = resolveDestinationAccountId(
                req.tipo(), trx.getAccountId(), trx.getContaDestinoId());
        UUID destinationAccountIdToLock = previousDestinationApplied
                ? trx.getContaDestinoId()
                : newDestinationAccountId;

        Map<UUID, Account> lockedAccounts = lockAccounts(
                userId, trx.getAccountId(), destinationAccountIdToLock);
        Account account = lockedAccounts.get(trx.getAccountId());
        Account destinationAccount = destinationAccountIdToLock == null
                ? null
                : lockedAccounts.get(destinationAccountIdToLock);

        if (newDestinationAccountId != null) {
            validarTransferenciaEntreContas(account, destinationAccount);
        }

        TransactionType tipoAntigo = trx.getTipo();
        TransactionStatus statusAntigo = trx.getStatus();
        LocalDate dataAntiga = trx.getData();

        if (statusAntigo == TransactionStatus.CONFIRMADA) {
            reverterSaldo(account, tipoAntigo, trx.getValor());
            if (previousDestinationApplied) {
                destinationAccount.setBalance(destinationAccount.getBalance().subtract(trx.getValor()));
            }
        }

        Category category = resolveCategoria(req.categoryId(), req.estabelecimento(), userId);
        trx.setCategory(category);
        trx.setTipo(req.tipo());
        trx.setStatus(req.status());
        trx.setValor(req.valor());
        trx.setDescricao(req.descricao());
        trx.setEstabelecimento(req.estabelecimento());
        trx.setData(req.data());

        if (req.status() == TransactionStatus.CONFIRMADA) {
            ajustarSaldo(account, req.tipo(), req.valor());
            if (newDestinationAccountId != null) {
                destinationAccount.setBalance(destinationAccount.getBalance().add(req.valor()));
            }
        }

        trx.setTransferDestinationApplied(
                req.status() == TransactionStatus.CONFIRMADA && newDestinationAccountId != null);

        if (statusAntigo == TransactionStatus.CONFIRMADA
                || req.status() == TransactionStatus.CONFIRMADA) {
            accountRepository.save(account);
        }
        if (destinationAccount != null
                && (previousDestinationApplied
                || (req.status() == TransactionStatus.CONFIRMADA && newDestinationAccountId != null))) {
            accountRepository.save(destinationAccount);
        }

        Transaction saved = transactionRepository.save(trx);
        String keycloakId = caller.id().toString();

        if (req.status() == TransactionStatus.PENDENTE) {
            eventPublisher.publishEvent(new FinanceEvents.TransacaoPendenteEvent(
                    keycloakId, userId, saved.getId(), req.descricao(), req.valor()
            ));
        }

        if (req.status() == TransactionStatus.CONFIRMADA
                && account.getBalance().compareTo(BigDecimal.ZERO) < 0) {
            eventPublisher.publishEvent(new FinanceEvents.ContaSaldoNegativoEvent(
                    keycloakId, userId, account.getId(), account.getName(), account.getBalance()
            ));
        }

        recalcularOrcamentoSeNecessario(userId, tipoAntigo, statusAntigo, dataAntiga);
        recalcularOrcamentoSeNecessario(userId, req.tipo(), req.status(), req.data());
        return TransactionResponse.de(saved);
    }

    @Transactional
    public void deletar(UUID id, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);

        Transaction trx = transactionRepository.findByIdAndUserIdForUpdate(id, userId)
                .orElseThrow(() -> new TransactionNotFoundException("Transação não encontrada: " + id));

        if (trx.isTransferDestinationApplied()
                && (trx.getTipo() != TransactionType.TRANSFERENCIA
                || trx.getContaDestinoId() == null
                || trx.getContaDestinoId().equals(trx.getAccountId()))) {
            throw new IllegalStateException("Estado de saldo da transferência inconsistente; exclusão bloqueada.");
        }

        if (trx.getStatus() == TransactionStatus.CONFIRMADA) {
            UUID destinationId = trx.isTransferDestinationApplied() ? trx.getContaDestinoId() : null;
            Map<UUID, Account> lockedAccounts = lockAccounts(userId, trx.getAccountId(), destinationId);
            Account account = lockedAccounts.get(trx.getAccountId());
            reverterSaldo(account, trx.getTipo(), trx.getValor());
            accountRepository.save(account);

            if (destinationId != null) {
                Account destinationAccount = lockedAccounts.get(destinationId);
                destinationAccount.setBalance(destinationAccount.getBalance().subtract(trx.getValor()));
                accountRepository.save(destinationAccount);
            }
        }

        transactionRepository.delete(trx);
        log.info("Transação removida: id={} user={}", id, userId);
        recalcularOrcamentoSeNecessario(userId, trx.getTipo(), trx.getStatus(), trx.getData());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * FIX: Budget.valorGasto é um campo persistido, não calculado on-the-fly —
     * por isso toda mudança em transação de DESPESA precisa recalcular o orçamento
     * do mês/ano afetado, ou o valorGasto fica desatualizado e os alertas de
     * orçamento (BudgetAlertScheduler) nunca disparam corretamente.
     *
     * Só recalcula para DESPESA, já que orçamentos só somam despesas confirmadas
     * (ver TransactionRepository.sumDespesaByCategoria).
     */
    private void recalcularOrcamentoSeNecessario(UUID userId, TransactionType tipo, TransactionStatus status, LocalDate data) {
        if (tipo == TransactionType.DESPESA && status == TransactionStatus.CONFIRMADA && data != null) {
            budgetService.recalcularGastos(userId, data.getMonthValue(), data.getYear());
        }
    }

    private UUID resolveDestinationAccountId(
            TransactionType type, UUID sourceAccountId, UUID destinationAccountId) {
        if (type != TransactionType.TRANSFERENCIA) {
            return null;
        }
        if (destinationAccountId == null) {
            throw new IllegalArgumentException("Conta de destino é obrigatória para transferências.");
        }
        if (destinationAccountId.equals(sourceAccountId)) {
            throw new IllegalArgumentException("A conta de origem e destino da transferência devem ser diferentes.");
        }
        return destinationAccountId;
    }

    /**
     * Bloqueia todas as contas financeiras em ordem determinística para reduzir
     * deadlocks em transferências simultâneas entre as mesmas contas.
     */
    private void validarTransferenciaEntreContas(Account origem, Account destino) {
        if (destino == null || !destino.isActive()) {
            throw new IllegalArgumentException("A conta de destino deve existir e estar ativa.");
        }
        if (origem.getCurrency() == null || destino.getCurrency() == null
                || !origem.getCurrency().equalsIgnoreCase(destino.getCurrency())) {
            throw new IllegalArgumentException("Transferências exigem contas na mesma moeda.");
        }
    }

    private Map<UUID, Account> lockAccounts(UUID userId, UUID... accountIds) {
        if (accountIds.length == 0 || accountIds[0] == null) {
            throw new IllegalArgumentException("Conta de origem é obrigatória.");
        }

        TreeSet<UUID> orderedIds = new TreeSet<>();
        for (UUID accountId : accountIds) {
            if (accountId != null) {
                orderedIds.add(accountId);
            }
        }

        Map<UUID, Account> accounts = new HashMap<>();
        for (UUID accountId : orderedIds) {
            Account account = accountRepository.findByIdAndUserIdForUpdate(accountId, userId)
                    .orElseThrow(() -> new AccountNotFoundException("Conta não encontrada: " + accountId));
            accounts.put(accountId, account);
        }
        return accounts;
    }

    private void ajustarSaldo(Account account, TransactionType tipo, BigDecimal valor) {
        if (tipo == TransactionType.RECEITA) {
            account.setBalance(account.getBalance().add(valor));
        } else {
            account.setBalance(account.getBalance().subtract(valor));
        }
    }

    private void reverterSaldo(Account account, TransactionType tipo, BigDecimal valor) {
        ajustarSaldo(account,
                tipo == TransactionType.RECEITA ? TransactionType.DESPESA : TransactionType.RECEITA,
                valor);
    }

    private Category resolveCategoria(UUID categoryId, String estabelecimento, UUID userId) {
        if (categoryId != null) {
            return categoryRepository.findByIdForUser(categoryId, userId).orElse(null);
        }
        return categoryService.categorizar(estabelecimento, userId).orElse(null);
    }

    private UUID resolveUserId(AuthenticatedUser caller) {
        return userDirectory.findInternalUserId(caller.id())
                .orElseThrow(() -> ResourceNotFoundException.of("User", caller.id()));
    }


}