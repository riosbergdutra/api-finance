package com.api.finance.user.repository;

import java.util.Optional;
import java.util.UUID;

import com.api.finance.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByKeycloakId(UUID keycloakId);

    Optional<User> findByEmail(String email);
    void deleteByKeycloakId(UUID keycloakId);
    boolean existsByKeycloakId(UUID keycloakId);
    boolean existsByEmail(String email);
    @Query("SELECT u.id FROM User u WHERE u.keycloakId = :keycloakId")
    Optional<UUID> findIdByKeycloakId(@Param("keycloakId") UUID keycloakId);
}