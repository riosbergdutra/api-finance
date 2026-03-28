package com.api.finance.bff.model;

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
@RedisHash(value = "AuthSession", timeToLive = 2592000)
public class AuthSession implements Serializable {

    @Id
    private String sessionId;

    @Indexed
    private String keycloakId;
    
    private String refreshToken;
}