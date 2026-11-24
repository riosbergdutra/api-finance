package com.api.finance.notification.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import com.api.finance.bff.service.AuthService;
import com.api.finance.bff.event.BffSessionRevokedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handler de WebSocket para notificações em tempo real.
 *
 * FLUXO:
 * 1. Browser opens /ws/notifications with its HttpOnly SESSION_ID cookie
 * 2. Handler resolves and validates the server-side BFF session before accepting
 * 3. Sessão WebSocket fica associada ao userId
 * 4. Quando NotificationService cria uma notificação, chama sendToUser()
 * 5. A mensagem é enviada via WebSocket — sem polling, sem reload
 *
 * SEGURANÇA:
 * - Session cookie validated on connection establishment
 * - Session rejected if the cookie is missing, expired, or invalid
 * - userId extraído do "sub" do JWT (keycloakId), nunca do query param diretamente
 *
 * CONCORRÊNCIA:
 * - ConcurrentHashMap para o mapa de sessões (thread-safe com Virtual Threads)
 * - CopyOnWriteArrayList para múltiplas abas do mesmo usuário
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private final AuthService authService;

    /**
     * Mapa: keycloakId → lista de sessões WebSocket ativas.
     * Um usuário pode ter múltiplas abas abertas.
     */
    private final Map<String, List<WebSocketSession>> sessoesPorUsuario = new ConcurrentHashMap<>();
    private final AtomicInteger falhasConsecutivasDeRevalidacao = new AtomicInteger();

    /**
     * Redis Pub/Sub is best-effort. Periodic authoritative checks ensure a lost
     * revocation message cannot leave a remote socket alive indefinitely.
     * After repeated Redis failures this node closes its sockets (fail-closed).
     */
    @Scheduled(fixedDelayString = "${app.bff.websocket.revalidation-interval:60000}")
    public void revalidarSessoesAtivas() {
        Set<String> sessionIds = new HashSet<>();
        sessoesPorUsuario.values().forEach(sessions -> sessions.forEach(session -> {
            Object value = session.getAttributes().get("bffSessionId");
            if (value instanceof String id && !id.isBlank()) {
                sessionIds.add(id);
            }
        }));

        if (sessionIds.isEmpty()) {
            falhasConsecutivasDeRevalidacao.set(0);
            return;
        }

        try {
            for (String sessionId : sessionIds) {
                if (!authService.sessaoAtiva(sessionId)) {
                    closeConnectionsForRevokedSession(sessionId);
                }
            }
            falhasConsecutivasDeRevalidacao.set(0);
        } catch (RuntimeException redisUnavailable) {
            int failures = falhasConsecutivasDeRevalidacao.incrementAndGet();
            log.warn("Falha ao revalidar sessões WebSocket no Redis (tentativa consecutiva {}).", failures);
            if (failures >= 3) {
                closeAllConnections();
                falhasConsecutivasDeRevalidacao.set(0);
            }
        }
    }

    private void closeAllConnections() {
        sessoesPorUsuario.values().forEach(sessions -> sessions.forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.close(CloseStatus.SERVICE_RESTARTED);
                }
            } catch (IOException ignored) {
                // Closing every local socket is best-effort after Redis becomes unavailable.
            }
        }));
        sessoesPorUsuario.clear();
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = extrairSessionId(session);
        if (sessionId == null) {
            recusarConexao(session, "Cookie de sessão ausente");
            return;
        }

        try {
            Jwt jwt = authService.autenticarSessao(sessionId);
            String keycloakId = jwt.getSubject();

            // Associa a sessão ao keycloakId
            sessoesPorUsuario
                    .computeIfAbsent(keycloakId, k -> new CopyOnWriteArrayList<>())
                    .add(session);

            // Guarda o keycloakId na sessão para uso no fechamento
            session.getAttributes().put("keycloakId", keycloakId);
            session.getAttributes().put("bffSessionId", sessionId);

            log.info("[WS] Conexão estabelecida: keycloakId={} sessionId={}", keycloakId, session.getId());

        } catch (RuntimeException e) {
            recusarConexao(session, "Sessão inválida ou expirada");
        }
    }

    /**
     * Closes every WebSocket opened with a BFF session after that session is
     * revoked. This prevents another tab/device from continuing to receive
     * private notifications after logout or account deletion.
     */
    @EventListener
    public void onBffSessionRevoked(BffSessionRevokedEvent event) {
        closeConnectionsForRevokedSession(event.sessionId());
    }

    /** Closes this replica's sockets; invoked for both local and Redis-broadcast revocations. */
    public void closeConnectionsForRevokedSession(String revokedSessionId) {
        sessoesPorUsuario.forEach((keycloakId, sessions) -> {
            // Primeiro identifica e remove as conexões da lista, sem efeitos colaterais
            // dentro do removeIf. O close() pode disparar afterConnectionClosed()
            // sincronamente, que também remove a sessão da mesma CopyOnWriteArrayList.
            List<WebSocketSession> revokedSessions = sessions.stream()
                    .filter(webSocket -> revokedSessionId.equals(
                            webSocket.getAttributes().get("bffSessionId")))
                    .toList();

            if (!revokedSessions.isEmpty()) {
                sessions.removeAll(revokedSessions);

                // Fecha somente depois da remoção. O callback afterConnectionClosed()
                // pode executar novamente a remoção, que será inofensiva.
                for (WebSocketSession webSocket : revokedSessions) {
                    try {
                        if (webSocket.isOpen()) {
                            webSocket.close(CloseStatus.POLICY_VIOLATION);
                        }
                    } catch (IOException e) {
                        log.warn("[WS] Não foi possível fechar conexão após revogação de sessão: {}",
                                webSocket.getId());
                    }
                }
            }

            if (sessions.isEmpty()) {
                sessoesPorUsuario.remove(keycloakId, sessions);
            }
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String keycloakId = (String) session.getAttributes().get("keycloakId");
        if (keycloakId != null) {
            List<WebSocketSession> sessoes = sessoesPorUsuario.get(keycloakId);
            if (sessoes != null) {
                sessoes.remove(session);
                if (sessoes.isEmpty()) {
                    sessoesPorUsuario.remove(keycloakId);
                }
            }
        }
        log.info("[WS] Conexão fechada: sessionId={} status={}", session.getId(), status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Conexão é unidirecional (server → client) — mensagens do client são ignoradas
        log.debug("[WS] Mensagem ignorada do client: {}", session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("[WS] Erro de transporte: sessionId={} erro={}", session.getId(), exception.getMessage());
        afterConnectionClosed(session, CloseStatus.SERVER_ERROR);
    }

    // ── API pública — chamada pelo NotificationService ────────────────────

    /**
     * Envia uma notificação em tempo real para todas as sessões abertas do usuário.
     * Chamado de forma @Async pelo NotificationService — não bloqueia o fluxo principal.
     *
     * @param keycloakId  ID do Keycloak do destinatário (string, não UUID)
     * @param payload     JSON da notificação (serializado pelo NotificationService)
     */
    public void sendToUser(String keycloakId, String payload) {
        List<WebSocketSession> sessoes = sessoesPorUsuario.get(keycloakId);
        if (sessoes == null || sessoes.isEmpty()) {
            log.debug("[WS] Nenhuma sessão ativa para keycloakId={} — notificação só persistida", keycloakId);
            return;
        }

        TextMessage message = new TextMessage(payload);
        sessoes.removeIf(session -> {
            if (!session.isOpen()) return true; // remove sessões mortas
            try {
                session.sendMessage(message);
                return false;
            } catch (IOException e) {
                log.warn("[WS] Falha ao enviar para sessionId={}: {}", session.getId(), e.getMessage());
                return true; // remove sessão com erro
            }
        });
    }

    /**
     * Retorna quantas sessões WebSocket estão abertas (para métricas/actuator).
     */
    public int totalSessoesAtivas() {
        return sessoesPorUsuario.values().stream().mapToInt(List::size).sum();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Reads the opaque HttpOnly session cookie from the WebSocket handshake.
     * OAuth access and refresh tokens are never placed in the URL.
     */
    private String extrairSessionId(WebSocketSession session) {
        String cookieHeader = session.getHandshakeHeaders().getFirst("Cookie");
        if (cookieHeader == null || cookieHeader.isBlank()) {
            return null;
        }

        String found = null;
        for (String cookie : cookieHeader.split(";")) {
            String[] pair = cookie.trim().split("=", 2);
            if (pair.length == 2 && "SESSION_ID".equals(pair[0])) {
                if (found != null || pair[1].length() != 36) {
                    return null;
                }
                try {
                    java.util.UUID.fromString(pair[1]);
                } catch (IllegalArgumentException malformed) {
                    return null;
                }
                found = pair[1];
            }
        }
        return found;
    }

    private void recusarConexao(WebSocketSession session, String motivo) {
        log.warn("[WS] Conexão recusada: {} — sessionId={}", motivo, session.getId());
        try {
            session.close(CloseStatus.NOT_ACCEPTABLE);
        } catch (IOException e) {
            log.error("[WS] Erro ao fechar sessão recusada", e);
        }
    }
}