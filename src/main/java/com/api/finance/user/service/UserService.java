package com.api.finance.user.service;

import com.api.finance.config.AuthenticatedUser;
import com.api.finance.config.AuthenticatedUserProvider;
import com.api.finance.shared.exception.ResourceNotFoundException;
import com.api.finance.user.dto.UserResponseDTO;
import com.api.finance.user.model.User;
import com.api.finance.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final AuthenticatedUserProvider userProvider; // Injetado conforme o AuthService

    @Transactional(readOnly = true)
    public UserResponseDTO getMe(Jwt jwt) {
        // 1. Usa o provider para extrair a identidade de forma segura e padronizada
        AuthenticatedUser authenticatedUser = userProvider.get(jwt);
        UUID keycloakId = authenticatedUser.id();

        log.info("Iniciando busca de perfil para o usuário autenticado: {}", keycloakId);

        // 2. Busca no banco usando a âncora do Keycloak
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> {
                    log.error("Tentativa de acesso a perfil inexistente. Keycloak ID: {}", keycloakId);
                    return ResourceNotFoundException.of("User", keycloakId);
                });

        log.debug("Perfil localizado com sucesso para o e-mail: {}", user.getEmail());

        // 3. Retorno do DTO (Sem expor a Entity)
        return new UserResponseDTO(
                user.getId(),
                user.getNome(),
                user.getEmail(),
                user.getCriadoEm()
        );
    }
}