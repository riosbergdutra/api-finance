package com.api.finance.notification.application.port.out;

/** Porta de saída para entrega em tempo real; não expõe protocolo nem serialização. */
public interface NotificationPushPort {
    void publish(String identityProviderUserId, NotificationPushMessage notification);
}
