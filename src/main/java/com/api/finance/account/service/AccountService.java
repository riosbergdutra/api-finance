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
import com.api.finance.subscription.service.SubscriptionService;
import com.api.finance.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository       accountRepository;
    private final UserRepository          userRepository;
    private final SubscriptionService     subscriptionService;

    // ── READ ──────────────────────────────────────────────────────────────

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
        Account account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Conta não encontrada: " + accountId));
        return AccountResponse.de(account);
    }

    // ── WRITE ─────────────────────────────────────────────────────────────

    @Transactional
    public AccountResponse create(CreateAccountRequest request, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);

        // Verifica limite do plano (FREE = 3 contas, PRO = ilimitado)
        long total = accountRepository.countByUserIdAndActiveTrue(userId);
        subscriptionService.assertPodeCriarConta(userId, total);

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

        Account account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Conta não encontrada: " + accountId));

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

    @Transactional
    public void deactivate(UUID accountId, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);
        int affected = accountRepository.deactivateByIdAndUserId(accountId, userId);
        if (affected == 0) {
            throw new AccountNotFoundException("Conta não encontrada: " + accountId);
        }
        log.info("Conta desativada: id={} user={}", accountId, userId);
    }

    // ── HELPERS ───────────────────────────────────────────────────────────

    private UUID resolveUserId(AuthenticatedUser caller) {
        return userRepository.findIdByKeycloakId(caller.id())
                .orElseThrow(() -> ResourceNotFoundException.of("User", caller.id()));
    }
}