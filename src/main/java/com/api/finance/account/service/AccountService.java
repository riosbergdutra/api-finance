package com.api.finance.account.service;

import com.api.finance.account.dto.AccountResponse;
import com.api.finance.account.dto.CreateAccountRequest;
import com.api.finance.account.dto.UpdateAccountRequest;
import com.api.finance.account.exception.AccountNotFoundException;
import com.api.finance.account.exception.DuplicateAccountNameException;
import com.api.finance.account.model.Account;
import com.api.finance.account.repository.AccountRepository;
import com.api.finance.config.AuthenticatedUser;
import com.api.finance.shared.exception.ResourceNotFoundException;
import com.api.finance.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Serviço do módulo Account.
 *
 * ═══════════════════════════════════════════════════════
 * MODELO DE SEGURANÇA — LEIA ANTES DE MODIFICAR
 * ═══════════════════════════════════════════════════════
 *
 * 1. ISOLAMENTO DE DADOS (IDOR Prevention):
 *    Todos os métodos recebem `caller` (AuthenticatedUser extraído do JWT).
 *    O userId é resolvido a partir do keycloakId SOMENTE neste ponto de entrada.
 *    Nenhuma query pode retornar dados sem filtrar por userId.
 *
 * 2. TRUST BOUNDARY:
 *    Nenhum dado de identificação vem do request body.
 *    O userId nunca é aceito como parâmetro HTTP.
 *
 * 3. KEYCLOAK ISOLATION:
 *    Este módulo NÃO conhece keycloakId. Só opera com userId interno.
 *    A resolução keycloakId → userId acontece uma única vez, aqui no serviço.
 *
 * 4. LIMITES DE NEGÓCIO:
 *    Máximo de 10 contas ativas por usuário (plano freemium default).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private static final int MAX_ACCOUNTS_PER_USER = 10;

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    // ──────────────────────────────────────────────────────────
    // READ
    // ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AccountResponse> listActiveAccounts(AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);
        return accountRepository.findByUserIdAndActiveTrue(userId)
                .stream()
                .map(AccountResponse::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getById(UUID accountId, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);
        // findByIdAndUserId garante que a conta pertence ao caller (IDOR prevention)
        Account account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Conta não encontrada: " + accountId));
        return AccountResponse.de(account);
    }

    // ──────────────────────────────────────────────────────────
    // WRITE
    // ──────────────────────────────────────────────────────────

    @Transactional
    public AccountResponse create(CreateAccountRequest request, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);

        // Limite de contas por usuário
        long total = accountRepository.countByUserIdAndActiveTrue(userId);
        if (total >= MAX_ACCOUNTS_PER_USER) {
            throw new IllegalStateException(
                    "Limite de " + MAX_ACCOUNTS_PER_USER + " contas ativas atingido.");
        }

        // Nome único por usuário (case-insensitive)
        if (accountRepository.existsByUserIdAndNameIgnoreCase(userId, request.name())) {
            throw new DuplicateAccountNameException(request.name());
        }

        BigDecimal initialBalance = request.initialBalance() != null
                ? request.initialBalance()
                : BigDecimal.ZERO;

        String currency = request.currency() != null
                ? request.currency().toUpperCase()
                : "BRL";

        Account account = Account.builder()
                .userId(userId)
                .name(request.name().strip())
                .type(request.type())
                .balance(initialBalance)
                .currency(currency)
                .color(request.color())
                .icon(request.icon())
                .build();

        Account saved = accountRepository.save(account);
        log.info("Conta criada: id={} user={}", saved.getId(), userId);
        return AccountResponse.de(saved);
    }

    @Transactional
    public AccountResponse update(UUID accountId, UpdateAccountRequest request, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);

        // Busca garantindo ownership (IDOR prevention)
        Account account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Conta não encontrada: " + accountId));

        // Nome único, excluindo a própria conta
        if (accountRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, request.name(), accountId)) {
            throw new DuplicateAccountNameException(request.name());
        }

        account.setName(request.name().strip());
        account.setType(request.type());
        account.setColor(request.color());
        account.setIcon(request.icon());

        Account updated = accountRepository.save(account);
        log.info("Conta atualizada: id={} user={}", accountId, userId);
        return AccountResponse.de(updated);
    }

    /**
     * Soft-delete: desativa a conta sem remover dados históricos.
     * Hard-delete deve ser operação administrativa, não exposta ao usuário.
     */
    @Transactional
    public void deactivate(UUID accountId, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);

        int affected = accountRepository.deactivateByIdAndUserId(accountId, userId);
        if (affected == 0) {
            throw new AccountNotFoundException("Conta não encontrada: " + accountId);
        }
        log.info("Conta desativada: id={} user={}", accountId, userId);
    }

    // ──────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ──────────────────────────────────────────────────────────

    /**
     * Resolve keycloakId → userId interno.
     *
     * Ponto centralizado de tradução IAM → domínio.
     * Executado UMA VEZ por request neste serviço.
     * Todos os outros módulos devem replicar este padrão.
     */
    private UUID resolveUserId(AuthenticatedUser caller) {
        return userRepository.findIdByKeycloakId(caller.id())
                .orElseThrow(() -> ResourceNotFoundException.of("User", caller.id()));
    }
}
