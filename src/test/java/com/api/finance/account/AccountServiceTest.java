package com.api.finance.account;

import com.api.finance.account.dto.AccountResponse;
import com.api.finance.account.dto.CreateAccountRequest;
import com.api.finance.account.dto.UpdateAccountRequest;
import com.api.finance.account.exception.AccountNotFoundException;
import com.api.finance.account.exception.DuplicateAccountNameException;
import com.api.finance.account.model.Account;
import com.api.finance.account.model.AccountType;
import com.api.finance.account.repository.AccountRepository;
import com.api.finance.account.service.AccountService;
import com.api.finance.config.AuthenticatedUser;
import com.api.finance.shared.TestFixtures;
import com.api.finance.shared.exception.ResourceNotFoundException;
import com.api.finance.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.api.finance.shared.TestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService")
class AccountServiceTest {

    @Mock AccountRepository accountRepository;
    @Mock UserRepository userRepository;
    @InjectMocks AccountService accountService;

    AuthenticatedUser caller;
    Account account;

    @BeforeEach
    void setUp() {
        caller = caller();
        account = account();
        given(userRepository.findIdByKeycloakId(KEYCLOAK_ID)).willReturn(Optional.of(USER_ID));
    }

    // ─── listActiveAccounts ───────────────────────────────────────────

    @Nested @DisplayName("listActiveAccounts")
    class ListActiveAccounts {

        @Test
        @DisplayName("retorna contas ativas do usuário")
        void retornaContasAtivas() {
            given(accountRepository.findByUserIdAndActiveTrue(USER_ID)).willReturn(List.of(account));

            List<AccountResponse> result = accountService.listActiveAccounts(caller);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(ACCOUNT_ID);
        }

        @Test
        @DisplayName("retorna lista vazia quando não há contas")
        void retornaVazioSemContas() {
            given(accountRepository.findByUserIdAndActiveTrue(USER_ID)).willReturn(List.of());

            List<AccountResponse> result = accountService.listActiveAccounts(caller);

            assertThat(result).isEmpty();
        }
    }

    // ─── getById ─────────────────────────────────────────────────────

    @Nested @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("retorna conta quando encontrada e pertence ao usuário")
        void retornaConta() {
            given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).willReturn(Optional.of(account));

            AccountResponse result = accountService.getById(ACCOUNT_ID, caller);

