package com.api.finance.transaction.repository;

import com.api.finance.transaction.model.Transaction;
import com.api.finance.transaction.model.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /** Listagem paginada com filtro de período — IDOR-safe */
    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId AND t.data BETWEEN :de AND :ate")
    Page<Transaction> findByUserIdAndPeriodo(
            @Param("userId") UUID userId,
            @Param("de") LocalDate de,
            @Param("ate") LocalDate ate,
            Pageable pageable);

    /** Busca individual garantindo ownership */
    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

    /** Soma de gastos por categoria num período — usado pelo BudgetService */
    @Query("""
        SELECT COALESCE(SUM(t.valor), 0) FROM Transaction t
        WHERE t.userId = :userId AND t.category.id = :categoryId
          AND t.tipo = 'DESPESA' AND t.status = 'CONFIRMADA'
          AND MONTH(t.data) = :mes AND YEAR(t.data) = :ano
        """)
    BigDecimal sumDespesaByCategoria(
            @Param("userId") UUID userId,
            @Param("categoryId") UUID categoryId,
            @Param("mes") int mes,
            @Param("ano") int ano);

    /** Verifica duplicidade por hash */
    boolean existsByHashDeduplicacao(String hash);
}
