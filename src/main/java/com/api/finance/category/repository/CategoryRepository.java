package com.api.finance.category.repository;

import com.api.finance.category.model.Category;
import com.api.finance.category.model.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    /** Categorias do usuário + categorias de sistema (userId null) */
    @Query("SELECT c FROM Category c WHERE (c.userId = :userId OR c.userId IS NULL) AND c.ativa = true")
    List<Category> findAllForUser(@Param("userId") UUID userId);

    @Query("SELECT c FROM Category c WHERE (c.userId = :userId OR c.userId IS NULL) AND c.tipo = :tipo AND c.ativa = true")
    List<Category> findAllForUserByTipo(@Param("userId") UUID userId, @Param("tipo") CategoryType tipo);

    /** Garante ownership: busca pelo id somente se pertence ao usuário ou é de sistema */
    @Query("SELECT c FROM Category c WHERE c.id = :id AND (c.userId = :userId OR c.userId IS NULL)")
    Optional<Category> findByIdForUser(@Param("id") UUID id, @Param("userId") UUID userId);

    boolean existsByUserIdAndNomeIgnoreCaseAndIdNot(UUID userId, String nome, UUID id);

    boolean existsByUserIdAndNomeIgnoreCase(UUID userId, String nome);
}
