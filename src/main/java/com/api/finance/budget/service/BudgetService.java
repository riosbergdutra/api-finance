package com.api.finance.budget.service;

import com.api.finance.budget.dto.BudgetResponse;
import com.api.finance.budget.dto.CreateBudgetRequest;
import com.api.finance.budget.exception.BudgetNotFoundException;
import com.api.finance.budget.model.Budget;
import com.api.finance.budget.repository.BudgetRepository;
import com.api.finance.category.model.Category;
import com.api.finance.category.repository.CategoryRepository;
import com.api.finance.config.AuthenticatedUser;
import com.api.finance.shared.exception.ResourceNotFoundException;
import com.api.finance.transaction.repository.TransactionRepository;
import com.api.finance.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<BudgetResponse> listar(int mes, int ano, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);
        return budgetRepository.findByUserIdAndMesAndAno(userId, mes, ano)
                .stream().map(BudgetResponse::de).toList();
    }

    @Transactional
    public BudgetResponse criar(CreateBudgetRequest req, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);

        if (budgetRepository.existsByUserIdAndCategoryIdAndMesAndAno(
                userId, req.categoryId(), req.mes(), req.ano())) {
            throw new IllegalStateException("Já existe um orçamento para esta categoria neste mês/ano.");
        }

        Category category = null;
        if (req.categoryId() != null) {
            category = categoryRepository.findByIdForUser(req.categoryId(), userId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Category", req.categoryId()));
        }

        BigDecimal gastoAtual = calcularGasto(userId, req.categoryId(), req.mes(), req.ano());

        Budget budget = Budget.builder()
                .userId(userId)
                .category(category)
                .valorLimite(req.valorLimite())
                .valorGasto(gastoAtual)
                .mes(req.mes())
                .ano(req.ano())
                .alertaEm(req.alertaEm())
                .build();

        return BudgetResponse.de(budgetRepository.save(budget));
    }

    @Transactional
    public BudgetResponse atualizar(UUID id, CreateBudgetRequest req, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);

        Budget budget = budgetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BudgetNotFoundException("Orçamento não encontrado: " + id));

        budget.setValorLimite(req.valorLimite());
        budget.setAlertaEm(req.alertaEm());

        return BudgetResponse.de(budgetRepository.save(budget));
    }

    @Transactional
    public void deletar(UUID id, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);
        Budget budget = budgetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BudgetNotFoundException("Orçamento não encontrado: " + id));
        budgetRepository.delete(budget);
    }

    /**
     * Recalcula valorGasto para todos os orçamentos do mês/ano.
     * Deve ser chamado após criar/editar/deletar uma transação.
     */
    @Transactional
    public void recalcularGastos(UUID userId, int mes, int ano) {
        budgetRepository.findByUserIdAndMesAndAno(userId, mes, ano).forEach(b -> {
            UUID catId = b.getCategory() != null ? b.getCategory().getId() : null;
            b.setValorGasto(calcularGasto(userId, catId, mes, ano));
        });
    }

    /**
     * Verifica alertas de orçamento e retorna os que ultrapassaram o percentual definido.
     * Resultado usado pelo NotificationService para disparar alertas.
     */
    @Transactional(readOnly = true)
    public List<Budget> verificarAlertas(UUID userId) {
        LocalDate hoje = LocalDate.now();
        return budgetRepository.findComAlertaAtivo(userId, hoje.getMonthValue(), hoje.getYear())
                .stream()
                .filter(b -> b.getAlertaEm() != null && b.getPercentualGasto() >= b.getAlertaEm())
                .toList();
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private BigDecimal calcularGasto(UUID userId, UUID categoryId, int mes, int ano) {
        if (categoryId == null) return BigDecimal.ZERO; // TODO: soma geral sem categoria implementar se necessário
        return transactionRepository.sumDespesaByCategoria(userId, categoryId, mes, ano);
    }

    private UUID resolveUserId(AuthenticatedUser caller) {
        return userRepository.findIdByKeycloakId(caller.id())
                .orElseThrow(() -> ResourceNotFoundException.of("User", caller.id()));
    }
}
