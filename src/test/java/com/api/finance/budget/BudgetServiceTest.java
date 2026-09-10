package com.api.finance.budget;

import com.api.finance.budget.dto.BudgetResponse;
import com.api.finance.budget.dto.CreateBudgetRequest;
import com.api.finance.budget.exception.BudgetNotFoundException;
import com.api.finance.budget.model.Budget;
import com.api.finance.budget.repository.BudgetRepository;
import com.api.finance.budget.service.BudgetService;
import com.api.finance.category.model.Category;
import com.api.finance.category.repository.CategoryRepository;
import com.api.finance.config.AuthenticatedUser;
import com.api.finance.transaction.repository.TransactionRepository;
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
@DisplayName("BudgetService")
class BudgetServiceTest {

    @Mock BudgetRepository budgetRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock UserRepository userRepository;
    @InjectMocks BudgetService budgetService;

    AuthenticatedUser caller;
    Budget budget;
    int mes = LocalDate.now().getMonthValue();
    int ano = LocalDate.now().getYear();

    @BeforeEach
    void setUp() {
        caller = caller();
        budget = budget();
        given(userRepository.findIdByKeycloakId(KEYCLOAK_ID)).willReturn(Optional.of(USER_ID));
    }

    // ─── listar ──────────────────────────────────────────────────────

    @Test
    @DisplayName("listar: retorna orçamentos do mês/ano")
    void listarRetornaOrcamentos() {
        given(budgetRepository.findByUserIdAndMesAndAno(USER_ID, mes, ano)).willReturn(List.of(budget));

        List<BudgetResponse> result = budgetService.listar(mes, ano, caller);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(BUDGET_ID);
    }

    // ─── criar ───────────────────────────────────────────────────────

    @Nested @DisplayName("criar")
    class Criar {

        @Test
        @DisplayName("cria orçamento geral (sem categoria) com sucesso")
        void criaOrcamentoGeralComSucesso() {
            CreateBudgetRequest req = new CreateBudgetRequest(
                    null, BigDecimal.valueOf(2000), mes, ano, 80);

            given(budgetRepository.existsByUserIdAndCategoryIdAndMesAndAno(USER_ID, null, mes, ano)).willReturn(false);
            given(budgetRepository.save(any())).willAnswer(inv -> {
                Budget b = inv.getArgument(0);
                b.setId(BUDGET_ID);
                return b;
            });

            BudgetResponse result = budgetService.criar(req, caller);

            assertThat(result.valorLimite()).isEqualByComparingTo(BigDecimal.valueOf(2000));
            assertThat(result.alertaEm()).isEqualTo(80);
        }

        @Test
        @DisplayName("cria orçamento por categoria com sucesso e calcula gasto atual")
        void criaOrcamentoPorCategoriaComGasto() {
            CreateBudgetRequest req = new CreateBudgetRequest(
                    CATEGORY_ID, BigDecimal.valueOf(500), mes, ano, null);
            Category cat = category();

            given(budgetRepository.existsByUserIdAndCategoryIdAndMesAndAno(USER_ID, CATEGORY_ID, mes, ano)).willReturn(false);
            given(categoryRepository.findByIdForUser(CATEGORY_ID, USER_ID)).willReturn(Optional.of(cat));
            given(transactionRepository.sumDespesaByCategoria(USER_ID, CATEGORY_ID, mes, ano))
                    .willReturn(BigDecimal.valueOf(150));
            given(budgetRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            BudgetResponse result = budgetService.criar(req, caller);

            assertThat(result.valorGasto()).isEqualByComparingTo(BigDecimal.valueOf(150));
        }

        @Test
        @DisplayName("lança IllegalStateException quando orçamento duplicado para categoria/mês/ano")
        void lancaExcecaoDuplicado() {
            CreateBudgetRequest req = new CreateBudgetRequest(
                    CATEGORY_ID, BigDecimal.valueOf(500), mes, ano, null);

            given(budgetRepository.existsByUserIdAndCategoryIdAndMesAndAno(USER_ID, CATEGORY_ID, mes, ano)).willReturn(true);

            assertThatThrownBy(() -> budgetService.criar(req, caller))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Já existe");
        }
    }

