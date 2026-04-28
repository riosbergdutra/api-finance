package com.api.finance.account.controller;

import com.api.finance.account.dto.AccountResponse;
import com.api.finance.account.dto.CreateAccountRequest;
import com.api.finance.account.dto.UpdateAccountRequest;
import com.api.finance.account.service.AccountService;
import com.api.finance.config.AuthenticatedUser;
import com.api.finance.config.AuthenticatedUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller do módulo Account.
 * ═══════════════════════════════════════════════════════
 * MODELO DE SEGURANÇA — LEIA ANTES DE MODIFICAR
 * ═══════════════════════════════════════════════════════

 * 1. @AuthenticationPrincipal Jwt → sempre extraído do Bearer token pelo Spring Security.
 *    Nunca aceite userId via @RequestParam, @PathVariable ou @RequestBody.

 * 2. AuthenticatedUserProvider.get(jwt) → converte Jwt → AuthenticatedUser (record imutável).
 *    Esse objeto é passado para o Service, que resolve userId internamente.

 * 3. O Controller NÃO tem lógica de negócio. É um tradutor HTTP → Service.

 * 4. Rate limiting, auditoria e throttling devem ser configurados em SecurityConfig
 *    ou em um API Gateway externo (ex: NGINX, Kong).
 */
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Gerenciamento de contas financeiras")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

    private final AccountService accountService;
    private final AuthenticatedUserProvider userProvider;

    // ──────────────────────────────────────────────────────────
    // GET /accounts
    // ──────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Lista todas as contas ativas do usuário autenticado")
    public ResponseEntity<List<AccountResponse>> listAccounts(
            @AuthenticationPrincipal Jwt jwt) {

        AuthenticatedUser caller = userProvider.get(jwt);
        return ResponseEntity.ok(accountService.listActiveAccounts(caller));
    }

    // ──────────────────────────────────────────────────────────
    // GET /accounts/{id}
    // ──────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @Operation(summary = "Busca uma conta pelo ID (somente do usuário autenticado)")
    public ResponseEntity<AccountResponse> getAccount(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        AuthenticatedUser caller = userProvider.get(jwt);
        return ResponseEntity.ok(accountService.getById(id, caller));
    }

    // ──────────────────────────────────────────────────────────
    // POST /accounts
    // ──────────────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Cria uma nova conta financeira")
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        AuthenticatedUser caller = userProvider.get(jwt);
        AccountResponse created = accountService.create(request, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ──────────────────────────────────────────────────────────
    // PUT /accounts/{id}
    // ──────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza nome, tipo e aparência de uma conta")
    public ResponseEntity<AccountResponse> updateAccount(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAccountRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        AuthenticatedUser caller = userProvider.get(jwt);
        return ResponseEntity.ok(accountService.update(id, request, caller));
    }

    // ──────────────────────────────────────────────────────────
    // DELETE /accounts/{id}
    // ──────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @Operation(summary = "Desativa uma conta (soft-delete — dados históricos preservados)")
    public ResponseEntity<Void> deactivateAccount(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        AuthenticatedUser caller = userProvider.get(jwt);
        accountService.deactivate(id, caller);
        return ResponseEntity.noContent().build();
    }
}
