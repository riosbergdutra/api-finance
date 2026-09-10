package com.api.finance.openfinance.dto;

/**
 * Resposta ao iniciar o fluxo de consentimento Open Finance.
 * O frontend deve redirecionar o usuário para `consentUrl`.
 */
public record OpenFinanceConsentResponse(
        String consentUrl,   // URL para redirecionar o usuário ao banco de origem
        String state         // State para validar no callback (anti-CSRF)
) {}
