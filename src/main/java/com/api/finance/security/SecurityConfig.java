package com.api.finance.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Configuração de segurança do FinanceFlow.
 *
 * MODELO BFF STATELESS:
 * - Nenhuma sessão HTTP criada (STATELESS)
 * - Bearer JWT validado em todo request de negócio
 * - Refresh token fica em Redis (AuthSession), nunca exposto
 * - CSRF desabilitado: API stateless com JWT não precisa (sem cookies de autenticação)
 *
 * FAIL-SAFE: anyRequest().denyAll() — qualquer rota não mapeada é bloqueada.
 * Nunca use permitAll() como regra final.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        // Monitoramento — só health e info expostos
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                        // Documentação da API
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // BFF auth — sem JWT (recebe authorization code do Keycloak)
                        .requestMatchers(HttpMethod.GET, "/auth/login", "auth/callback").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/refresh", "/auth/logout", "auth/callback").permitAll()
                        // WebSocket — autenticado via query param token (não Bearer header)
                        .requestMatchers("/ws/**").permitAll()
                        // Módulos de negócio — JWT obrigatório
                        .requestMatchers("/users/**").authenticated()
                        .requestMatchers("/accounts/**").authenticated()
                        .requestMatchers("/transactions/**").authenticated()
                        .requestMatchers("/categories/**").authenticated()
                        .requestMatchers("/budgets/**").authenticated()
                        .requestMatchers("/goals/**").authenticated()
                        .requestMatchers("/notifications/**").authenticated()
                        .requestMatchers("/dashboard/**").authenticated()
                        .requestMatchers("/subscriptions/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/webhooks/mercadopago").permitAll()
                        .requestMatchers("/export/**").authenticated()
                        .requestMatchers("/open-finance/**").authenticated()
                        // FAIL-SAFE: bloqueia qualquer rota não mapeada explicitamente
                        .anyRequest().denyAll()
                )
                // API stateless com JWT — CSRF não aplicável
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .logout(logout -> logout.disable())
                // Valida Bearer JWT em todo request de negócio
                .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()))
                // Sem sessão HTTP
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                // Security headers
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)
                        )
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                        )
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("default-src 'self'; frame-ancestors 'none'")
                        )
                        .contentTypeOptions(Customizer.withDefaults())
                );

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
    }
}