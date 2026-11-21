package com.api.finance.bff.service;

import com.api.finance.bff.exceptions.AuthIntegrationException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KeycloakAccountDeletionProcessorTest {

    @Test
    void removesOutboxEntryAfterProviderConfirmsDeletion() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        KeycloakAdminClient admin = mock(KeycloakAdminClient.class);
        KeycloakAccountDeletionProcessor processor = new KeycloakAccountDeletionProcessor(jdbc, admin);
        UUID identity = UUID.randomUUID();

        processor.processOne(identity);

        verify(admin).deleteUser(identity);
        verify(jdbc).update("DELETE FROM account_deletion_outbox WHERE keycloak_id = ?", identity);
    }

    @Test
    void retainsOutboxEntryAndSchedulesBackoffWhenProviderIsUnavailable() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        KeycloakAdminClient admin = mock(KeycloakAdminClient.class);
        KeycloakAccountDeletionProcessor processor = new KeycloakAccountDeletionProcessor(jdbc, admin);
        UUID identity = UUID.randomUUID();
        doThrow(new AuthIntegrationException("unavailable")).when(admin).deleteUser(identity);

        processor.processOne(identity);

        verify(jdbc).update(contains("UPDATE account_deletion_outbox"), eq(identity));
    }
}
