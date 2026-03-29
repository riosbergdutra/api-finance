package com.api.finance.bff.excetlions;

//erros de comunicacao com o keycloak
public class AuthIntegrationException extends RuntimeException {
    public AuthIntegrationException(String message) {
        super(message);
    }
}