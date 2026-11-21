package com.api.finance.user.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Contrato público do contexto User para consumidores internos.
 * Expõe identidade e operações de ciclo de vida sem vazar User/JPA ou UserRepository.
 */
public interface UserDirectoryPort {

    Optional<UUID> findInternalUserId(UUID identityProviderUserId);

    Optional<UUID> findIdentityProviderId(UUID internalUserId);

    boolean existsByIdentityProviderId(UUID identityProviderUserId);

    void registerIfAbsent(UUID identityProviderUserId, String email, String name);

    void deleteByIdentityProviderId(UUID identityProviderUserId);
}
