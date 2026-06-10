package com.api.finance.notification.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Notificação persistida para o usuário.
 *
 * entidadeTipo + entidadeId: link opcional para a entidade relacionada
 * (ex: BUDGET, GOAL, ACCOUNT) — usado pelo frontend para deep-link.
 */
@Entity
@Table(name = "notifications",
    indexes = {
        @Index(name = "idx_notif_user_lida", columnList = "user_id, lida, created_at")
    })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType tipo;

    @Column(nullable = false, length = 100)
    private String titulo;

    @Column(nullable = false, length = 500)
    private String mensagem;

    @Column(nullable = false)
    @Builder.Default
    private boolean lida = false;

    /** Tipo da entidade relacionada (ex: "BUDGET", "GOAL") — nullable */
    @Column(name = "entidade_tipo", length = 30)
    private String entidadeTipo;

    /** ID da entidade relacionada — nullable */
    @Column(name = "entidade_id")
    private UUID entidadeId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime criadoEm;
}
