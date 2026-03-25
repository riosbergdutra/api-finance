package com.api.finance.user.service;

import java.util.UUID;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.api.finance.user.model.User;
import com.api.finance.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;

    /**
     * Busca um usuário pelo Keycloak ID.
     * @param keycloakId ID do Keycloak (claim "sub")
     * @return Usuário encontrado
     * @throws RuntimeException se não encontrado
     */
    public User buscarPorKeycloakId(UUID keycloakId) {
        return repository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
    }

    /**
     * Obtém o Keycloak ID do usuário a partir do JWT atual.
     * @param jwt JWT do request
     * @return UUID do Keycloak
     */
    public UUID obterKeycloakIdAtual(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("sub"));
    }

    /**
     * Método utilitário para obter o ID interno do usuário
     * sem precisar passar na URL.
     */
    public UUID obterUsuarioId(Jwt jwt) {
        UUID keycloakId = obterKeycloakIdAtual(jwt);
        return buscarPorKeycloakId(keycloakId).getId();
    }

    /**
     * Cria ou atualiza usuário a partir dos claims do JWT do Keycloak
     */
    @Transactional
   public User criarOuAtualizarUsuario(OAuth2AuthenticationToken authToken) {
    String sub = authToken.getPrincipal().getAttribute("sub");
    String nome = authToken.getPrincipal().getAttribute("preferred_username");
    String email = authToken.getPrincipal().getAttribute("email");

    UUID keycloakId = UUID.fromString(sub);

    User usuario = repository.findByKeycloakId(keycloakId)
            .orElseGet(() -> {
                User novo = new User();
                novo.setKeycloakId(keycloakId);
                novo.setEmail(email);
                return novo;
            });

    usuario.setNome(nome);
    usuario.setEmail(email);
    usuario.setAtivo(true);

    return repository.save(usuario);
}
}

