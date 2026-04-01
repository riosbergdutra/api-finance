package com.api.finance.config;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuthenticatedUserProvider {

    public AuthenticatedUser get(Jwt jwt) {
        return new AuthenticatedUser(
                UUID.fromString(jwt.getSubject()));
    }
}