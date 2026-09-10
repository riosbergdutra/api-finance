package com.api.finance.shared;

import com.api.finance.config.AuthenticatedUser;
import com.api.finance.config.AuthenticatedUserProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("AuthenticatedUserProvider")
class AuthenticatedUserProviderTest {

    AuthenticatedUserProvider provider = new AuthenticatedUserProvider();

    @Test
    @DisplayName("extrai keycloakId do subject do JWT")
    void extraiKeycloakIdDoJwt() {
        Jwt jwt = mock(Jwt.class);
        given(jwt.getSubject()).willReturn(TestFixtures.KEYCLOAK_ID.toString());

        AuthenticatedUser result = provider.get(jwt);

        assertThat(result.id()).isEqualTo(TestFixtures.KEYCLOAK_ID);
    }

    @Test
    @DisplayName("cada chamada com JWTs diferentes retorna IDs diferentes")
    void diferentesJwtsDiferentesIds() {
        Jwt jwt1 = mock(Jwt.class);
        Jwt jwt2 = mock(Jwt.class);
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        given(jwt1.getSubject()).willReturn(id1.toString());
        given(jwt2.getSubject()).willReturn(id2.toString());

        assertThat(provider.get(jwt1).id()).isEqualTo(id1);
        assertThat(provider.get(jwt2).id()).isEqualTo(id2);
    }
}
