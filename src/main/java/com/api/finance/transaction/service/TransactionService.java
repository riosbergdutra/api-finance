package com.api.finance.transaction.service;

import com.api.finance.account.exception.AccountNotFoundException;
import com.api.finance.account.model.Account;
import com.api.finance.account.repository.AccountRepository;
import com.api.finance.category.model.Category;
import com.api.finance.category.repository.CategoryRepository;
import com.api.finance.category.service.CategoryService;
import com.api.finance.config.AuthenticatedUser;
import com.api.finance.shared.exception.ResourceNotFoundException;
import com.api.finance.transaction.dto.CreateTransactionRequest;
import com.api.finance.transaction.dto.TransactionResponse;
import com.api.finance.transaction.dto.UpdateTransactionRequest;
import com.api.finance.transaction.exception.TransactionNotFoundException;
import com.api.finance.transaction.model.Transaction;
import com.api.finance.transaction.model.TransactionStatus;
import com.api.finance.transaction.model.TransactionType;
import com.api.finance.transaction.repository.TransactionRepository;
import com.api.finance.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
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

        // Garante ownership da conta
        Account account = accountRepository.findByIdAndUserId(req.accountId(), userId)
                .orElseThrow(() -> new AccountNotFoundException("Conta não encontrada: " + req.accountId()));

        // Deduplicação
        String hash = gerarHash(userId, req.accountId(), req.valor(), req.data(), req.descricao());
        if (transactionRepository.existsByHashDeduplicacao(hash)) {
            throw new IllegalStateException("Transação duplicada detectada.");
        }

        // Categorização automática se não veio category
        Category category = resolveCategoria(req.categoryId(), req.estabelecimento(), userId);

        TransactionStatus status = req.status() != null ? req.status() : TransactionStatus.CONFIRMADA;

        Transaction trx = Transaction.builder()
                .userId(userId)
                .accountId(account.getId())
                .contaDestinoId(req.tipo() == TransactionType.TRANSFERENCIA ? req.contaDestinoId() : null)
                .category(category)
                .tipo(req.tipo())
                .status(status)
                .valor(req.valor())
                .descricao(req.descricao())
                .estabelecimento(req.estabelecimento())
                .data(req.data())
                .hashDeduplicacao(hash)
                .build();

        // Ajusta saldo da conta se confirmada
        if (status == TransactionStatus.CONFIRMADA) {
            ajustarSaldo(account, req.tipo(), req.valor());
            accountRepository.save(account);
        }

        Transaction saved = transactionRepository.save(trx);
        log.info("Transação criada: id={} user={} tipo={} valor={}", saved.getId(), userId, req.tipo(), req.valor());
        return TransactionResponse.de(saved);
    }

    @Transactional
    public TransactionResponse atualizar(UUID id, UpdateTransactionRequest req, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);

        Transaction trx = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new TransactionNotFoundException("Transação não encontrada: " + id));

        // Reverte saldo anterior se estava confirmada
        if (trx.getStatus() == TransactionStatus.CONFIRMADA) {
            Account account = accountRepository.findByIdAndUserId(trx.getAccountId(), userId)
                    .orElseThrow(() -> new AccountNotFoundException("Conta não encontrada"));
            reverterSaldo(account, trx.getTipo(), trx.getValor());
            accountRepository.save(account);
        }

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
            Account account = accountRepository.findByIdAndUserId(trx.getAccountId(), userId)
                    .orElseThrow(() -> new AccountNotFoundException("Conta não encontrada"));
            ajustarSaldo(account, req.tipo(), req.valor());
            accountRepository.save(account);
        }

        return TransactionResponse.de(transactionRepository.save(trx));
    }

    @Transactional
    public void deletar(UUID id, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);

        Transaction trx = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new TransactionNotFoundException("Transação não encontrada: " + id));

        // Reverte saldo se confirmada
        if (trx.getStatus() == TransactionStatus.CONFIRMADA) {
            accountRepository.findByIdAndUserId(trx.getAccountId(), userId).ifPresent(acc -> {
                reverterSaldo(acc, trx.getTipo(), trx.getValor());
                accountRepository.save(acc);
            });
        }

        transactionRepository.delete(trx);
        log.info("Transação removida: id={} user={}", id, userId);
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private void ajustarSaldo(Account account, TransactionType tipo, BigDecimal valor) {
        if (tipo == TransactionType.RECEITA) {
            account.setBalance(account.getBalance().add(valor));
        } else {
            account.setBalance(account.getBalance().subtract(valor));
        }
    }

    private void reverterSaldo(Account account, TransactionType tipo, BigDecimal valor) {
        ajustarSaldo(account, tipo == TransactionType.RECEITA ? TransactionType.DESPESA : TransactionType.RECEITA, valor);
    }

    private Category resolveCategoria(UUID categoryId, String estabelecimento, UUID userId) {
        if (categoryId != null) {
            return categoryRepository.findByIdForUser(categoryId, userId).orElse(null);
        }
        return categoryService.categorizar(estabelecimento, userId).orElse(null);
    }

    private String gerarHash(UUID userId, UUID accountId, BigDecimal valor, LocalDate data, String descricao) {
        try {
            String raw = userId + "|" + accountId + "|" + valor + "|" + data + "|" + (descricao != null ? descricao : "");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            return UUID.randomUUID().toString(); // fallback sem deduplicação
        }
    }

    private UUID resolveUserId(AuthenticatedUser caller) {
        return userRepository.findIdByKeycloakId(caller.id())
                .orElseThrow(() -> ResourceNotFoundException.of("User", caller.id()));
    }
}
