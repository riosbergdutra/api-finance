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
import com.api.finance.transaction.model.Transaction;
import com.api.finance.transaction.model.TransactionStatus;
import com.api.finance.transaction.model.TransactionType;
import com.api.finance.transaction.repository.TransactionRepository;
import com.api.finance.user.model.User;
import com.api.finance.user.repository.UserRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryService categoryService;
    private final UserRepository userRepository;
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
        User user = resolveUser(caller);

        // Verifica limite do plano FREE
        LocalDate inicioMes = LocalDate.now().withDayOfMonth(1);
        LocalDate fimMes    = inicioMes.withDayOfMonth(inicioMes.lengthOfMonth());
        long transacoesNoMes = transactionRepository.countByUserIdAndMes(userId, inicioMes, fimMes);
        subscriptionService.assertPodeCriarTransacao(userId, transacoesNoMes);

        // Garante ownership da conta
        Account account = accountRepository.findByIdAndUserId(req.accountId(), userId)
                .orElseThrow(() -> new AccountNotFoundException("Conta não encontrada: " + req.accountId()));

        // Impressão digital de domínio: SHA-256 determinístico e sem fallback aleatório.
        String fingerprint = TransactionFingerprint.of(
                userId, req.accountId(), req.valor(), req.data(), req.descricao()
        ).value();

        Category category = resolveCategoria(req.categoryId(), req.estabelecimento(), userId);
        TransactionStatus status = req.status() != null ? req.status() : TransactionStatus.CONFIRMADA;
        UUID transactionId = UUID.randomUUID();

        // O INSERT ... ON CONFLICT é a garantia atômica no PostgreSQL.
        // A consulta prévia fornece uma mensagem amigável no caminho comum;
        // o índice UNIQUE continua protegendo a corrida entre requisições.
        if (transactionRepository.existsByHashDeduplicacao(fingerprint)) {
            throw new IllegalStateException("Transação duplicada detectada.");
        }

        int inserted = transactionRepository.insertIfFingerprintAbsent(
                transactionId,
                userId,
                account.getId(),
                req.tipo() == TransactionType.TRANSFERENCIA ? req.contaDestinoId() : null,
                category != null ? category.getId() : null,
                req.tipo().name(),
                status.name(),
                req.valor(),
                req.descricao(),
                req.estabelecimento(),
                req.data(),
                fingerprint
        );

        if (inserted == 0) {
            throw new IllegalStateException("Transação duplicada detectada.");
        }

        // Efeitos financeiros só acontecem depois de confirmar que o INSERT venceu.
        // Tudo permanece na mesma transação SQL: falha no saldo reverte também o INSERT.
        if (status == TransactionStatus.CONFIRMADA) {
            ajustarSaldo(account, req.tipo(), req.valor());
            accountRepository.save(account);
        }

        Transaction saved = transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new IllegalStateException("Transação inserida, mas não foi possível recuperá-la."));
        log.info("Transação criada: id={} user={} tipo={}", saved.getId(), userId, req.tipo());

        // FIX: eventos publicados APÓS salvar — listener executa só após commit
        String keycloakId = user.getKeycloakId().toString();

        if (status == TransactionStatus.PENDENTE) {
            eventPublisher.publishEvent(new FinanceEvents.TransacaoPendenteEvent(
                    keycloakId, userId, saved.getId(), req.descricao(), req.valor()
            ));
        }

        if (status == TransactionStatus.CONFIRMADA && account.getBalance().compareTo(BigDecimal.ZERO) < 0) {
            eventPublisher.publishEvent(new FinanceEvents.ContaSaldoNegativoEvent(
                    keycloakId, userId, account.getId(), account.getName(), account.getBalance()
            ));
        }

        // FIX: orçamentos guardam valorGasto persistido (não é calculado on-the-fly),
        // então toda mudança em transação precisa recalcular o mês/ano afetado.
        recalcularOrcamentoSeNecessario(userId, req.tipo(), status, req.data());

        return TransactionResponse.de(saved);
    }

    @Transactional
    public TransactionResponse atualizar(UUID id, UpdateTransactionRequest req, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);
        User user = resolveUser(caller);

        Transaction trx = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new TransactionNotFoundException("Transação não encontrada: " + id));

        // Reverte saldo anterior se estava confirmada
        Account account = accountRepository.findByIdAndUserId(trx.getAccountId(), userId)
                .orElseThrow(() -> new AccountNotFoundException("Conta não encontrada"));

        if (trx.getStatus() == TransactionStatus.CONFIRMADA) {
            reverterSaldo(account, trx.getTipo(), trx.getValor());
            accountRepository.save(account);
        }

        // Guarda estado anterior para recalcular o orçamento do período antigo também,
        // caso a transação tenha mudado de mês/tipo/status na edição.
        TransactionType tipoAntigo = trx.getTipo();
        TransactionStatus statusAntigo = trx.getStatus();
        LocalDate dataAntiga = trx.getData();

        Category category = resolveCategoria(req.categoryId(), req.estabelecimento(), userId);
        trx.setCategory(category);
        trx.setTipo(req.tipo());
        trx.setStatus(req.status());
        trx.setValor(req.valor());
        trx.setDescricao(req.descricao());
        trx.setEstabelecimento(req.estabelecimento());
        trx.setData(req.data());

        // Aplica novo saldo se confirmada
        if (req.status() == TransactionStatus.CONFIRMADA) {
            ajustarSaldo(account, req.tipo(), req.valor());
            accountRepository.save(account);
        }

        Transaction saved = transactionRepository.save(trx);

        // FIX: eventos de atualização
        String keycloakId = user.getKeycloakId().toString();

        if (req.status() == TransactionStatus.PENDENTE) {
            eventPublisher.publishEvent(new FinanceEvents.TransacaoPendenteEvent(
                    keycloakId, userId, saved.getId(), req.descricao(), req.valor()
            ));
        }

        if (req.status() == TransactionStatus.CONFIRMADA && account.getBalance().compareTo(BigDecimal.ZERO) < 0) {
            eventPublisher.publishEvent(new FinanceEvents.ContaSaldoNegativoEvent(
                    keycloakId, userId, account.getId(), account.getName(), account.getBalance()
            ));
        }

        // FIX: recalcula o orçamento do período antigo (caso a transação tenha "saído" dele)
        // e do período novo (caso tenha "entrado" nele ou apenas mudado de valor/categoria).
        recalcularOrcamentoSeNecessario(userId, tipoAntigo, statusAntigo, dataAntiga);
        recalcularOrcamentoSeNecessario(userId, req.tipo(), req.status(), req.data());

        return TransactionResponse.de(saved);
    }

    @Transactional
    public void deletar(UUID id, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);

        Transaction trx = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new TransactionNotFoundException("Transação não encontrada: " + id));

        if (trx.getStatus() == TransactionStatus.CONFIRMADA) {
            accountRepository.findByIdAndUserId(trx.getAccountId(), userId).ifPresent(acc -> {
                reverterSaldo(acc, trx.getTipo(), trx.getValor());
                accountRepository.save(acc);
            });
        }

        transactionRepository.delete(trx);
        log.info("Transação removida: id={} user={}", id, userId);

        // FIX: o orçamento do período da transação removida precisa refletir a remoção.
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
        return userRepository.findIdByKeycloakId(caller.id())
                .orElseThrow(() -> ResourceNotFoundException.of("User", caller.id()));
    }

    private User resolveUser(AuthenticatedUser caller) {
        return userRepository.findByKeycloakId(caller.id())
                .orElseThrow(() -> ResourceNotFoundException.of("User", caller.id()));
    }
}