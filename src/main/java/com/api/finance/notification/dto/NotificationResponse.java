package com.api.finance.notification.dto;

import com.api.finance.notification.model.Notification;
import com.api.finance.notification.model.NotificationType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType tipo,
        String titulo,
        String mensagem,
        boolean lida,
        String entidadeTipo,
        UUID entidadeId,
        OffsetDateTime criadoEm
) {
    public static NotificationResponse de(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getTipo(), n.getTitulo(), n.getMensagem(),
                n.isLida(), n.getEntidadeTipo(), n.getEntidadeId(), n.getCriadoEm()
        );
    }
}
