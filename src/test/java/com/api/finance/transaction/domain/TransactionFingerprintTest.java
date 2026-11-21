package com.api.finance.transaction.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionFingerprintTest {

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACCOUNT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final LocalDate DATE = LocalDate.of(2026, 11, 20);

    @Test
    void fingerprintIsStableForEquivalentAmountsAndWhitespace() {
        var first = TransactionFingerprint.of(USER, ACCOUNT,
                new BigDecimal("10.0"), DATE, "  Mercado   Central ");
        var second = TransactionFingerprint.of(USER, ACCOUNT,
                new BigDecimal("10.00"), DATE, "Mercado Central");

        assertThat(first).isEqualTo(second);
        assertThat(first.value()).hasSize(64).matches("[a-f0-9]{64}");
    }

    @Test
    void fingerprintChangesWhenUserOrAccountChanges() {
        var original = TransactionFingerprint.of(USER, ACCOUNT,
                new BigDecimal("10.00"), DATE, "Compra");
        var otherUser = TransactionFingerprint.of(UUID.randomUUID(), ACCOUNT,
                new BigDecimal("10.00"), DATE, "Compra");
        var otherAccount = TransactionFingerprint.of(USER, UUID.randomUUID(),
                new BigDecimal("10.00"), DATE, "Compra");

        assertThat(otherUser).isNotEqualTo(original);
        assertThat(otherAccount).isNotEqualTo(original);
    }

    @Test
    void fingerprintChangesWhenBusinessDataChanges() {
        var original = TransactionFingerprint.of(USER, ACCOUNT,
                new BigDecimal("10.00"), DATE, "Compra");
        var otherDate = TransactionFingerprint.of(USER, ACCOUNT,
                new BigDecimal("10.00"), DATE.plusDays(1), "Compra");
        var otherDescription = TransactionFingerprint.of(USER, ACCOUNT,
                new BigDecimal("10.00"), DATE, "Outra compra");

        assertThat(otherDate).isNotEqualTo(original);
        assertThat(otherDescription).isNotEqualTo(original);
    }
}
