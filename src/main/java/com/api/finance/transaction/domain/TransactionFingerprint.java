package com.api.finance.transaction.domain;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Identidade determinística de uma transação sem identificador fornecido pela origem.
 *
 * <p>O formato usa campos normalizados e delimitadores com comprimento para evitar
 * ambiguidades. Quando uma origem fornecer FITID/ID externo, esse identificador
 * deverá ser usado em uma chave de idempotência própria, em vez desta impressão digital.</p>
 */
public record TransactionFingerprint(String value) {

    public TransactionFingerprint {
        Objects.requireNonNull(value, "value");
        if (!value.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("A impressão digital deve ser SHA-256 hexadecimal.");
        }
    }

    public static TransactionFingerprint of(
            UUID userId,
            UUID accountId,
            BigDecimal amount,
            LocalDate date,
            String description
    ) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(date, "date");

        String normalizedAmount = amount.stripTrailingZeros().toPlainString();
        String normalizedDescription = normalizeDescription(description);
        String canonical = field(userId.toString())
                + field(accountId.toString())
                + field(normalizedAmount)
                + field(date.toString())
                + field(normalizedDescription);

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return new TransactionFingerprint(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException impossibleOnSupportedJdks) {
            throw new IllegalStateException("SHA-256 não está disponível nesta JVM.",
                    impossibleOnSupportedJdks);
        }
    }

    private static String normalizeDescription(String description) {
        if (description == null) {
            return "";
        }
        return Normalizer.normalize(description, Normalizer.Form.NFC)
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String field(String value) {
        return value.length() + ":" + value + "|";
    }
}
