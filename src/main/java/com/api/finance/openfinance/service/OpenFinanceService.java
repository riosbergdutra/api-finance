package com.api.finance.openfinance.service;

import com.api.finance.config.AuthenticatedUser;
import com.api.finance.openfinance.dto.OpenFinanceConsentResponse;
import com.api.finance.openfinance.dto.OpenFinanceSyncResponse;
import com.api.finance.shared.exception.ResourceNotFoundException;
import com.api.finance.subscription.service.SubscriptionService;
import com.api.finance.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Open Finance — stub de implementação.
 *
 * Este service define a interface que a integração real deve implementar.
 * Cada método lança UnsupportedOperationException com instruções sobre
 * qual integração usar e como implementar.
 *
 * ════════════════════════════════════════════════════════════
 * PRÓXIMO PASSO: integrar com Belvo ou Pluggy
 * ════════════════════════════════════════════════════════════
 *
 * BELVO (https://belvo.com):
 *   - Documentação: https://developers.belvo.com
 *   - SDK Java não oficial disponível; use RestClient diretamente
 *   - Endpoints relevantes:
 *     POST /api/links/        → cria conexão com banco (retorna link_id)
 *     GET  /api/transactions/ → importa transações do link
 *     GET  /api/accounts/     → importa saldos e contas
 *
 * PLUGGY (https://pluggy.ai):
 *   - Documentação: https://docs.pluggy.ai
 *   - SDK Java: https://github.com/pluggyai/pluggy-java
 *   - Endpoints relevantes:
 *     POST /connect-token         → token para o widget
 *     GET  /items/{itemId}        → conexão criada
 *     GET  /transactions          → transações
 *
 * ABORDAGEM RECOMENDADA PARA MVP:
 *   1. Escolha Belvo ou Pluggy
 *   2. Implemente BelvoClient (ou PluggyClient) com RestClient
 *   3. Substitua os throws neste service pelas chamadas ao client
 *   4. Mapeie os dados externos para os modelos Transaction e Account internos
 *   5. Use o TransactionService.criar() para persistir (mantém deduplicação e saldo)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OpenFinanceService {

    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;

    public OpenFinanceConsentResponse iniciarConsentimento(String bankId, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);
        subscriptionService.assertIsPro(userId, "Open Finance");

        // TODO: implementar com Belvo/Pluggy
        // 1. Chamar API do agregador para gerar URL de consentimento para o bankId
        // 2. Armazenar o state/session para validar no callback
        // 3. Retornar a URL para o frontend redirecionar

        log.info("[OpenFinance] Consentimento solicitado — userId={} bankId={}", userId, bankId);
        throw new UnsupportedOperationException(
            "Open Finance não implementado. Integre com Belvo (belvo.com) ou Pluggy (pluggy.ai). " +
            "Veja os comentários em OpenFinanceService para o passo a passo.");
    }

    public void processarCallback(String code, String state) {
        // TODO: implementar com Belvo/Pluggy
        // 1. Validar o state (evita CSRF no callback)
        // 2. Trocar o code por access token na API do banco via agregador
        // 3. Armazenar a conexão (link_id / item_id) vinculada ao userId
        throw new UnsupportedOperationException("Open Finance não implementado.");
    }

    public OpenFinanceSyncResponse sincronizar(String connectionId, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);
        subscriptionService.assertIsPro(userId, "Open Finance");

        // TODO: implementar com Belvo/Pluggy
        // 1. Buscar transações da conexão no agregador
        // 2. Mapear para CreateTransactionRequest
        // 3. Chamar TransactionService.criar() para cada transação
        //    (deduplicação por hash já previne duplicatas)
        // 4. Retornar resumo: quantas importadas, quantas ignoradas (duplicatas)

        throw new UnsupportedOperationException("Open Finance não implementado.");
    }

    public List<?> listarConexoes(AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);
        subscriptionService.assertIsPro(userId, "Open Finance");

        // TODO: buscar conexões ativas do usuário (tabela open_finance_connections a criar)
        return List.of();
    }

    public void removerConexao(String connectionId, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);
        subscriptionService.assertIsPro(userId, "Open Finance");

        // TODO: revogar token no agregador e remover da tabela local
        throw new UnsupportedOperationException("Open Finance não implementado.");
    }

    private UUID resolveUserId(AuthenticatedUser caller) {
        return userRepository.findIdByKeycloakId(caller.id())
                .orElseThrow(() -> ResourceNotFoundException.of("User", caller.id()));
    }
}
