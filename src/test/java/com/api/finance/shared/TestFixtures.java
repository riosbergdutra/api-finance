package com.api.finance.shared;

import com.api.finance.account.model.Account;
import com.api.finance.account.model.AccountType;
import com.api.finance.budget.model.Budget;
import com.api.finance.category.model.Category;
import com.api.finance.category.model.CategoryType;
import com.api.finance.config.AuthenticatedUser;
import com.api.finance.goal.model.Goal;
import com.api.finance.notification.model.Notification;
import com.api.finance.notification.model.NotificationType;
import com.api.finance.transaction.model.Transaction;
import com.api.finance.transaction.model.TransactionStatus;
import com.api.finance.transaction.model.TransactionType;
import com.api.finance.user.model.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Fábrica central de objetos de teste.
 * Todos os UUIDs são fixos para facilitar asserts determinísticos.
 */
public final class TestFixtures {

    public static final UUID USER_ID         = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID KEYCLOAK_ID     = UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final UUID ACCOUNT_ID      = UUID.fromString("00000000-0000-0000-0000-000000000010");
    public static final UUID CATEGORY_ID     = UUID.fromString("00000000-0000-0000-0000-000000000020");
    public static final UUID TRANSACTION_ID  = UUID.fromString("00000000-0000-0000-0000-000000000030");
    public static final UUID BUDGET_ID       = UUID.fromString("00000000-0000-0000-0000-000000000040");
    public static final UUID GOAL_ID         = UUID.fromString("00000000-0000-0000-0000-000000000050");
    public static final UUID NOTIFICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000060");

    private TestFixtures() {}

    public static AuthenticatedUser caller() {
        return new AuthenticatedUser(KEYCLOAK_ID);
    }

    public static User user() {
        User u = new User();
        u.setId(USER_ID);
        u.setKeycloakId(KEYCLOAK_ID);
        u.setNome("Test User");
        u.setEmail("test@example.com");
        return u;
    }

    public static Account account() {
        return Account.builder()
                .id(ACCOUNT_ID)
                .userId(USER_ID)
                .name("Conta Corrente")
                .type(AccountType.CORRENTE)
                .balance(BigDecimal.valueOf(1000))
                .currency("BRL")
                .active(true)
                .build();
    }

    public static Category category() {
        return Category.builder()
                .id(CATEGORY_ID)
                .userId(USER_ID)
                .nome("Alimentação")
                .tipo(CategoryType.DESPESA)
                .sistema(false)
                .ativa(true)
                .build();
    }

    public static Transaction transaction() {
        return Transaction.builder()
                .id(TRANSACTION_ID)
                .userId(USER_ID)
                .accountId(ACCOUNT_ID)
                .tipo(TransactionType.DESPESA)
                .status(TransactionStatus.CONFIRMADA)
                .valor(BigDecimal.valueOf(100))
                .descricao("Mercado")
                .estabelecimento("Supermercado X")
                .data(LocalDate.now())
                .hashDeduplicacao("abc123")
                .build();
    }

    public static Budget budget() {
        return Budget.builder()
                .id(BUDGET_ID)
                .userId(USER_ID)
                .valorLimite(BigDecimal.valueOf(500))
                .valorGasto(BigDecimal.valueOf(100))
                .mes(LocalDate.now().getMonthValue())
                .ano(LocalDate.now().getYear())
                .build();
    }

    public static Goal goal() {
        Goal g = new Goal();
        g.setId(GOAL_ID);
        g.setUserId(USER_ID);
        g.setNome("Viagem");
        g.setValorAlvo(BigDecimal.valueOf(5000));
        g.setValorAtual(BigDecimal.valueOf(1000));
        g.setConcluida(false);
        g.setCriadoEm(OffsetDateTime.now().minusDays(10));
        return g;
    }

    public static Notification notification() {
        return Notification.builder()
                .id(NOTIFICATION_ID)
                .userId(USER_ID)
                .tipo(NotificationType.ORCAMENTO_ALERTA)
                .titulo("Alerta de orçamento")
                .mensagem("Você atingiu 80% do orçamento.")
                .lida(false)
                .build();
    }
}
