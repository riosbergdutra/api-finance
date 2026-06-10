package com.api.finance.goal;

import com.api.finance.config.AuthenticatedUser;
import com.api.finance.goal.dto.CreateGoalRequest;
import com.api.finance.goal.dto.DepositRequest;
import com.api.finance.goal.dto.GoalResponse;
import com.api.finance.goal.exception.GoalNotFoundException;
import com.api.finance.goal.model.Goal;
import com.api.finance.goal.repository.GoalRepository;
import com.api.finance.goal.service.GoalService;
import com.api.finance.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.api.finance.shared.TestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GoalService")
class GoalServiceTest {

    @Mock GoalRepository goalRepository;
    @Mock UserRepository userRepository;
    @InjectMocks GoalService goalService;

    AuthenticatedUser caller;
    Goal goal;

    @BeforeEach
    void setUp() {
        caller = caller();
        goal = goal();
        given(userRepository.findIdByKeycloakId(KEYCLOAK_ID)).willReturn(Optional.of(USER_ID));
    }

    // ─── listar ──────────────────────────────────────────────────────

    @Test
    @DisplayName("listar: retorna todas as metas quando apenasPendentes=false")
    void listarTodasMetas() {
        goal.setConcluida(true);
        given(goalRepository.findByUserId(USER_ID)).willReturn(List.of(goal));

        List<GoalResponse> result = goalService.listar(false, caller);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("listar: retorna apenas pendentes quando apenasPendentes=true")
    void listarApenasPendentes() {
        given(goalRepository.findByUserIdAndConcluidaFalse(USER_ID)).willReturn(List.of(goal));

        List<GoalResponse> result = goalService.listar(true, caller);

        assertThat(result).hasSize(1);
        then(goalRepository).should().findByUserIdAndConcluidaFalse(USER_ID);
    }

    // ─── criar ───────────────────────────────────────────────────────

    @Test
    @DisplayName("criar: salva nova meta com dados corretos")
    void criarMetaComSucesso() {
        CreateGoalRequest req = new CreateGoalRequest(
                "Notebook", BigDecimal.valueOf(3000), LocalDate.now().plusMonths(6), "#FF0000", "laptop");

        given(goalRepository.save(any())).willAnswer(inv -> {
            Goal g = inv.getArgument(0);
            g.setId(GOAL_ID);
            return g;
        });

        GoalResponse result = goalService.criar(req, caller);

        assertThat(result.nome()).isEqualTo("Notebook");
        assertThat(result.valorAlvo()).isEqualByComparingTo(BigDecimal.valueOf(3000));
        assertThat(result.concluida()).isFalse();
    }

    // ─── atualizar ───────────────────────────────────────────────────

    @Test
    @DisplayName("atualizar: lança IllegalStateException quando meta já concluída")
    void atualizarMetaConcluida() {
        goal.setConcluida(true);
        given(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).willReturn(Optional.of(goal));

        CreateGoalRequest req = new CreateGoalRequest("X", BigDecimal.ONE, null, null, null);

        assertThatThrownBy(() -> goalService.atualizar(GOAL_ID, req, caller))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("concluída");
    }

    // ─── depositar ───────────────────────────────────────────────────

    @Nested @DisplayName("depositar")
    class Depositar {

        @Test
        @DisplayName("adiciona valor e não conclui quando ainda abaixo do alvo")
        void adicionaValorSemConcluir() {
            // valorAtual=1000, alvo=5000, deposito=500 → 1500 (ainda pendente)
            DepositRequest req = new DepositRequest(BigDecimal.valueOf(500));
            given(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).willReturn(Optional.of(goal));
            given(goalRepository.save(any())).willReturn(goal);

            GoalResponse result = goalService.depositar(GOAL_ID, req, caller);

            assertThat(goal.getValorAtual()).isEqualByComparingTo(BigDecimal.valueOf(1500));
            assertThat(goal.isConcluida()).isFalse();
        }

        @Test
        @DisplayName("conclui automaticamente quando atinge o alvo")
        void concluiAutomaticamenteAoAtingirAlvo() {
            // valorAtual=1000, alvo=5000, deposito=4000 → 5000 = concluída
            DepositRequest req = new DepositRequest(BigDecimal.valueOf(4000));
            given(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).willReturn(Optional.of(goal));
            given(goalRepository.save(any())).willReturn(goal);

            goalService.depositar(GOAL_ID, req, caller);

            assertThat(goal.isConcluida()).isTrue();
        }

        @Test
        @DisplayName("conclui automaticamente quando ultrapassa o alvo")
        void concluiQuandoUltrapassaAlvo() {
            DepositRequest req = new DepositRequest(BigDecimal.valueOf(99999));
            given(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).willReturn(Optional.of(goal));
            given(goalRepository.save(any())).willReturn(goal);

            goalService.depositar(GOAL_ID, req, caller);

            assertThat(goal.isConcluida()).isTrue();
        }

        @Test
        @DisplayName("lança IllegalStateException quando meta já concluída")
        void lancaExcecaoMetaConcluida() {
            goal.setConcluida(true);
            given(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).willReturn(Optional.of(goal));

            assertThatThrownBy(() -> goalService.depositar(GOAL_ID, new DepositRequest(BigDecimal.ONE), caller))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ─── sacar ───────────────────────────────────────────────────────

    @Nested @DisplayName("sacar")
    class Sacar {

        @Test
        @DisplayName("subtrai valor com sucesso")
        void sacaComSucesso() {
            // valorAtual=1000, saque=300 → 700
            DepositRequest req = new DepositRequest(BigDecimal.valueOf(300));
            given(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).willReturn(Optional.of(goal));
            given(goalRepository.save(any())).willReturn(goal);

            goalService.sacar(GOAL_ID, req, caller);

            assertThat(goal.getValorAtual()).isEqualByComparingTo(BigDecimal.valueOf(700));
            assertThat(goal.isConcluida()).isFalse();
        }

        @Test
        @DisplayName("lança IllegalArgumentException quando saque excede valor atual")
        void lancaExcecaoSaqueExcede() {
            // valorAtual=1000, saque=2000
            DepositRequest req = new DepositRequest(BigDecimal.valueOf(2000));
            given(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).willReturn(Optional.of(goal));

            assertThatThrownBy(() -> goalService.sacar(GOAL_ID, req, caller))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Saque excede");
        }
    }

    // ─── concluir ────────────────────────────────────────────────────

    @Test
    @DisplayName("concluir: marca meta como concluída manualmente")
    void concluirManualmente() {
        given(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).willReturn(Optional.of(goal));
        given(goalRepository.save(any())).willReturn(goal);

        goalService.concluir(GOAL_ID, caller);

        assertThat(goal.isConcluida()).isTrue();
    }

    // ─── deletar ─────────────────────────────────────────────────────

    @Test
    @DisplayName("deletar: remove meta com sucesso")
    void deletarComSucesso() {
        given(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).willReturn(Optional.of(goal));

        assertThatCode(() -> goalService.deletar(GOAL_ID, caller)).doesNotThrowAnyException();

        then(goalRepository).should().delete(goal);
    }

    @Test
    @DisplayName("deletar: lança GoalNotFoundException quando não encontrada")
    void deletarLancaNotFound() {
        given(goalRepository.findByIdAndUserId(any(), eq(USER_ID))).willReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.deletar(UUID.randomUUID(), caller))
                .isInstanceOf(GoalNotFoundException.class);
    }

    // ─── calcularProjecaoDias ────────────────────────────────────────

    @Nested @DisplayName("calcularProjecaoDias")
    class ProjecaoDias {

        @Test
        @DisplayName("retorna estimativa positiva quando há ritmo de depósito")
        void retornaEstimativaPositiva() {
            // valorAtual=1000 em 10 dias → ritmo=100/dia. Restante=4000 → ~40 dias
            given(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).willReturn(Optional.of(goal));

            long dias = goalService.calcularProjecaoDias(GOAL_ID, caller);

            assertThat(dias).isGreaterThan(0);
        }

        @Test
        @DisplayName("retorna -1 quando meta já concluída")
        void retornaNegativoMetaConcluida() {
            goal.setConcluida(true);
            given(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).willReturn(Optional.of(goal));

            long dias = goalService.calcularProjecaoDias(GOAL_ID, caller);

            assertThat(dias).isEqualTo(-1);
        }

        @Test
        @DisplayName("retorna -1 quando valorAtual é zero")
        void retornaNegativoSemDepositos() {
            goal.setValorAtual(BigDecimal.ZERO);
            given(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).willReturn(Optional.of(goal));

            long dias = goalService.calcularProjecaoDias(GOAL_ID, caller);

            assertThat(dias).isEqualTo(-1);
        }
    }

    // ─── Goal model ──────────────────────────────────────────────────

    @Nested @DisplayName("Goal.getPercentualConcluido")
    class PercentualConcluido {

        @Test
        @DisplayName("retorna percentual correto")
        void percentualCorreto() {
            Goal g = goal();
            // valorAtual=1000, alvo=5000 → 20%
            assertThat(g.getPercentualConcluido()).isEqualTo(20.0);
        }

        @Test
        @DisplayName("limita a 100% quando ultrapassa o alvo")
        void limitaA100() {
            Goal g = goal();
            g.setValorAtual(BigDecimal.valueOf(999999));
            assertThat(g.getPercentualConcluido()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("retorna 0 quando valorAlvo é zero")
        void retornaZeroComAlvoZero() {
            Goal g = goal();
            g.setValorAlvo(BigDecimal.ZERO);
            assertThat(g.getPercentualConcluido()).isEqualTo(0.0);
        }
    }
}
