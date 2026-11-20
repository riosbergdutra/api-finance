package com.api.finance.notification.websocket;

import com.api.finance.bff.event.BffSessionRevokedEvent;
import com.api.finance.bff.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationWebSocketHandlerTest {

    @Mock
    private AuthService authService;

    private NotificationWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new NotificationWebSocketHandler(authService);
    }

    @Test
    void deveFecharWebSocketQuandoSessaoBffForRevogada() throws Exception {
        WebSocketSession webSocket = mock(WebSocketSession.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Cookie", "SESSION_ID=opaque-session-id");
        Map<String, Object> attributes = new HashMap<>();
        when(webSocket.getHandshakeHeaders()).thenReturn(headers);
        when(webSocket.getAttributes()).thenReturn(attributes);
        when(webSocket.isOpen()).thenReturn(true);
        when(webSocket.getId()).thenReturn("websocket-test-id");

        Jwt jwt = Jwt.withTokenValue("test-access-token")
                .header("alg", "none")
                .subject("user-123")
                .build();
        when(authService.autenticarSessao("opaque-session-id")).thenReturn(jwt);

        handler.afterConnectionEstablished(webSocket);

        assertEquals(1, handler.totalSessoesAtivas());
        assertEquals("opaque-session-id", attributes.get("bffSessionId"));

        handler.onBffSessionRevoked(new BffSessionRevokedEvent("opaque-session-id"));

        verify(webSocket).close(CloseStatus.POLICY_VIOLATION);
        assertEquals(0, handler.totalSessoesAtivas());
    }
}
