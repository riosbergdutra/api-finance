package com.api.finance.subscription.repository;

import com.api.finance.subscription.model.PlanType;
import com.api.finance.subscription.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByUserId(UUID userId);

    /** Assinaturas PRO que expiram nos próximos N dias — usado pelo scheduler de alertas */
    @Query("""
        SELECT s FROM Subscription s
        WHERE s.plano = 'PRO'
          AND s.fimPeriodo IS NOT NULL
          AND s.fimPeriodo BETWEEN :hoje AND :limite
        """)
    List<Subscription> findProExpirandoAte(@Param("hoje") LocalDate hoje,
                                            @Param("limite") LocalDate limite);

    /** Assinaturas PRO expiradas que ainda não foram marcadas como EXPIRED */
    @Query("""
        SELECT s FROM Subscription s
        WHERE s.plano = 'PRO'
          AND s.status = 'ACTIVE'
          AND s.fimPeriodo < :hoje
        """)
    List<Subscription> findProExpiradas(@Param("hoje") LocalDate hoje);
}
