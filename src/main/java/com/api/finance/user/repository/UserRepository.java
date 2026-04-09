package com.api.finance.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;


import com.api.finance.user.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByKeycloakId(UUID keycloakId);

    @Query("SELECT u.id FROM User u WHERE u.keycloakId = :keycloakId")
    Optional<UUID> findIdByKeycloakId(@Param("keycloakId") UUID keycloakId);

    void deleteByKeycloakId(UUID keycloakId);
    boolean existsByKeycloakId(UUID keycloakId);

}