package com.api.finance.user.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponseDTO(
        String nome,
        String email,
        OffsetDateTime criadoEm
) {
}
