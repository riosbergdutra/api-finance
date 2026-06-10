package com.api.finance.transaction;

import com.api.finance.account.exception.AccountNotFoundException;
import com.api.finance.account.model.Account;
import com.api.finance.account.repository.AccountRepository;
import com.api.finance.category.model.Category;
import com.api.finance.category.repository.CategoryRepository;
import com.api.finance.category.service.CategoryService;
import com.api.finance.config.AuthenticatedUser;
import com.api.finance.transaction.dto.CreateTransactionRequest;
import com.api.finance.transaction.dto.TransactionResponse;
import com.api.finance.transaction.dto.UpdateTransactionRequest;
import com.api.finance.transaction.exception.TransactionNotFoundException;
import com.api.finance.transaction.model.Transaction;
import com.api.finance.transaction.model.TransactionStatus;
import com.api.finance.transaction.model.TransactionType;
import com.api.finance.transaction.repository.TransactionRepository;
import com.api.finance.transaction.service.TransactionService;
import com.api.finance.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.api.finance.shared.TestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionService")
class TransactionServiceTest {

    @Mock TransactionRepository transactionRepository;
    @Mock AccountRepository accountRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock CategoryService categoryService;
    @Mock UserRepository userRepository;
    @InjectMocks TransactionService transactionService;

    AuthenticatedUser caller;
    Account account;
    Transaction transaction;

    @BeforeEach
    void setUp() {
        caller = caller();
        account = account();
        transaction = transaction();
        given(userRepository.findIdByKeycloakId(KEYCLOAK_ID)).willReturn(Optional.of(USER_ID));
    }

    // ─── listar ──────────────────────────────────────────────────────

    @Test
    @DisplayName("listar: retorna page de transações do período")
    void listarRetornaPage() {
        Page<Transaction> page = new PageImpl<>(List.of(transaction));
        given(transactionRepository.findByUserIdAndPeriodo(eq(USER_ID), any(), any(), any(Pageable.class)))
                .willReturn(page);

        Page<TransactionResponse> result = transactionService.listar(
                LocalDate.now().minusDays(5), LocalDate.now(), Pageable.unpaged(), caller);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(TRANSACTION_ID);
    }

    // ─── buscarPorId ─────────────────────────────────────────────────

    @Test
    @DisplayName("buscarPorId: lança TransactionNotFoundException quando não encontrada")
    void buscarPorIdLancaNotFound() {
        given(transactionRepository.findByIdAndUserId(any(), eq(USER_ID))).willReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.buscarPorId(UUID.randomUUID(), caller))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    // ─── criar ───────────────────────────────────────────────────────

    @Nested @DisplayName("criar")
    class Criar {

        @Test
        @DisplayName("cria transação DESPESA e debita saldo da conta")
        void criaDespesaEDebitaSaldo() {
            CreateTransactionRequest req = new CreateTransactionRequest(
                    ACCOUNT_ID, null, null, TransactionType.DESPESA, TransactionStatus.CONFIRMADA,
                    BigDecimal.valueOf(100), "Mercado", "Supermercado X", LocalDate.now());

            given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).willReturn(Optional.of(account));
            given(transactionRepository.existsByHashDeduplicacao(any())).willReturn(false);
            given(categoryService.categorizar(any(), eq(USER_ID))).willReturn(Optional.empty());
            given(transactionRepository.save(any())).willReturn(transaction);

            TransactionResponse result = transactionService.criar(req, caller);

