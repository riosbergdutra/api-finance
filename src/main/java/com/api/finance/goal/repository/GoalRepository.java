package com.api.finance.goal.repository;

import com.api.finance.goal.model.Goal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GoalRepository extends JpaRepository<Goal, UUID> {

    List<Goal> findByUserIdAndConcluidaFalse(UUID userId);

    List<Goal> findByUserId(UUID userId);

    Optional<Goal> findByIdAndUserId(UUID id, UUID userId);
}
