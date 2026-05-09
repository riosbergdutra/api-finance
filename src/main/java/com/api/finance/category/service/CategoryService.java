package com.api.finance.category.service;

import com.api.finance.category.dto.CategoryResponse;
import com.api.finance.category.dto.CreateCategoryRequest;
import com.api.finance.category.exception.CategoryNotFoundException;
import com.api.finance.category.model.Category;
import com.api.finance.category.model.CategoryType;
import com.api.finance.category.model.MerchantRule;
import com.api.finance.category.repository.CategoryRepository;
import com.api.finance.category.repository.MerchantRuleRepository;
import com.api.finance.config.AuthenticatedUser;
import com.api.finance.shared.exception.ResourceNotFoundException;
import com.api.finance.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final MerchantRuleRepository merchantRuleRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> listar(AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);
        return categoryRepository.findAllForUser(userId)
                .stream().map(CategoryResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listarPorTipo(CategoryType tipo, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);
        return categoryRepository.findAllForUserByTipo(userId, tipo)
                .stream().map(CategoryResponse::de).toList();
    }

    @Transactional
    public CategoryResponse criar(CreateCategoryRequest req, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);

        if (categoryRepository.existsByUserIdAndNomeIgnoreCase(userId, req.nome())) {
            throw new IllegalArgumentException("Já existe uma categoria com o nome: " + req.nome());
        }

        Category pai = null;
        if (req.paiId() != null) {
            pai = categoryRepository.findByIdForUser(req.paiId(), userId)
                    .orElseThrow(() -> new CategoryNotFoundException("Categoria pai não encontrada: " + req.paiId()));
        }

        Category cat = Category.builder()
                .userId(userId)
                .nome(req.nome().strip())
                .tipo(req.tipo())
                .icone(req.icone())
                .cor(req.cor())
                .pai(pai)
                .sistema(false)
                .build();

        return CategoryResponse.de(categoryRepository.save(cat));
    }

    @Transactional
    public CategoryResponse atualizar(UUID id, CreateCategoryRequest req, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);

        Category cat = categoryRepository.findByIdForUser(id, userId)
                .orElseThrow(() -> new CategoryNotFoundException("Categoria não encontrada: " + id));

        if (cat.isSistema()) {
            throw new IllegalStateException("Categorias de sistema não podem ser editadas.");
        }

        if (categoryRepository.existsByUserIdAndNomeIgnoreCaseAndIdNot(userId, req.nome(), id)) {
            throw new IllegalArgumentException("Já existe uma categoria com o nome: " + req.nome());
        }

        cat.setNome(req.nome().strip());
        cat.setTipo(req.tipo());
        cat.setIcone(req.icone());
        cat.setCor(req.cor());

        return CategoryResponse.de(categoryRepository.save(cat));
    }

    @Transactional
    public void deletar(UUID id, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);
        Category cat = categoryRepository.findByIdForUser(id, userId)
                .orElseThrow(() -> new CategoryNotFoundException("Categoria não encontrada: " + id));

        if (cat.isSistema()) {
            throw new IllegalStateException("Categorias de sistema não podem ser removidas.");
        }

        cat.setAtiva(false);
        categoryRepository.save(cat);
    }

    /**
     * Categoriza uma transação com base no nome do estabelecimento.
     * Busca MerchantRules por prioridade DESC e retorna a primeira que fizer match (contains, case-insensitive).
     */
    @Transactional(readOnly = true)
    public Optional<Category> categorizar(String nomeEstabelecimento, UUID userId) {
        if (nomeEstabelecimento == null || nomeEstabelecimento.isBlank()) return Optional.empty();

        String nome = nomeEstabelecimento.toLowerCase();
        return merchantRuleRepository.findRulesForUser(userId).stream()
                .filter(r -> nome.contains(r.getPadraoNome().toLowerCase()))
                .map(MerchantRule::getCategory)
                .findFirst();
    }

    /**
     * Cria uma MerchantRule para o usuário (aprender() do diagrama).
     */
    @Transactional
    public void aprenderRegra(String padraoNome, UUID categoryId, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);

        Category cat = categoryRepository.findByIdForUser(categoryId, userId)
                .orElseThrow(() -> new CategoryNotFoundException("Categoria não encontrada: " + categoryId));

        MerchantRule rule = MerchantRule.builder()
                .userId(userId)
                .padraoNome(padraoNome.toLowerCase().strip())
                .category(cat)
                .prioridade(1)
                .build();

        merchantRuleRepository.save(rule);
        log.info("MerchantRule criada: padrao={} user={}", padraoNome, userId);
    }

    private UUID resolveUserId(AuthenticatedUser caller) {
        return userRepository.findIdByKeycloakId(caller.id())
                .orElseThrow(() -> ResourceNotFoundException.of("User", caller.id()));
    }
}
