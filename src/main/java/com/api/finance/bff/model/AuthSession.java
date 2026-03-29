package com.api.finance.bff.model;

import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RedisHash(value = "auth_session", timeToLive = 2592000)
public class AuthSession implements Serializable {

    @Id
    @Column(nullable = false, unique = true)
    private String sessionId;
    @Column(nullable = false)
    private String keycloakId;
    @Column(nullable = false, unique = true)
    private String refreshToken;
}