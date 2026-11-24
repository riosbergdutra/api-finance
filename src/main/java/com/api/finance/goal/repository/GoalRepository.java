package com.api.finance.goal.repository;

import com.api.finance.goal.model.Goal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GoalRepository extends JpaRepository<Goal, UUID> {

    List<Goal> findByUserIdAndConcluidaFalse(UUID userId);

    long countByUserIdAndConcluidaFalse(UUID userId);

    List<Goal> findByUserId(UUID userId);

    Optional<Goal> findByIdAndUserId(UUID id, UUID userId);

    /** Serializa depósitos, saques e alterações concorrentes do mesmo objetivo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM Goal g WHERE g.id = :id AND g.userId = :userId")
    Optional<Goal> findByIdAndUserIdForUpdate(
            @Param("id") UUID id,
            @Param("userId") UUID userId);
}
