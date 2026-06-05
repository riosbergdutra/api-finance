package com.api.finance.transaction.repository;

import com.api.finance.transaction.model.Transaction;
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

/**
 * ATENÇÃO: todas as queries usam BETWEEN LocalDate — NÃO use MONTH()/YEAR().
 * MONTH() e YEAR() são funções MySQL e NÃO existem no PostgreSQL.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId AND t.data BETWEEN :de AND :ate")
    Page<Transaction> findByUserIdAndPeriodo(
            @Param("userId") UUID userId,
            @Param("de") LocalDate de,
            @Param("ate") LocalDate ate,
            Pageable pageable);

    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

    @Query("""
        SELECT COALESCE(SUM(t.valor), 0) FROM Transaction t
        WHERE t.userId = :userId AND t.category.id = :categoryId
          AND t.tipo = 'DESPESA' AND t.status = 'CONFIRMADA'
          AND t.data BETWEEN :de AND :ate
        """)
    BigDecimal sumDespesaByCategoria(
            @Param("userId") UUID userId,
            @Param("categoryId") UUID categoryId,
            @Param("de") LocalDate de,
            @Param("ate") LocalDate ate);

    @Query("""
        SELECT COALESCE(SUM(t.valor), 0) FROM Transaction t
        WHERE t.userId = :userId AND t.tipo = 'RECEITA' AND t.status = 'CONFIRMADA'
          AND t.data BETWEEN :de AND :ate
        """)
    BigDecimal sumReceitasMes(
            @Param("userId") UUID userId,
            @Param("de") LocalDate de,
            @Param("ate") LocalDate ate);

    @Query("""
        SELECT COALESCE(SUM(t.valor), 0) FROM Transaction t
        WHERE t.userId = :userId AND t.tipo = 'DESPESA' AND t.status = 'CONFIRMADA'
          AND t.data BETWEEN :de AND :ate
        """)
    BigDecimal sumDespesasMes(
            @Param("userId") UUID userId,
            @Param("de") LocalDate de,
            @Param("ate") LocalDate ate);

    boolean existsByHashDeduplicacao(String hash);

    @Query("""
        SELECT COUNT(t) FROM Transaction t
        WHERE t.userId = :userId
          AND t.data BETWEEN :inicioMes AND :fimMes
        """)
    long countByUserIdAndMes(
            @Param("userId") UUID userId,
            @Param("inicioMes") LocalDate inicioMes,
            @Param("fimMes") LocalDate fimMes);

}