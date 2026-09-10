package com.api.finance.category;

import com.api.finance.category.dto.CategoryResponse;
import com.api.finance.category.dto.CreateCategoryRequest;
import com.api.finance.category.exception.CategoryNotFoundException;
import com.api.finance.category.model.Category;
import com.api.finance.category.model.CategoryType;
import com.api.finance.category.model.MerchantRule;
import com.api.finance.category.repository.CategoryRepository;
import com.api.finance.category.repository.MerchantRuleRepository;
import com.api.finance.category.service.CategoryService;
import com.api.finance.config.AuthenticatedUser;
import com.api.finance.shared.TestFixtures;
import com.api.finance.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.api.finance.shared.TestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryService")
class CategoryServiceTest {

    @Mock CategoryRepository categoryRepository;
    @Mock MerchantRuleRepository merchantRuleRepository;
    @Mock UserRepository userRepository;
    @InjectMocks CategoryService categoryService;

    AuthenticatedUser caller;
    Category category;

    @BeforeEach
    void setUp() {
        caller = caller();
        category = category();
        given(userRepository.findIdByKeycloakId(KEYCLOAK_ID)).willReturn(Optional.of(USER_ID));
    }

    // ─── listar ──────────────────────────────────────────────────────

    @Test
    @DisplayName("listar: retorna categorias do usuário e de sistema")
    void listarRetornaCategorias() {
        given(categoryRepository.findAllForUser(USER_ID)).willReturn(List.of(category));

        List<CategoryResponse> result = categoryService.listar(caller);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).nome()).isEqualTo("Alimentação");
    }

    // ─── criar ───────────────────────────────────────────────────────

    @Nested @DisplayName("criar")
    class Criar {

        @Test
        @DisplayName("cria categoria com sucesso")
        void criaComSucesso() {
            CreateCategoryRequest req = new CreateCategoryRequest(
                    "Transporte", CategoryType.DESPESA, null, null, null);

            given(categoryRepository.existsByUserIdAndNomeIgnoreCase(USER_ID, "Transporte")).willReturn(false);
            given(categoryRepository.save(any())).willAnswer(inv -> {
                Category c = inv.getArgument(0);
                c.setId(CATEGORY_ID);
                return c;
            });

            CategoryResponse result = categoryService.criar(req, caller);

            assertThat(result.nome()).isEqualTo("Transporte");
            assertThat(result.tipo()).isEqualTo(CategoryType.DESPESA);
        }

        @Test
        @DisplayName("lança IllegalArgumentException quando nome duplicado")
        void lancaExcecaoNomeDuplicado() {
            CreateCategoryRequest req = new CreateCategoryRequest(
                    "Alimentação", CategoryType.DESPESA, null, null, null);

            given(categoryRepository.existsByUserIdAndNomeIgnoreCase(USER_ID, "Alimentação")).willReturn(true);

            assertThatThrownBy(() -> categoryService.criar(req, caller))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Já existe");
        }

        @Test
        @DisplayName("resolve categoria pai quando paiId informado")
        void resolveCategoriaPai() {
            UUID paiId = UUID.randomUUID();
            CreateCategoryRequest req = new CreateCategoryRequest(
                    "Sub", CategoryType.DESPESA, null, null, paiId);

            Category pai = category();
            pai.setId(paiId);

            given(categoryRepository.existsByUserIdAndNomeIgnoreCase(USER_ID, "Sub")).willReturn(false);
            given(categoryRepository.findByIdForUser(paiId, USER_ID)).willReturn(Optional.of(pai));
            given(categoryRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            assertThatCode(() -> categoryService.criar(req, caller)).doesNotThrowAnyException();
            then(categoryRepository).should().findByIdForUser(paiId, USER_ID);
        }

        @Test
        @DisplayName("lança CategoryNotFoundException quando paiId não encontrado")
        void lancaNotFoundQuandoPaiInexistente() {
            UUID paiId = UUID.randomUUID();
            CreateCategoryRequest req = new CreateCategoryRequest(
                    "Sub", CategoryType.DESPESA, null, null, paiId);

            given(categoryRepository.existsByUserIdAndNomeIgnoreCase(any(), any())).willReturn(false);
            given(categoryRepository.findByIdForUser(paiId, USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.criar(req, caller))
                    .isInstanceOf(CategoryNotFoundException.class);
        }
    }

    // ─── atualizar ───────────────────────────────────────────────────

    @Nested @DisplayName("atualizar")
    class Atualizar {

        @Test
        @DisplayName("atualiza categoria com sucesso")
        void atualizaComSucesso() {
            CreateCategoryRequest req = new CreateCategoryRequest(
                    "Lazer", CategoryType.DESPESA, null, null, null);

            given(categoryRepository.findByIdForUser(CATEGORY_ID, USER_ID)).willReturn(Optional.of(category));
            given(categoryRepository.existsByUserIdAndNomeIgnoreCaseAndIdNot(USER_ID, "Lazer", CATEGORY_ID)).willReturn(false);
            given(categoryRepository.save(any())).willReturn(category);

            CategoryResponse result = categoryService.atualizar(CATEGORY_ID, req, caller);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("lança IllegalStateException quando tentativa de editar categoria de sistema")
        void lancaExcecaoCategoriaSistema() {
            category.setSistema(true);
            CreateCategoryRequest req = new CreateCategoryRequest(
                    "X", CategoryType.DESPESA, null, null, null);

            given(categoryRepository.findByIdForUser(CATEGORY_ID, USER_ID)).willReturn(Optional.of(category));

            assertThatThrownBy(() -> categoryService.atualizar(CATEGORY_ID, req, caller))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("sistema");
        }
    }

    // ─── deletar ─────────────────────────────────────────────────────

    @Nested @DisplayName("deletar")
    class Deletar {

        @Test
        @DisplayName("faz soft-delete com sucesso")
        void deletaComSucesso() {
            given(categoryRepository.findByIdForUser(CATEGORY_ID, USER_ID)).willReturn(Optional.of(category));
            given(categoryRepository.save(any())).willReturn(category);

            assertThatCode(() -> categoryService.deletar(CATEGORY_ID, caller)).doesNotThrowAnyException();
            assertThat(category.isAtiva()).isFalse();
        }

        @Test
        @DisplayName("lança IllegalStateException quando tentativa de deletar categoria de sistema")
        void lancaExcecaoCategoriaSistema() {
            category.setSistema(true);
            given(categoryRepository.findByIdForUser(CATEGORY_ID, USER_ID)).willReturn(Optional.of(category));

            assertThatThrownBy(() -> categoryService.deletar(CATEGORY_ID, caller))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ─── categorizar ─────────────────────────────────────────────────

    @Nested @DisplayName("categorizar")
    class Categorizar {

        @Test
        @DisplayName("retorna categoria quando regra faz match")
        void retornaCategoriaPorRegra() {
            MerchantRule rule = MerchantRule.builder()
                    .padraoNome("supermercado")
                    .category(category)
                    .prioridade(1)
                    .build();

            given(merchantRuleRepository.findRulesForUser(USER_ID)).willReturn(List.of(rule));

            Optional<Category> result = categoryService.categorizar("Supermercado Extra", USER_ID);

            assertThat(result).isPresent();
            assertThat(result.get().getNome()).isEqualTo("Alimentação");
        }

        @Test
        @DisplayName("retorna vazio quando nenhuma regra faz match")
        void retornaVazioSemMatch() {
            MerchantRule rule = MerchantRule.builder()
                    .padraoNome("farmacia")
                    .category(category)
                    .prioridade(1)
                    .build();

            given(merchantRuleRepository.findRulesForUser(USER_ID)).willReturn(List.of(rule));

            Optional<Category> result = categoryService.categorizar("Restaurante XYZ", USER_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("retorna vazio quando nome do estabelecimento é nulo ou branco")
        void retornaVazioComNomeNulo() {
            assertThat(categoryService.categorizar(null, USER_ID)).isEmpty();
            assertThat(categoryService.categorizar("  ", USER_ID)).isEmpty();
            then(merchantRuleRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("respeita prioridade das regras — regra de maior prioridade vence")
        void respeitaPrioridade() {
            Category outra = category();
            outra.setNome("Compras");

            MerchantRule sistemica = MerchantRule.builder()
                    .padraoNome("extra")
                    .category(outra)
                    .prioridade(0)
                    .build();

            MerchantRule usuario = MerchantRule.builder()
                    .padraoNome("extra")
                    .category(category)
                    .prioridade(1)
                    .build();

            // findRulesForUser retorna ordenado por prioridade DESC — usuário primeiro
            given(merchantRuleRepository.findRulesForUser(USER_ID)).willReturn(List.of(usuario, sistemica));

            Optional<Category> result = categoryService.categorizar("Supermercado Extra", USER_ID);

            assertThat(result).isPresent();
            assertThat(result.get().getNome()).isEqualTo("Alimentação");
        }
    }

    // ─── aprenderRegra ───────────────────────────────────────────────

    @Test
    @DisplayName("aprenderRegra: salva MerchantRule com sucesso")
    void aprenderRegraComSucesso() {
        given(categoryRepository.findByIdForUser(CATEGORY_ID, USER_ID)).willReturn(Optional.of(category));

        assertThatCode(() -> categoryService.aprenderRegra("padaria", CATEGORY_ID, caller))
                .doesNotThrowAnyException();

        then(merchantRuleRepository).should().save(any(MerchantRule.class));
    }
}
