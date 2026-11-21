package com.api.finance.user.infrastructure;

import com.api.finance.user.application.port.UserDirectoryPort;
import com.api.finance.user.application.port.UserIdentity;
import com.api.finance.user.model.User;
import com.api.finance.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adaptador que mantém a persistência de identidade sob propriedade do módulo User. */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserDirectoryAdapter implements UserDirectoryPort {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<UserIdentity> findPage(int page, int size) {
        return userRepository.findAll(PageRequest.of(page, size)).getContent().stream()
                .map(user -> new UserIdentity(user.getId(), user.getKeycloakId()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findInternalUserId(UUID identityProviderUserId) {
        return userRepository.findIdByKeycloakId(identityProviderUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findIdentityProviderId(UUID internalUserId) {
        return userRepository.findById(internalUserId).map(User::getKeycloakId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByIdentityProviderId(UUID identityProviderUserId) {
        return userRepository.existsByKeycloakId(identityProviderUserId);
    }

    @Override
    @Transactional
    public void registerIfAbsent(UUID identityProviderUserId, String email, String name) {
        if (userRepository.existsByKeycloakId(identityProviderUserId)) {
            return;
        }

        User user = new User();
        user.setKeycloakId(identityProviderUserId);
        user.setEmail(email);
        user.setNome(name);
        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException concurrentRegistration) {
            // A constraint UNIQUE do banco é a garantia contra corrida entre instâncias.
            log.info("Registro de usuário concorrente detectado; a identidade já foi criada.");
        }
    }

    @Override
    @Transactional
    public void deleteByIdentityProviderId(UUID identityProviderUserId) {
        userRepository.deleteByKeycloakId(identityProviderUserId);
    }
}
