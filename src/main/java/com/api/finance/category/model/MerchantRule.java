package com.api.finance.category.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Regra de categorização automática por padrão de nome do estabelecimento.
 * prioridade=0 = regra de sistema; prioridade=1 = regra do usuário (maior prioridade no match).
 */
@Entity
@Table(name = "merchant_rules",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "padrao_nome"}),
    indexes = @Index(name = "idx_mr_user", columnList = "user_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MerchantRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** null = regra de sistema */
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "padrao_nome", nullable = false, length = 100)
    private String padraoNome;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /** 0=sistema (menor prioridade), 1=usuário (maior prioridade) */
    @Column(nullable = false)
    @Builder.Default
    private int prioridade = 1;
}
