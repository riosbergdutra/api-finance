package com.api.finance.notification.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/**
 * Handler de WebSocket para notificações em tempo real.
 *
 * FLUXO:
 * 1. Angular abre conexão: ws://backend/ws/notifications?token=<access_token>
 * 2. Handler valida o JWT do query param antes de aceitar
 * 3. Sessão WebSocket fica associada ao userId
 * 4. Quando NotificationService cria uma notificação, chama sendToUser()
 * 5. A mensagem é enviada via WebSocket — sem polling, sem reload
 *
 * SEGURANÇA:
 * - Token validado no afterConnectionEstablished (antes de aceitar qualquer mensagem)
 * - Sessão recusada se token inválido ou ausente
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

    private final JwtDecoder jwtDecoder;

    /**
     * Mapa: keycloakId → lista de sessões WebSocket ativas.
     * Um usuário pode ter múltiplas abas abertas.
     */
    private final Map<String, List<WebSocketSession>> sessoesPorUsuario = new ConcurrentHashMap<>();

    // ── Lifecycle ────────────────────────────────────────────────────────

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String token = extrairToken(session);
        if (token == null) {
            recusarConexao(session, "Token ausente");
            return;
        }

        try {
            Jwt jwt = jwtDecoder.decode(token);
            String keycloakId = jwt.getSubject();

            // Associa a sessão ao keycloakId
            sessoesPorUsuario
                    .computeIfAbsent(keycloakId, k -> new CopyOnWriteArrayList<>())
                    .add(session);

            // Guarda o keycloakId na sessão para uso no fechamento
            session.getAttributes().put("keycloakId", keycloakId);

            log.info("[WS] Conexão estabelecida: keycloakId={} sessionId={}", keycloakId, session.getId());

        } catch (JwtException e) {
            recusarConexao(session, "Token inválido: " + e.getMessage());
        }
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
     * Extrai o token do query param: /ws/notifications?token=xxx
     * Não usa Authorization header porque browsers não enviam headers customizados
     * na abertura de conexões WebSocket.
     */
    private String extrairToken(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) return null;
        String query = uri.getQuery();
        if (query == null) return null;

        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && "token".equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
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