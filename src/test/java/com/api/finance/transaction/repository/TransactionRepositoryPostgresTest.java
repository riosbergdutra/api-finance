package com.api.finance.transaction.repository;

import com.api.finance.transaction.model.Transaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class TransactionRepositoryPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired
    private TransactionRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void insertIfFingerprintAbsentUsesPostgresUniqueConstraintAtomically() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        String fingerprint = "a".repeat(64);

        int firstInsert = insert(firstId, userId, accountId, fingerprint);
        int duplicateInsert = insert(UUID.randomUUID(), userId, accountId, fingerprint);

        assertThat(firstInsert).isEqualTo(1);
        assertThat(duplicateInsert).isZero();

        Transaction stored = repository.findByIdAndUserId(firstId, userId).orElseThrow();
        assertThat(stored.getHashDeduplicacao()).isEqualTo(fingerprint);
        assertThat(stored.getValor()).isEqualByComparingTo("42.50");
    }

    @Test
    void generalBudgetAllowsOnlyOnePerUserAndPeriodWhenCategoryIsNull() {
        UUID userId = UUID.randomUUID();
        insertGeneralBudget(UUID.randomUUID(), userId);

        assertThatThrownBy(() -> insertGeneralBudget(UUID.randomUUID(), userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void stripeWebhookInboxDeduplicatesRepeatedEventIds() {
        String eventId = "evt_" + UUID.randomUUID();

        int firstDelivery = jdbcTemplate.update("""
                INSERT INTO stripe_webhook_events (event_id, event_type)
                VALUES (?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """, eventId, "customer.subscription.updated");

        int repeatedDelivery = jdbcTemplate.update("""
                INSERT INTO stripe_webhook_events (event_id, event_type)
                VALUES (?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """, eventId, "customer.subscription.updated");

        assertThat(firstDelivery).isEqualTo(1);
        assertThat(repeatedDelivery).isZero();
    }

    private void insertGeneralBudget(UUID id, UUID userId) {
        jdbcTemplate.update("""
                INSERT INTO budgets (id, user_id, category_id, valor_limite, valor_gasto, mes, ano)
                VALUES (?, ?, NULL, 1000.00, 0.00, 11, 2026)
                """, id, userId);
    }

    private int insert(UUID id, UUID userId, UUID accountId, String fingerprint) {
        return repository.insertIfFingerprintAbsent(
                id,
                userId,
                accountId,
                null,
                null,
                "DESPESA",
                "CONFIRMADA",
                new BigDecimal("42.50"),
                "Compra de teste",
                "Loja de teste",
                LocalDate.of(2026, 11, 20),
                fingerprint
        );
    }
}
