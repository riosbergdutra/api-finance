package com.api.finance.bff.repository;

import com.api.finance.bff.model.AuthSession;
import org.springframework.data.repository.CrudRepository;

public interface AuthSessionRepository extends CrudRepository<AuthSession, String> {
}
