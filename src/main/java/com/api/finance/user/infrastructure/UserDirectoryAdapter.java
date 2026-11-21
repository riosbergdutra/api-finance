package com.api.finance.user.infrastructure;

import com.api.finance.user.application.port.UserDirectoryPort;
import com.api.finance.user.application.port.UserIdentity;
import com.api.finance.user.model.User;
import com.api.finance.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import com.api.finance.user.exception.UserIdentityDeletedException;

/** Adaptador que mantém a persistência de identidade sob propriedade do módulo User. */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserDirectoryAdapter implements UserDirectoryPort {

    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

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
        if (isIdentityDeleted(identityProviderUserId)) {
            throw new UserIdentityDeletedException();
        }
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
        String identityHash = hashIdentity(identityProviderUserId);
        jdbcTemplate.update("""
                INSERT INTO deleted_user_identities (identity_hash)
                VALUES (?)
                ON CONFLICT (identity_hash) DO NOTHING
                """, identityHash);
        jdbcTemplate.update("""
                INSERT INTO account_deletion_outbox (keycloak_id)
                VALUES (?)
                ON CONFLICT (keycloak_id) DO NOTHING
                """, identityProviderUserId);

        userRepository.findByKeycloakId(identityProviderUserId).ifPresent(user -> {
            UUID userId = user.getId();

            // user_id é mantido como UUID escalar em várias tabelas legadas,
            // sem FK/cascade. A exclusão precisa remover explicitamente os dados.
            jdbcTemplate.update("DELETE FROM transactions WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM budgets WHERE user_id = ?", userId);
            jdbcTemplate.update("""
                    DELETE FROM merchant_rules
                    WHERE user_id = ?
                       OR category_id IN (SELECT id FROM categories WHERE user_id = ?)
                    """, userId, userId);
            jdbcTemplate.update("""
                    UPDATE categories SET parent_id = NULL
                    WHERE parent_id IN (SELECT id FROM categories WHERE user_id = ?)
                    """, userId);
            jdbcTemplate.update("DELETE FROM categories WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM goals WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM notifications WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM subscription WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM accounts WHERE user_id = ?", userId);

            userRepository.delete(user);
        });
    }
 
    private boolean isIdentityDeleted(UUID identityProviderUserId) {
        Boolean deleted = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM deleted_user_identities WHERE identity_hash = ?)",
                Boolean.class,
                hashIdentity(identityProviderUserId));
        return Boolean.TRUE.equals(deleted);
    }

    private String hashIdentity(UUID identityProviderUserId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(identityProviderUserId.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 não está disponível.", impossible);
        }
    }
}
