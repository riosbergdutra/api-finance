package com.api.finance.user;

import com.api.finance.user.exception.UserIdentityDeletedException;
import com.api.finance.user.infrastructure.UserDirectoryAdapter;
import com.api.finance.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserDirectoryAdapterTombstoneTest {

    @Test
    void deletedIdentityCannotBeSilentlyRegisteredAgain() {
        UserRepository users = mock(UserRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), anyString())).thenReturn(true);
        UserDirectoryAdapter adapter = new UserDirectoryAdapter(users, jdbc);

        assertThrows(UserIdentityDeletedException.class,
                () -> adapter.registerIfAbsent(UUID.randomUUID(), "deleted@example.test", "Deleted"));

        verify(users, never()).existsByKeycloakId(org.mockito.ArgumentMatchers.any(UUID.class));
        verify(users, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
