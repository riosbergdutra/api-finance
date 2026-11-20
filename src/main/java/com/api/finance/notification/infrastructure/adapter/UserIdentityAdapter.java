package com.api.finance.notification.infrastructure.adapter;

import com.api.finance.notification.application.port.out.UserIdentityPort;
import com.api.finance.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador anticorrupção entre o contexto Notification e o contexto User.
 * A dependência do repositório de User fica confinada à infraestrutura.
 */
@Component
@RequiredArgsConstructor
public class UserIdentityAdapter implements UserIdentityPort {

    private final UserRepository userRepository;

    @Override
    public Optional<UUID> findInternalUserId(UUID identityProviderUserId) {
        return userRepository.findIdByKeycloakId(identityProviderUserId);
    }
}
