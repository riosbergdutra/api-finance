package com.api.finance.security;

import com.api.finance.bff.service.AuthService;
import com.api.finance.bff.exceptions.AuthIntegrationException;
import com.api.finance.bff.exceptions.SessionNotFoundException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import jakarta.servlet.ServletException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BffSessionAuthenticationFilterTest {

    @Mock
    private AuthService authService;

    private BffSessionAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new BffSessionAuthenticationFilter(authService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deveAutenticarComCookieDeSessaoValido() throws ServletException, IOException {
        MockHttpServletRequest request = request("/api/users/me");
        request.setCookies(new jakarta.servlet.http.Cookie("SESSION_ID", "opaque-session-id"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        Jwt jwt = Jwt.withTokenValue("test-access-token")
                .header("alg", "none")
                .subject("user-123")
                .build();
        when(authService.autenticarSessao("opaque-session-id")).thenReturn(jwt);

        filter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().isAuthenticated());
        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
        verify(authService).autenticarSessao("opaque-session-id");
    }

    @Test
    void deveResponder401QuandoSessaoForInvalida() throws ServletException, IOException {
        MockHttpServletRequest request = request("/api/users/me");
        request.setCookies(new jakarta.servlet.http.Cookie("SESSION_ID", "invalid-session"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            throw new AssertionError("A cadeia não deveria continuar para uma sessão inválida.");
        };
        when(authService.autenticarSessao("invalid-session"))
                .thenThrow(new SessionNotFoundException("invalid"));

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertFalse(SecurityContextHolder.getContext().getAuthentication() != null);
    }

    @Test
    void deveResponder503QuandoOProvedorDeAutenticacaoEstiverIndisponivel() throws ServletException, IOException {
        MockHttpServletRequest request = request("/api/users/me");
        request.setCookies(new jakarta.servlet.http.Cookie("SESSION_ID", "opaque-session-id"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            throw new AssertionError("A cadeia não deveria continuar durante indisponibilidade de autenticação.");
        };
        when(authService.autenticarSessao("opaque-session-id"))
                .thenThrow(new AuthIntegrationException("provider unavailable"));

        filter.doFilter(request, response, chain);

        assertEquals(503, response.getStatus());
    }

    @Test
    void naoDeveConsultarSessaoEmEndpointDeRefresh() throws ServletException, IOException {
        MockHttpServletRequest request = request("/auth/refresh");
        request.setMethod("POST");
        request.setCookies(new jakarta.servlet.http.Cookie("SESSION_ID", "opaque-session-id"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
        verify(authService, never()).autenticarSessao("opaque-session-id");
    }

    @Test
    void deveIgnorarCookieDeSessaoDuplicadoOuMalformado() throws ServletException, IOException {
        MockHttpServletRequest request = request("/api/users/me");
        request.setCookies(
                new jakarta.servlet.http.Cookie("SESSION_ID", "not-a-uuid"),
                new jakarta.servlet.http.Cookie("SESSION_ID", "another-value"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertTrue(SecurityContextHolder.getContext().getAuthentication() == null);
        verify(authService, never()).autenticarSessao(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void deveContinuarSemCookieParaQueSecurityConfigDecidaAcesso() throws ServletException, IOException {
        MockHttpServletRequest request = request("/api/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
        assertTrue(SecurityContextHolder.getContext().getAuthentication() == null);
        verify(authService, never()).autenticarSessao(org.mockito.ArgumentMatchers.anyString());
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath(path);
        request.setMethod("GET");
        return request;
    }
}
