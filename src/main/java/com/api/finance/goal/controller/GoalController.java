package com.api.finance.goal.controller;

import com.api.finance.config.AuthenticatedUser;
import com.api.finance.config.AuthenticatedUserProvider;
import com.api.finance.goal.dto.CreateGoalRequest;
import com.api.finance.goal.dto.DepositRequest;
import com.api.finance.goal.dto.GoalResponse;
import com.api.finance.goal.service.GoalService;
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
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/goals")
@RequiredArgsConstructor
@Tag(name = "Goals", description = "Metas financeiras")
@SecurityRequirement(name = "bearerAuth")
public class GoalController {

    private final GoalService goalService;
    private final AuthenticatedUserProvider userProvider;

    @GetMapping
    @Operation(summary = "Lista metas (todas ou apenas pendentes)")
    public ResponseEntity<List<GoalResponse>> listar(
            @RequestParam(defaultValue = "false") boolean pendentes,
            @AuthenticationPrincipal Jwt jwt) {

        AuthenticatedUser caller = userProvider.get(jwt);
        return ResponseEntity.ok(goalService.listar(pendentes, caller));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GoalResponse> buscar(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(goalService.buscarPorId(id, userProvider.get(jwt)));
    }

    @PostMapping
    @Operation(summary = "Cria uma nova meta financeira")
    public ResponseEntity<GoalResponse> criar(
            @Valid @RequestBody CreateGoalRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        AuthenticatedUser caller = userProvider.get(jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(goalService.criar(request, caller));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GoalResponse> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody CreateGoalRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(goalService.atualizar(id, request, userProvider.get(jwt)));
    }

    @PostMapping("/{id}/depositar")
    @Operation(summary = "Adiciona valor à meta")
    public ResponseEntity<GoalResponse> depositar(
            @PathVariable UUID id,
            @Valid @RequestBody DepositRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(goalService.depositar(id, request, userProvider.get(jwt)));
    }

    @PostMapping("/{id}/sacar")
    @Operation(summary = "Remove valor da meta")
    public ResponseEntity<GoalResponse> sacar(
            @PathVariable UUID id,
            @Valid @RequestBody DepositRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(goalService.sacar(id, request, userProvider.get(jwt)));
    }

    @PostMapping("/{id}/concluir")
    @Operation(summary = "Conclui a meta manualmente")
    public ResponseEntity<GoalResponse> concluir(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(goalService.concluir(id, userProvider.get(jwt)));
    }

    @GetMapping("/{id}/projecao")
    @Operation(summary = "Retorna projeção de dias para conclusão com base no ritmo atual")
    public ResponseEntity<Map<String, Long>> projecao(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        long dias = goalService.calcularProjecaoDias(id, userProvider.get(jwt));
        return ResponseEntity.ok(Map.of("diasEstimados", dias));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        goalService.deletar(id, userProvider.get(jwt));
        return ResponseEntity.noContent().build();
    }
}
