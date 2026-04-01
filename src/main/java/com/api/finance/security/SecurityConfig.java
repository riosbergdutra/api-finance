package com.api.finance.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http

            // 2. Define permissões das rotas
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.GET, "/actuator/**").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                // O seu novo fluxo manual precisa dessas rotas abertas:
                .requestMatchers("/usuario/callback", "/usuario/refresh", "/usuario/logout").permitAll()
                .anyRequest().permitAll()
            )
            
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            
            // 3. REMOVEMOS o .oauth2Login() daqui! 
            // Agora o login é um endpoint comum no seu UserController.

            // 4. Configura o Logout (Apenas para limpar o contexto do Spring, se houver)
            .logout(logout -> logout
                .logoutUrl("/usuario/logout")
                .disable() // Desativamos o logout padrão para usar o seu do Controller
            )
            
            // 5. RESOURCE SERVER: Valida o Bearer JWT que o Front enviará
            .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()))
            
            // 6. STATELESS TOTAL: Nenhuma sessão será criada.
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            );

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
    }
}