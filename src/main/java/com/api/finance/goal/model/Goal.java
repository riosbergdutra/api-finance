package com.api.finance.goal.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "goals",
    indexes = @Index(name = "idx_goal_user", columnList = "user_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valorAlvo;

    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal valorAtual = BigDecimal.ZERO;

    @Column(name = "data_alvo")
    private LocalDate dataAlvo;

    @Column(nullable = false)
    @Builder.Default
    private boolean concluida = false;

    @Column(length = 7)
    private String cor;

    @Column(length = 50)
    private String icone;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime criadoEm;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime atualizadoEm;

    /** Percentual concluído, máximo 100 */
    @Transient
    public double getPercentualConcluido() {
        if (valorAlvo == null || valorAlvo.compareTo(BigDecimal.ZERO) == 0) return 0;
        double pct = valorAtual.divide(valorAlvo, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue();
        return Math.min(pct, 100.0);
    }
}
