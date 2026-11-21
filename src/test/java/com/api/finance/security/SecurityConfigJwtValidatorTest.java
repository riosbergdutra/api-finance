package com.api.finance.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigJwtValidatorTest {

    @Test
    void acceptsOnlyTokensIssuedForTheConfiguredKeycloakClient() {
        var validator = new org.springframework.security.oauth2.jwt.JwtClaimValidator<String>(
                "azp", "finance-bff"::equals);

        Jwt valid = Jwt.withTokenValue("opaque")
                .header("alg", "RS256")
                .subject("user-1")
                .claim("azp", "finance-bff")
                .build();
        Jwt wrongClient = Jwt.withTokenValue("opaque")
                .header("alg", "RS256")
                .subject("user-1")
                .claim("azp", "another-client")
                .build();
        Jwt missingClient = Jwt.withTokenValue("opaque")
                .header("alg", "RS256")
                .subject("user-1")
                .build();

        assertFalse(validator.validate(valid).hasErrors());
        assertTrue(validator.validate(wrongClient).hasErrors());
        assertTrue(validator.validate(missingClient).hasErrors());
    }
}
