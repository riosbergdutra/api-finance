package com.api.finance.bff.repository;

import com.api.finance.bff.model.AuthSession;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface AuthSessionRepository extends CrudRepository<AuthSession, String> {
    Optional<AuthSession> findByKeycloakId(String code);
}
