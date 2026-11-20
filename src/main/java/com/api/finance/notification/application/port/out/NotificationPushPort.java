package com.api.finance.notification.application.port.out;

import com.api.finance.notification.dto.NotificationResponse;

/** Porta de saída para entrega em tempo real; não expõe protocolo nem serialização. */
public interface NotificationPushPort {
    void publish(String identityProviderUserId, NotificationResponse notification);
}
