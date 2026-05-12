package com.api.finance.budget.repository;

import com.api.finance.budget.model.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    List<Budget> findByUserIdAndMesAndAno(UUID userId, int mes, int ano);

    Optional<Budget> findByIdAndUserId(UUID id, UUID userId);

    /** Verifica duplicidade antes de criar */
    boolean existsByUserIdAndCategoryIdAndMesAndAno(UUID userId, UUID categoryId, int mes, int ano);

    /** Orçamentos com alertaEm definido — usados pelo job de verificação */
    @Query("SELECT b FROM Budget b WHERE b.userId = :userId AND b.mes = :mes AND b.ano = :ano AND b.alertaEm IS NOT NULL")
    List<Budget> findComAlertaAtivo(@Param("userId") UUID userId, @Param("mes") int mes, @Param("ano") int ano);
}
