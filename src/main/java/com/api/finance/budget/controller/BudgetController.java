package com.api.finance.budget.controller;

import com.api.finance.budget.dto.BudgetResponse;
import com.api.finance.budget.dto.CreateBudgetRequest;
import com.api.finance.budget.service.BudgetService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/budgets")
@RequiredArgsConstructor
@Tag(name = "Budgets", description = "Orçamentos mensais por categoria")
@SecurityRequirement(name = "bearerAuth")
public class BudgetController {

    private final BudgetService budgetService;
    private final AuthenticatedUserProvider userProvider;

    @GetMapping
    @Operation(summary = "Lista orçamentos do mês/ano informado")
    public ResponseEntity<List<BudgetResponse>> listar(
            @RequestParam(defaultValue = "0") int mes,
            @RequestParam(defaultValue = "0") int ano,
            @AuthenticationPrincipal Jwt jwt) {

        LocalDate hoje = LocalDate.now();
        AuthenticatedUser caller = userProvider.get(jwt);
        return ResponseEntity.ok(budgetService.listar(
                mes > 0 ? mes : hoje.getMonthValue(),
                ano > 0 ? ano : hoje.getYear(),
                caller));
    }

    @PostMapping
    @Operation(summary = "Cria um orçamento mensal")
    public ResponseEntity<BudgetResponse> criar(
            @Valid @RequestBody CreateBudgetRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        AuthenticatedUser caller = userProvider.get(jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(budgetService.criar(request, caller));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza limite e alerta de um orçamento")
    public ResponseEntity<BudgetResponse> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody CreateBudgetRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        AuthenticatedUser caller = userProvider.get(jwt);
        return ResponseEntity.ok(budgetService.atualizar(id, request, caller));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove um orçamento")
    public ResponseEntity<Void> deletar(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        AuthenticatedUser caller = userProvider.get(jwt);
        budgetService.deletar(id, caller);
        return ResponseEntity.noContent().build();
    }
}
