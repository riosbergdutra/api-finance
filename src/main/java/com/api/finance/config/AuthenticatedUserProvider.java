package com.api.finance.config;

@Component
public class AuthenticatedUserProvider {

    public AuthenticatedUser get(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();

        return new AuthenticatedUser(
            jwt.getSubject(),
            jwt.getClaimAsString("email")
        );
    }
}