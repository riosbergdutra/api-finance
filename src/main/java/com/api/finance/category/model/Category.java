package com.api.finance.category.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Categorias de transação.
 * userId null = categoria de sistema (compartilhada, não editável pelo usuário).
 */
@Entity
@Table(name = "categories",
    indexes = {
        @Index(name = "idx_cat_user_id", columnList = "user_id"),
        @Index(name = "idx_cat_sistema", columnList = "sistema")
    })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** null = categoria de sistema */
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 80)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoryType tipo;

    @Column(length = 50)
    private String icone;

    @Column(length = 7)
    private String cor;

    /** Auto-referência para subcategorias */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category pai;

    /** true = categoria de sistema, não editável pelo usuário */
    @Column(nullable = false)
    @Builder.Default
    private boolean sistema = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean ativa = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime criadoEm;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime atualizadoEm;
}
