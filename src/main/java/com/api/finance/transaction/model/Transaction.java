package com.api.finance.transaction.model;

import com.api.finance.category.model.Category;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entidade de domínio Transaction.
 *
 * SEGURANÇA: Isolada por userId (FK users.id).
 * hashDeduplicacao evita importação duplicada de extratos.
 * Conta de destino (contaDestinoId) usada apenas em TRANSFERENCIA.
 */
@Entity
@Table(name = "transactions",
    indexes = {
        @Index(name = "idx_trx_user_id", columnList = "user_id"),
        @Index(name = "idx_trx_conta", columnList = "account_id"),
        @Index(name = "idx_trx_data", columnList = "data"),
        @Index(name = "idx_trx_hash", columnList = "hash_deduplicacao", unique = true)
    })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    /** Conta destino — preenchida somente em TRANSFERENCIA */
    @Column(name = "conta_destino_id")
    private UUID contaDestinoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.CONFIRMADA;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;

    @Column(length = 200)
    private String descricao;

    /** Nome do estabelecimento — usado para categorização automática */
    @Column(name = "estabelecimento", length = 100)
    private String estabelecimento;

    @Column(nullable = false)
    private LocalDate data;

    /**
     * Hash SHA-256 de (userId + accountId + valor + data + descricao).
     * Previne duplicação na importação de extratos.
     */
    @Column(name = "hash_deduplicacao", unique = true, length = 64)
    private String hashDeduplicacao;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime criadoEm;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime atualizadoEm;
}