    // ─── atualizar ───────────────────────────────────────────────────

    @Test
    @DisplayName("atualizar: altera limite e alerta do orçamento")
    void atualizarOrcamento() {
        CreateBudgetRequest req = new CreateBudgetRequest(
                null, BigDecimal.valueOf(1000), mes, ano, 75);

        given(budgetRepository.findByIdAndUserId(BUDGET_ID, USER_ID)).willReturn(Optional.of(budget));
        given(budgetRepository.save(any())).willReturn(budget);

        BudgetResponse result = budgetService.atualizar(BUDGET_ID, req, caller);

        assertThat(result).isNotNull();
        assertThat(budget.getValorLimite()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(budget.getAlertaEm()).isEqualTo(75);
    }

    // ─── deletar ─────────────────────────────────────────────────────

    @Test
    @DisplayName("deletar: remove orçamento com sucesso")
    void deletarOrcamento() {
        given(budgetRepository.findByIdAndUserId(BUDGET_ID, USER_ID)).willReturn(Optional.of(budget));

        assertThatCode(() -> budgetService.deletar(BUDGET_ID, caller)).doesNotThrowAnyException();

        then(budgetRepository).should().delete(budget);
    }

    @Test
    @DisplayName("deletar: lança BudgetNotFoundException quando não encontrado")
    void deletarLancaNotFound() {
        given(budgetRepository.findByIdAndUserId(any(), eq(USER_ID))).willReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.deletar(UUID.randomUUID(), caller))
                .isInstanceOf(BudgetNotFoundException.class);
    }

    // ─── verificarAlertas ────────────────────────────────────────────

    @Nested @DisplayName("verificarAlertas")
    class VerificarAlertas {

        @Test
        @DisplayName("retorna orçamentos que ultrapassaram o percentual de alerta")
        void retornaOrcamentosAcimaDalertas() {
            // 100 gasto / 500 limite = 20% → alertaEm=80 → NÃO disparado
            Budget abaixo = budget();

            // 450 gasto / 500 limite = 90% → alertaEm=80 → disparado
            Budget acima = Budget.builder()
                    .id(UUID.randomUUID())
                    .userId(USER_ID)
                    .valorLimite(BigDecimal.valueOf(500))
                    .valorGasto(BigDecimal.valueOf(450))
                    .mes(mes).ano(ano)
                    .alertaEm(80)
                    .build();

            given(budgetRepository.findComAlertaAtivo(eq(USER_ID), anyInt(), anyInt()))
                    .willReturn(List.of(abaixo, acima));

            List<Budget> alertas = budgetService.verificarAlertas(USER_ID);

            assertThat(alertas).hasSize(1);
            assertThat(alertas.get(0).getId()).isEqualTo(acima.getId());
        }

        @Test
        @DisplayName("retorna lista vazia quando nenhum orçamento disparou alerta")
        void retornaVazioSemAlertas() {
            given(budgetRepository.findComAlertaAtivo(any(), anyInt(), anyInt())).willReturn(List.of());

            List<Budget> alertas = budgetService.verificarAlertas(USER_ID);

            assertThat(alertas).isEmpty();
        }
    }

    // ─── getPercentualGasto (Budget model) ───────────────────────────

    @Test
    @DisplayName("Budget.getPercentualGasto: retorna percentual correto")
    void percentualGastoCorreto() {
        Budget b = Budget.builder()
                .valorLimite(BigDecimal.valueOf(1000))
                .valorGasto(BigDecimal.valueOf(750))
                .build();

        assertThat(b.getPercentualGasto()).isEqualTo(75.0);
    }

    @Test
    @DisplayName("Budget.getPercentualGasto: retorna 0 quando limite é zero")
    void percentualGastoComLimiteZero() {
        Budget b = Budget.builder()
                .valorLimite(BigDecimal.ZERO)
                .valorGasto(BigDecimal.valueOf(100))
                .build();

        assertThat(b.getPercentualGasto()).isEqualTo(0.0);
    }
}
