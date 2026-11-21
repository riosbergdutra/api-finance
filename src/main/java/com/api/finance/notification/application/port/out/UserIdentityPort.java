package com.api.finance.notification.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saída para resolver a identidade interna do usuário.
 * O contexto de notificações não conhece o repositório nem a entidade User.
 */
public interface UserIdentityPort {
    Optional<UUID> findInternalUserId(UUID identityProviderUserId);
}
