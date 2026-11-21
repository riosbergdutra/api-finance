package com.api.finance.user;

import com.api.finance.user.infrastructure.UserDirectoryAdapter;
import com.api.finance.user.model.User;
import com.api.finance.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserDirectoryAdapterDeletionTest {

    @Test
    void removesEveryUserOwnedTableBeforeDeletingTheIdentityRow() {
        UUID keycloakId = UUID.randomUUID();
        UUID internalId = UUID.randomUUID();
        User user = new User();
        user.setId(internalId);
        user.setKeycloakId(keycloakId);

        UserRepository users = mock(UserRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(users.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

        new UserDirectoryAdapter(users, jdbc).deleteByIdentityProviderId(keycloakId);

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO deleted_user_identities"),
                org.mockito.ArgumentMatchers.anyString());
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO account_deletion_outbox"),
                eq(keycloakId));

        var order = inOrder(jdbc, users);
        order.verify(jdbc).update("DELETE FROM transactions WHERE user_id = ?", internalId);
        order.verify(jdbc).update("DELETE FROM budgets WHERE user_id = ?", internalId);
        order.verify(jdbc).update(org.mockito.ArgumentMatchers.contains("DELETE FROM merchant_rules"),
                eq(internalId), eq(internalId));
        order.verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE categories SET parent_id = NULL"),
                eq(internalId));
        order.verify(jdbc).update("DELETE FROM categories WHERE user_id = ?", internalId);
        order.verify(jdbc).update("DELETE FROM goals WHERE user_id = ?", internalId);
        order.verify(jdbc).update("DELETE FROM notifications WHERE user_id = ?", internalId);
        order.verify(jdbc).update("DELETE FROM subscription WHERE user_id = ?", internalId);
        order.verify(jdbc).update("DELETE FROM accounts WHERE user_id = ?", internalId);
        order.verify(users).delete(user);
    }

    @Test
    void isIdempotentWhenTheLocalIdentityDoesNotExist() {
        UUID keycloakId = UUID.randomUUID();
        UserRepository users = mock(UserRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(users.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());

        new UserDirectoryAdapter(users, jdbc).deleteByIdentityProviderId(keycloakId);

        verify(jdbc, org.mockito.Mockito.never()).update(
                org.mockito.ArgumentMatchers.contains("DELETE FROM transactions"),
                org.mockito.ArgumentMatchers.<Object[]>any());
        verify(users, org.mockito.Mockito.never()).delete(org.mockito.ArgumentMatchers.any(User.class));
    }
}
