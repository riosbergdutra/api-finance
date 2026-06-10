package com.api.finance.goal.service;

import com.api.finance.config.AuthenticatedUser;
import com.api.finance.goal.dto.CreateGoalRequest;
import com.api.finance.goal.dto.DepositRequest;
import com.api.finance.goal.dto.GoalResponse;
import com.api.finance.goal.exception.GoalNotFoundException;
import com.api.finance.goal.model.Goal;
import com.api.finance.goal.repository.GoalRepository;
import com.api.finance.shared.exception.ResourceNotFoundException;
import com.api.finance.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoalService {

    private final GoalRepository goalRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<GoalResponse> listar(boolean apenasPendentes, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);
        List<Goal> goals = apenasPendentes
                ? goalRepository.findByUserIdAndConcluidaFalse(userId)
                : goalRepository.findByUserId(userId);
        return goals.stream().map(GoalResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public GoalResponse buscarPorId(UUID id, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);
        return goalRepository.findByIdAndUserId(id, userId)
                .map(GoalResponse::de)
                .orElseThrow(() -> new GoalNotFoundException("Meta não encontrada: " + id));
    }

    @Transactional
    public GoalResponse criar(CreateGoalRequest req, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);

        Goal goal = Goal.builder()
                .userId(userId)
                .nome(req.nome().strip())
                .valorAlvo(req.valorAlvo())
                .dataAlvo(req.dataAlvo())
                .cor(req.cor())
                .icone(req.icone())
                .build();

        return GoalResponse.de(goalRepository.save(goal));
    }

    @Transactional
    public GoalResponse atualizar(UUID id, CreateGoalRequest req, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);

        Goal goal = goalRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new GoalNotFoundException("Meta não encontrada: " + id));

        if (goal.isConcluida()) {
            throw new IllegalStateException("Meta já concluída não pode ser editada.");
        }

        goal.setNome(req.nome().strip());
        goal.setValorAlvo(req.valorAlvo());
        goal.setDataAlvo(req.dataAlvo());
        goal.setCor(req.cor());
        goal.setIcone(req.icone());

        return GoalResponse.de(goalRepository.save(goal));
    }

    /**
     * Adiciona valor à meta. Se atingir ou superar o alvo, conclui automaticamente.
     */
    @Transactional
    public GoalResponse depositar(UUID id, DepositRequest req, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);

        Goal goal = goalRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new GoalNotFoundException("Meta não encontrada: " + id));

        if (goal.isConcluida()) {
            throw new IllegalStateException("Meta já concluída.");
        }

        BigDecimal novoValor = goal.getValorAtual().add(req.valor());
        goal.setValorAtual(novoValor);

        if (novoValor.compareTo(goal.getValorAlvo()) >= 0) {
            goal.setConcluida(true);
            log.info("Meta concluída: id={} user={}", id, userId);
        }

        return GoalResponse.de(goalRepository.save(goal));
    }

    /**
     * Remove valor da meta (saque parcial).
     */
    @Transactional
    public GoalResponse sacar(UUID id, DepositRequest req, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);

        Goal goal = goalRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new GoalNotFoundException("Meta não encontrada: " + id));

        BigDecimal novoValor = goal.getValorAtual().subtract(req.valor());
        if (novoValor.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Saque excede o valor atual da meta.");
        }

        goal.setValorAtual(novoValor);
        goal.setConcluida(false);

        return GoalResponse.de(goalRepository.save(goal));
    }

    /**
     * Conclui a meta manualmente, independente do valor atingido.
     */
    @Transactional
    public GoalResponse concluir(UUID id, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);

        Goal goal = goalRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new GoalNotFoundException("Meta não encontrada: " + id));

        goal.setConcluida(true);
        return GoalResponse.de(goalRepository.save(goal));
    }

    @Transactional
    public void deletar(UUID id, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);
        Goal goal = goalRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new GoalNotFoundException("Meta não encontrada: " + id));
        goalRepository.delete(goal);
    }

    /**
     * Projeção de conclusão: estima quantos dias faltam com base no ritmo de depósito atual.
     * Retorna -1 se não há dados suficientes ou meta já concluída.
     */
    @Transactional(readOnly = true)
    public long calcularProjecaoDias(UUID id, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);

        Goal goal = goalRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new GoalNotFoundException("Meta não encontrada: " + id));

        if (goal.isConcluida() || goal.getValorAtual().compareTo(BigDecimal.ZERO) == 0) return -1;

        long diasDesde = ChronoUnit.DAYS.between(goal.getCriadoEm().toLocalDate(), LocalDate.now());
        if (diasDesde == 0) return -1;

        BigDecimal ritmo = goal.getValorAtual()
                .divide(BigDecimal.valueOf(diasDesde), 4, java.math.RoundingMode.HALF_UP);

        BigDecimal restante = goal.getValorAlvo().subtract(goal.getValorAtual());
        if (ritmo.compareTo(BigDecimal.ZERO) == 0) return -1;

        return restante.divide(ritmo, 0, java.math.RoundingMode.CEILING).longValue();
    }

    private UUID resolveUserId(AuthenticatedUser caller) {
        return userRepository.findIdByKeycloakId(caller.id())
                .orElseThrow(() -> ResourceNotFoundException.of("User", caller.id()));
    }
}
