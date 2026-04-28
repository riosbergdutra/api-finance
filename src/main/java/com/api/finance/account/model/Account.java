package com.api.finance.account.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entidade de domínio Account.
 *
 * SEGURANÇA: Isolamento por user_id (FK para users.id).
 * - NÃO armazena keycloak_id. Nunca acessa IAM diretamente.
 * - Todas as queries DEVEM filtrar por userId extraído do JWT,
 *   jamais do request body (previne IDOR).
 */
@Entity
@Table(
    name = "accounts",
    indexes = {
        @Index(name = "idx_accounts_user_id", columnList = "user_id"),
        @Index(name = "idx_accounts_user_active", columnList = "user_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * FK para users.id — chave de isolamento de todos os dados do módulo.
     * Imutável após criação (updatable = false).
     */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccountType type;

    /**
     * Saldo armazenado em escala monetária (19,2).
     * Operações de débito/crédito devem ser atômicas via @Transactional.
     */
    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    /** Código ISO 4217 (ex: BRL, USD). Sempre maiúsculo. */
    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "BRL";

    /** Cor hex para UI (ex: #1A2B3C). Validado no banco e na camada de entrada. */
    @Column(length = 7)
    private String color;

    /** Identificador de ícone para o frontend. */
    @Column(length = 50)
    private String icon;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
