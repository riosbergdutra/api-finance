package com.api.finance.transaction.controller;

import com.api.finance.config.AuthenticatedUser;
import com.api.finance.config.AuthenticatedUserProvider;
import com.api.finance.transaction.dto.CreateTransactionRequest;
import com.api.finance.transaction.dto.TransactionResponse;
import com.api.finance.transaction.dto.UpdateTransactionRequest;
import com.api.finance.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Gerenciamento de transações financeiras")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionService transactionService;
    private final AuthenticatedUserProvider userProvider;

    @GetMapping
    @Operation(summary = "Lista transações paginadas por período")
    public ResponseEntity<Page<TransactionResponse>> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @PageableDefault(size = 20, sort = "data") Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {

        AuthenticatedUser caller = userProvider.get(jwt);
        return ResponseEntity.ok(transactionService.listar(de, ate, pageable, caller));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca transação por ID")
    public ResponseEntity<TransactionResponse> buscar(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        AuthenticatedUser caller = userProvider.get(jwt);
        return ResponseEntity.ok(transactionService.buscarPorId(id, caller));
    }

    @PostMapping
    @Operation(summary = "Cria uma nova transação")
    public ResponseEntity<TransactionResponse> criar(
            @Valid @RequestBody CreateTransactionRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        AuthenticatedUser caller = userProvider.get(jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionService.criar(request, caller));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza uma transação")
    public ResponseEntity<TransactionResponse> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTransactionRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        AuthenticatedUser caller = userProvider.get(jwt);
        return ResponseEntity.ok(transactionService.atualizar(id, request, caller));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove uma transação e reverte o saldo da conta")
    public ResponseEntity<Void> deletar(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        AuthenticatedUser caller = userProvider.get(jwt);
        transactionService.deletar(id, caller);
        return ResponseEntity.noContent().build();
    }
}
