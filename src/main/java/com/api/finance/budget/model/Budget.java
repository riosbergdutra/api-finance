package com.api.finance.budget.model;

import com.api.finance.category.model.Category;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Orçamento mensal por categoria.
 * categoria null = orçamento geral (todos os gastos do mês).
 * UNIQUE(user_id, category_id, mes, ano) garante um orçamento por categoria/mês.
 */
@Entity
@Table(name = "budgets",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_budget_user_cat_mes_ano",
        columnNames = {"user_id", "category_id", "mes", "ano"}),
    indexes = @Index(name = "idx_budget_user", columnList = "user_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** null = orçamento geral */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valorLimite;

    /** Calculado a partir das transações — atualizado pelo BudgetService */
    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal valorGasto = BigDecimal.ZERO;

    @Column(nullable = false)
    private int mes;

    @Column(nullable = false)
    private int ano;

    /** % de gasto para disparar alerta. null = sem alerta */
    @Column(name = "alerta_em")
    private Integer alertaEm;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime criadoEm;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime atualizadoEm;

    /** Percentual gasto em relação ao limite (0–100+) */
    @Transient
    public double getPercentualGasto() {
        if (valorLimite == null || valorLimite.compareTo(BigDecimal.ZERO) == 0) return 0;
        return valorGasto.divide(valorLimite, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue();
    }
}
