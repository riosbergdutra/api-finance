package com.api.finance.bff.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Retries provider-side identity deletion after local financial data has been
 * erased. The database outbox makes the operation durable across restarts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakAccountDeletionProcessor {

    private final JdbcTemplate jdbcTemplate;
    private final KeycloakAdminClient keycloakAdminClient;

    public void processOne(UUID keycloakId) {
        try {
            keycloakAdminClient.deleteUser(keycloakId);
            jdbcTemplate.update("DELETE FROM account_deletion_outbox WHERE keycloak_id = ?", keycloakId);
            log.info("Exclusão remota de identidade concluída e removida da fila.");
        } catch (RuntimeException providerOrDatabaseFailure) {
            try {
                jdbcTemplate.update("""
                        UPDATE account_deletion_outbox
                        SET attempts = attempts + 1,
                            last_error = 'KEYCLOAK_ADMIN_UNAVAILABLE',
                            next_attempt_at = CURRENT_TIMESTAMP
                                + (LEAST(3600, (30 * POWER(2, LEAST(attempts, 7)))::int) * INTERVAL '1 second')
                        WHERE keycloak_id = ?
                        """, keycloakId);
            } catch (RuntimeException persistenceFailure) {
                log.error("Não foi possível atualizar o backoff da fila de exclusão; a solicitação permanece persistida.");
            }
            log.warn("Exclusão remota de identidade pendente; a fila tentará novamente automaticamente.");
        }
    }

    @Scheduled(fixedDelayString = "${app.account-deletion.outbox.poll-interval:30000}")
    @net.javacrumbs.shedlock.spring.annotation.SchedulerLock(name = "keycloakAccountDeletionOutbox", lockAtMostFor = "PT10M", lockAtLeastFor = "PT5S")
    public void processPending() {
        List<UUID> pending = jdbcTemplate.query("""
                SELECT keycloak_id
                FROM account_deletion_outbox
                WHERE next_attempt_at <= CURRENT_TIMESTAMP
                ORDER BY requested_at
                LIMIT 25
                """, (rs, row) -> rs.getObject("keycloak_id", UUID.class));
        pending.forEach(this::processOne);
    }
}
