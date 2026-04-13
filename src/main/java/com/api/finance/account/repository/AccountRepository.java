package com.api.finance.account.repository;

import com.api.finance.account.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório do módulo Account.
 *
 * REGRA DE SEGURANÇA (IDOR Prevention):
 * Todas as queries que retornam ou modificam dados de uma conta específica
 * DEVEM receber userId como parâmetro e filtrá-lo na query.
 *
 * NUNCA use findById(accountId) isolado em código de produção —
 * isso permite que um usuário acesse dados de outro se adivinhar o UUID.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    /**
     * Lista apenas contas ativas do usuário.
     * Query padrão para o dashboard.
     */
    List<Account> findByUserIdAndActiveTrue(UUID userId);

    /**
     * Busca uma conta garantindo que pertence ao userId informado.
     * Use este método em TODOS os endpoints de leitura/escrita por ID.
     *
     * @param id     UUID da conta
     * @param userId UUID interno do usuário (extraído do JWT — nunca do body)
     */
    Optional<Account> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Verifica se um nome de conta já existe para o usuário (evitar duplicatas).
     */
    boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name);

    /**
     * Verifica se um nome de conta já existe excluindo a própria conta (para update).
     */
    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(UUID userId, String name, UUID id);

    /**
     * Soft-delete: desativa a conta sem remover do banco.
     * Preserva histórico de transações associadas.
     */
    @Modifying
    @Query("UPDATE Account a SET a.active = false WHERE a.id = :id AND a.userId = :userId")
    int deactivateByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    /**
     * Contagem de contas ativas — usada para limitar criação (ex: plano freemium).
     */
    long countByUserIdAndActiveTrue(UUID userId);
}
