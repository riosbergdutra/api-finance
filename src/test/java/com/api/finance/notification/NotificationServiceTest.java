package com.api.finance.notification;

import com.api.finance.config.AuthenticatedUser;
import com.api.finance.notification.dto.NotificationResponse;
import com.api.finance.notification.model.Notification;
import com.api.finance.notification.model.NotificationType;
import com.api.finance.notification.repository.NotificationRepository;
import com.api.finance.notification.service.NotificationService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.api.finance.shared.TestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService")
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock UserRepository userRepository;
    @InjectMocks NotificationService notificationService;

    AuthenticatedUser caller;
    Notification notification;

    @BeforeEach
    void setUp() {
        caller = caller();
        notification = notification();
        given(userRepository.findIdByKeycloakId(KEYCLOAK_ID)).willReturn(Optional.of(USER_ID));
    }

    // ─── listar ──────────────────────────────────────────────────────

    @Test
    @DisplayName("listar: retorna page de notificações")
    void listarRetornaPage() {
        Page<Notification> page = new PageImpl<>(List.of(notification));
        given(notificationRepository.findByUserIdOrderByLidaAscCriadoEmDesc(eq(USER_ID), any(Pageable.class)))
                .willReturn(page);

        Page<NotificationResponse> result = notificationService.listar(Pageable.unpaged(), caller);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(NOTIFICATION_ID);
    }

    // ─── listarNaoLidas ──────────────────────────────────────────────

    @Test
    @DisplayName("listarNaoLidas: retorna somente não lidas")
    void listarNaoLidas() {
        given(notificationRepository.findByUserIdAndLidaFalseOrderByCriadoEmDesc(USER_ID))
                .willReturn(List.of(notification));

        List<NotificationResponse> result = notificationService.listarNaoLidas(caller);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).lida()).isFalse();
    }

    // ─── contarNaoLidas ──────────────────────────────────────────────

    @Test
    @DisplayName("contarNaoLidas: retorna contagem correta")
    void contarNaoLidas() {
        given(notificationRepository.countByUserIdAndLidaFalse(USER_ID)).willReturn(5L);

        long total = notificationService.contarNaoLidas(caller);

        assertThat(total).isEqualTo(5L);
    }

    // ─── marcarComoLida ──────────────────────────────────────────────

    @Nested @DisplayName("marcarComoLida")
    class MarcarComoLida {

        @Test
        @DisplayName("marca notificação como lida com sucesso")
        void marcaComoLida() {
            given(notificationRepository.findByIdAndUserId(NOTIFICATION_ID, USER_ID))
                    .willReturn(Optional.of(notification));
            given(notificationRepository.save(any())).willReturn(notification);

            notificationService.marcarComoLida(NOTIFICATION_ID, caller);

            assertThat(notification.isLida()).isTrue();
            then(notificationRepository).should().save(notification);
        }

        @Test
        @DisplayName("lança ResourceNotFoundException quando notificação não encontrada")
        void lancaNotFound() {
            given(notificationRepository.findByIdAndUserId(any(), eq(USER_ID))).willReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.marcarComoLida(UUID.randomUUID(), caller))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ─── marcarTodasComoLidas ────────────────────────────────────────

    @Test
    @DisplayName("marcarTodasComoLidas: retorna quantidade marcada")
    void marcarTodasComoLidas() {
        given(notificationRepository.marcarTodasComoLidas(USER_ID)).willReturn(3);

        int total = notificationService.marcarTodasComoLidas(caller);

        assertThat(total).isEqualTo(3);
    }

    // ─── criarNotificacao ────────────────────────────────────────────

    @Nested @DisplayName("criarNotificacao")
    class CriarNotificacao {

        @Test
        @DisplayName("salva notificação com entidade relacionada")
        void criaComEntidade() {
            notificationService.criarNotificacao(
                    USER_ID, NotificationType.ORCAMENTO_ALERTA,
                    "Alerta", "Mensagem", "BUDGET", BUDGET_ID);

            then(notificationRepository).should().save(argThat(n ->
                    n.getUserId().equals(USER_ID)
                    && n.getTipo() == NotificationType.ORCAMENTO_ALERTA
                    && "BUDGET".equals(n.getEntidadeTipo())
                    && BUDGET_ID.equals(n.getEntidadeId())
            ));
        }

        @Test
        @DisplayName("salva notificação sem entidade relacionada")
        void criaSemEntidade() {
            notificationService.criarNotificacao(
                    USER_ID, NotificationType.META_CONCLUIDA, "Meta!", "Parabéns");

            then(notificationRepository).should().save(argThat(n ->
                    n.getEntidadeTipo() == null && n.getEntidadeId() == null
            ));
        }
    }
}
