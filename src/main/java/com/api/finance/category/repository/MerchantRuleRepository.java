package com.api.finance.category.repository;

import com.api.finance.category.model.MerchantRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MerchantRuleRepository extends JpaRepository<MerchantRule, UUID> {

    /**
     * Regras do usuário + sistema, ordenadas por prioridade DESC.
     * Usar a primeira que fizer match no categorizar().
     */
    @Query("SELECT r FROM MerchantRule r WHERE r.userId = :userId OR r.userId IS NULL ORDER BY r.prioridade DESC")
    List<MerchantRule> findRulesForUser(@Param("userId") UUID userId);
}
