package com.api.finance.bff.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponseDTO(
        @JsonProperty("access_token")
        String accessToken,

        @JsonIgnore
        String sessionId
) {
    // CORREÇÃO: O construtor precisa ser assim para não dar erro de recursão
    public AuthResponseDTO(String accessToken, String sessionId) {
        // O Java exige que o construtor customizado preencha os campos do record
        this.accessToken = accessToken;
        this.sessionId = sessionId;
    }
}