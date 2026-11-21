package com.api.finance.notification.application.port.out;

import com.api.finance.notification.model.NotificationType;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Contrato de aplicação para publicar uma notificação sem expor DTO HTTP ou entidade JPA. */
public record NotificationPushMessage(
        UUID id,
        NotificationType tipo,
        String titulo,
        String mensagem,
        boolean lida,
        String entidadeTipo,
        UUID entidadeId,
        OffsetDateTime criadoEm
) {}