            assertThat(result.id()).isEqualTo(ACCOUNT_ID);
            assertThat(result.name()).isEqualTo("Conta Corrente");
        }

        @Test
        @DisplayName("lança AccountNotFoundException quando conta não encontrada (IDOR protection)")
        void lancaNotFoundQuandoContaInexistente() {
            given(accountRepository.findByIdAndUserId(any(), eq(USER_ID))).willReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.getById(UUID.randomUUID(), caller))
                    .isInstanceOf(AccountNotFoundException.class);
        }
    }

    // ─── create ──────────────────────────────────────────────────────

    @Nested @DisplayName("create")
    class Create {

        @Test
        @DisplayName("cria conta com sucesso")
        void criaContaComSucesso() {
            CreateAccountRequest req = new CreateAccountRequest(
                    "Nova Conta", AccountType.CORRENTE, BigDecimal.ZERO, "BRL", null, null);

            given(accountRepository.countByUserIdAndActiveTrue(USER_ID)).willReturn(0L);
            given(accountRepository.existsByUserIdAndNameIgnoreCase(USER_ID, "Nova Conta")).willReturn(false);
            given(accountRepository.save(any())).willAnswer(inv -> {
                Account a = inv.getArgument(0);
                a.setId(ACCOUNT_ID);
                return a;
            });

            AccountResponse result = accountService.create(req, caller);

            assertThat(result.name()).isEqualTo("Nova Conta");
            then(accountRepository).should().save(any(Account.class));
        }

        @Test
        @DisplayName("lança IllegalStateException quando limite de contas atingido")
        void lancaExcecaoLimiteContas() {
            CreateAccountRequest req = new CreateAccountRequest(
                    "Extra", AccountType.CORRENTE, BigDecimal.ZERO, "BRL", null, null);

            given(accountRepository.countByUserIdAndActiveTrue(USER_ID)).willReturn(10L);

            assertThatThrownBy(() -> accountService.create(req, caller))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Limite");
        }

        @Test
        @DisplayName("lança DuplicateAccountNameException quando nome duplicado")
        void lancaExcecaoNomeDuplicado() {
            CreateAccountRequest req = new CreateAccountRequest(
                    "Conta Corrente", AccountType.CORRENTE, BigDecimal.ZERO, "BRL", null, null);

            given(accountRepository.countByUserIdAndActiveTrue(USER_ID)).willReturn(1L);
            given(accountRepository.existsByUserIdAndNameIgnoreCase(USER_ID, "Conta Corrente")).willReturn(true);

            assertThatThrownBy(() -> accountService.create(req, caller))
                    .isInstanceOf(DuplicateAccountNameException.class);
        }

        @Test
        @DisplayName("usa BRL e saldo zero como defaults quando não informados")
        void usaDefaultsQuandoNulo() {
            CreateAccountRequest req = new CreateAccountRequest(
                    "Conta", AccountType.POUPANCA, null, null, null, null);

            given(accountRepository.countByUserIdAndActiveTrue(USER_ID)).willReturn(0L);
            given(accountRepository.existsByUserIdAndNameIgnoreCase(any(), any())).willReturn(false);
            given(accountRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            AccountResponse result = accountService.create(req, caller);

            assertThat(result.balance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.currency()).isEqualTo("BRL");
        }
    }

    // ─── update ──────────────────────────────────────────────────────

    @Nested @DisplayName("update")
    class Update {

        @Test
        @DisplayName("atualiza conta com sucesso")
        void atualizaContaComSucesso() {
            UpdateAccountRequest req = new UpdateAccountRequest(
                    "Conta Atualizada", AccountType.POUPANCA, null, null);

            given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).willReturn(Optional.of(account));
            given(accountRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(USER_ID, "Conta Atualizada", ACCOUNT_ID)).willReturn(false);
            given(accountRepository.save(any())).willReturn(account);

            AccountResponse result = accountService.update(ACCOUNT_ID, req, caller);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("lança AccountNotFoundException quando conta não pertence ao usuário")
        void lancaNotFoundQuandoContaDeOutroUsuario() {
            UpdateAccountRequest req = new UpdateAccountRequest("X", AccountType.CORRENTE, null, null);
            given(accountRepository.findByIdAndUserId(any(), eq(USER_ID))).willReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.update(UUID.randomUUID(), req, caller))
                    .isInstanceOf(AccountNotFoundException.class);
        }
    }

    // ─── deactivate ───────────────────────────────────────────────────

    @Nested @DisplayName("deactivate")
    class Deactivate {

        @Test
        @DisplayName("desativa conta com sucesso")
        void desativaContaComSucesso() {
            given(accountRepository.deactivateByIdAndUserId(ACCOUNT_ID, USER_ID)).willReturn(1);

            assertThatCode(() -> accountService.deactivate(ACCOUNT_ID, caller))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("lança AccountNotFoundException quando nenhum registro afetado")
        void lancaNotFoundQuandoNenhumAfetado() {
            given(accountRepository.deactivateByIdAndUserId(any(), eq(USER_ID))).willReturn(0);

            assertThatThrownBy(() -> accountService.deactivate(UUID.randomUUID(), caller))
                    .isInstanceOf(AccountNotFoundException.class);
        }
    }

    // ─── resolveUserId ───────────────────────────────────────────────

    @Test
    @DisplayName("lança ResourceNotFoundException quando usuário não encontrado no banco")
    void lancaNotFoundQuandoUsuarioInexistente() {
        given(userRepository.findIdByKeycloakId(KEYCLOAK_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.listActiveAccounts(caller))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
