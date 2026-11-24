package com.api.finance.security;

import com.api.finance.bff.service.AuthService;
import com.api.finance.bff.exceptions.SessionNotFoundException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BffSessionAuthenticationFilter extends OncePerRequestFilter {

    private static final String SESSION_COOKIE = "SESSION_ID";
    private final AuthService authService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || path.equals("/auth/login")
                || path.equals("/auth/callback")
                || path.equals("/auth/csrf")
                || path.equals("/auth/refresh")
                || path.equals("/auth/logout")
                || path.startsWith("/actuator/")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/webhooks/")
                || path.startsWith("/ws/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String sessionId = readSessionId(request);
        if (sessionId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                var jwt = authService.autenticarSessao(sessionId);
                var authorities = new JwtGrantedAuthoritiesConverter().convert(jwt);
                var authentication = new JwtAuthenticationToken(jwt, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            } catch (SessionNotFoundException | JwtException invalidSession) {
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Sessão inválida ou expirada.");
                return;
            } catch (RuntimeException authenticationUnavailable) {
                // Redis/Keycloak outages are service failures, not proof that the
                // browser session is invalid. Do not turn an outage into a logout.
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "Serviço de autenticação temporariamente indisponível.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String readSessionId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        var sessionCookies = Arrays.stream(cookies)
                .filter(cookie -> SESSION_COOKIE.equals(cookie.getName()))
                .toList();
        if (sessionCookies.size() != 1) {
            return null;
        }
        String value = sessionCookies.get(0).getValue();
        if (value == null || value.length() != 36) {
            return null;
        }
        try {
            UUID.fromString(value);
            return value;
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}
