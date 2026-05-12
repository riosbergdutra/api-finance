package com.api.finance.user;

import com.api.finance.config.AuthenticatedUser;
import com.api.finance.config.AuthenticatedUserProvider;
import com.api.finance.shared.TestFixtures;
import com.api.finance.shared.exception.ResourceNotFoundException;
import com.api.finance.user.dto.UserResponseDTO;
import com.api.finance.user.model.User;
import com.api.finance.user.repository.UserRepository;
import com.api.finance.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

import static com.api.finance.shared.TestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock AuthenticatedUserProvider userProvider;
    @InjectMocks UserService userService;

    @Test
    @DisplayName("getMe: retorna dados do usuário autenticado")
    void getMeRetornaDados() {
        Jwt jwt = mock(Jwt.class);
        User user = TestFixtures.user();

        given(userProvider.get(jwt)).willReturn(caller());
        given(userRepository.findByKeycloakId(KEYCLOAK_ID)).willReturn(Optional.of(user));

        UserResponseDTO result = userService.getMe(jwt);

        assertThat(result.nome()).isEqualTo("Test User");
        assertThat(result.email()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("getMe: lança ResourceNotFoundException quando usuário não existe")
    void getMeLancaNotFound() {
        Jwt jwt = mock(Jwt.class);
        given(userProvider.get(jwt)).willReturn(caller());
        given(userRepository.findByKeycloakId(KEYCLOAK_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMe(jwt))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