            assertThat(result).isNotNull();
            // Saldo inicial 1000 - 100 = 900
            assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(900));
            then(accountRepository).should().save(account);
        }

        @Test
        @DisplayName("cria transação RECEITA e credita saldo da conta")
        void criaReceitaECreditaSaldo() {
            CreateTransactionRequest req = new CreateTransactionRequest(
                    ACCOUNT_ID, null, null, TransactionType.RECEITA, TransactionStatus.CONFIRMADA,
                    BigDecimal.valueOf(500), "Salário", null, LocalDate.now());

            given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).willReturn(Optional.of(account));
            given(transactionRepository.existsByHashDeduplicacao(any())).willReturn(false);
            given(categoryService.categorizar(any(), any())).willReturn(Optional.empty());
            given(transactionRepository.save(any())).willReturn(transaction);

            transactionService.criar(req, caller);

            // Saldo inicial 1000 + 500 = 1500
            assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1500));
        }

        @Test
        @DisplayName("não ajusta saldo quando status é PENDENTE")
        void naoAjustaSaldoPendente() {
            BigDecimal saldoOriginal = account.getBalance();
            CreateTransactionRequest req = new CreateTransactionRequest(
                    ACCOUNT_ID, null, null, TransactionType.DESPESA, TransactionStatus.PENDENTE,
                    BigDecimal.valueOf(200), "Fatura", null, LocalDate.now());

            given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).willReturn(Optional.of(account));
            given(transactionRepository.existsByHashDeduplicacao(any())).willReturn(false);
            given(categoryService.categorizar(any(), any())).willReturn(Optional.empty());
            given(transactionRepository.save(any())).willReturn(transaction);

            transactionService.criar(req, caller);

            assertThat(account.getBalance()).isEqualByComparingTo(saldoOriginal);
            then(accountRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("lança IllegalStateException quando transação duplicada")
        void lancaExcecaoTransacaoDuplicada() {
            CreateTransactionRequest req = new CreateTransactionRequest(
                    ACCOUNT_ID, null, null, TransactionType.DESPESA, null,
                    BigDecimal.valueOf(100), "Mercado", null, LocalDate.now());

            given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).willReturn(Optional.of(account));
            given(transactionRepository.existsByHashDeduplicacao(any())).willReturn(true);

            assertThatThrownBy(() -> transactionService.criar(req, caller))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("duplicada");
        }

        @Test
        @DisplayName("lança AccountNotFoundException quando conta não pertence ao usuário")
        void lancaExcecaoContaNaoEncontrada() {
            CreateTransactionRequest req = new CreateTransactionRequest(
                    UUID.randomUUID(), null, null, TransactionType.DESPESA, null,
                    BigDecimal.valueOf(100), "X", null, LocalDate.now());

            given(accountRepository.findByIdAndUserId(any(), eq(USER_ID))).willReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.criar(req, caller))
                    .isInstanceOf(AccountNotFoundException.class);
        }

        @Test
        @DisplayName("usa categorização automática quando categoryId é nulo")
        void usaCategorizacaoAutomatica() {
            Category cat = category();
            CreateTransactionRequest req = new CreateTransactionRequest(
                    ACCOUNT_ID, null, null, TransactionType.DESPESA, TransactionStatus.CONFIRMADA,
                    BigDecimal.valueOf(50), "Padaria", "Padaria Estrela", LocalDate.now());

            given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).willReturn(Optional.of(account));
            given(transactionRepository.existsByHashDeduplicacao(any())).willReturn(false);
            given(categoryService.categorizar("Padaria Estrela", USER_ID)).willReturn(Optional.of(cat));
            given(transactionRepository.save(any())).willReturn(transaction);

            transactionService.criar(req, caller);

            then(categoryService).should().categorizar("Padaria Estrela", USER_ID);
        }
    }

    // ─── atualizar ───────────────────────────────────────────────────

    @Nested @DisplayName("atualizar")
    class Atualizar {

        @Test
        @DisplayName("reverte saldo antigo e aplica novo quando ambas confirmadas")
        void reverteEAplicaNovaSaldo() {
            // Transação original: DESPESA 100 CONFIRMADA → conta 1000
            UpdateTransactionRequest req = new UpdateTransactionRequest(
                    null, TransactionType.DESPESA, TransactionStatus.CONFIRMADA,
                    BigDecimal.valueOf(200), "Ajuste", null, LocalDate.now());

            given(transactionRepository.findByIdAndUserId(TRANSACTION_ID, USER_ID)).willReturn(Optional.of(transaction));
            given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).willReturn(Optional.of(account)).willReturn(Optional.of(account));
            given(categoryService.categorizar(any(), any())).willReturn(Optional.empty());
            given(transactionRepository.save(any())).willReturn(transaction);

            transactionService.atualizar(TRANSACTION_ID, req, caller);

            // Reverteu DESPESA 100 → +100 (1100), depois aplicou DESPESA 200 → -200 (900)
            assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(900));
        }
    }

    // ─── deletar ─────────────────────────────────────────────────────

    @Nested @DisplayName("deletar")
    class Deletar {

        @Test
        @DisplayName("deleta transação e reverte saldo da conta")
        void deletaEReverteSaldo() {
            given(transactionRepository.findByIdAndUserId(TRANSACTION_ID, USER_ID)).willReturn(Optional.of(transaction));
            given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).willReturn(Optional.of(account));

            transactionService.deletar(TRANSACTION_ID, caller);

            then(transactionRepository).should().delete(transaction);
            // DESPESA 100 revertida → saldo volta a 1100
            assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1100));
        }

        @Test
        @DisplayName("lança TransactionNotFoundException quando não encontrada")
        void lancaNotFound() {
            given(transactionRepository.findByIdAndUserId(any(), eq(USER_ID))).willReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.deletar(UUID.randomUUID(), caller))
                    .isInstanceOf(TransactionNotFoundException.class);
        }

        @Test
        @DisplayName("não tenta reverter saldo quando transação é PENDENTE")
        void naoReverteSaldoPendente() {
            transaction.setStatus(TransactionStatus.PENDENTE);
            given(transactionRepository.findByIdAndUserId(TRANSACTION_ID, USER_ID)).willReturn(Optional.of(transaction));

            transactionService.deletar(TRANSACTION_ID, caller);

            then(accountRepository).should(never()).findByIdAndUserId(any(), any());
        }
    }
}
