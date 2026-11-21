package com.api.finance.transaction.repository;

import com.api.finance.transaction.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * FIX: usada por orçamentos gerais (category = null), que devem somar TODAS
     * as despesas do usuário no período, independente da categoria.
     * Antes não existia, e BudgetService.calcularGasto() retornava sempre ZERO
     * quando categoryId era null — orçamento geral nunca refletia gasto real.
     */
    @Query("""
        SELECT COALESCE(SUM(t.valor), 0) FROM Transaction t
        WHERE t.userId = :userId
          AND t.tipo = 'DESPESA' AND t.status = 'CONFIRMADA'
          AND t.data BETWEEN :de AND :ate
        """)
    BigDecimal sumDespesaTotal(
            @Param("userId") UUID userId,
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

    /**
     * Insere a transação somente se a impressão digital ainda não existir.
     * A restrição UNIQUE do PostgreSQL é a autoridade final sob concorrência:
     * duas requisições simultâneas não conseguem criar o mesmo lançamento.
     *
     * @return 1 quando inserida; 0 quando a impressão digital já existia.
     */
    @Modifying
    @Query(value = """
        INSERT INTO transactions
            (id, user_id, account_id, conta_destino_id, category_id, tipo, status,
             valor, descricao, estabelecimento, data, hash_deduplicacao,
             transfer_destination_applied, created_at, updated_at)
        VALUES
            (:id, :userId, :accountId, :destinationAccountId, :categoryId, :type, :status,
             :amount, :description, :merchant, :date, :fingerprint,
             :destinationBalanceApplied, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        ON CONFLICT (hash_deduplicacao) DO NOTHING
        """, nativeQuery = true)
    int insertIfFingerprintAbsent(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("accountId") UUID accountId,
            @Param("destinationAccountId") UUID destinationAccountId,
            @Param("categoryId") UUID categoryId,
            @Param("type") String type,
            @Param("status") String status,
            @Param("amount") BigDecimal amount,
            @Param("description") String description,
            @Param("merchant") String merchant,
            @Param("date") LocalDate date,
            @Param("fingerprint") String fingerprint,
            @Param("destinationBalanceApplied") boolean destinationBalanceApplied
    );


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