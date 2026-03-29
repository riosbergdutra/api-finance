package com.api.finance.bff.dto;

public record ErrorResponseDTO(
        String message,
        long timestamp,
        int status
) {}