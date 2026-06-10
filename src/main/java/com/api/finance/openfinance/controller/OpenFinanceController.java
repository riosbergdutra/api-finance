package com.api.finance.openfinance.controller;

import com.api.finance.config.AuthenticatedUser;
import com.api.finance.config.AuthenticatedUserProvider;
import com.api.finance.openfinance.service.OpenFinanceService;
import com.api.finance.openfinance.dto.OpenFinanceConsentResponse;
import com.api.finance.openfinance.dto.OpenFinanceSyncResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Open Finance — importação de dados bancários externos.
 * FLUXO OPEN FINANCE (Brasil):
 * 1. Usuário autoriza no banco de origem (consentimento via redirect)
 * 2. Banco retorna authorization code para o nosso callback
 * 3. Trocamos o code por um access token (API do banco)
 * 4. Importamos transações e saldos com o token

 * ESTE CONTROLLER É UM STUB FUNCIONAL.
 * A integração real depende de qual provedor/agregador você vai usar:
 *   - Belvo (recomendado para MVP — cobre bancos BR/LATAM)
 *   - Pluggy (alternativa BR)
 *   - Implementação direta via Open Banking BR (mais complexa)

 * Os endpoints já estão definidos e protegidos (PRO only).
 * Substitua os métodos do OpenFinanceService pela integração real.

 * REQUER PLANO PRO: verificado via SubscriptionService.assertIsPro()
 */
@RestController
@RequestMapping("/open-finance")
@RequiredArgsConstructor
public class OpenFinanceController {

    private final OpenFinanceService openFinanceService;
    private final AuthenticatedUserProvider userProvider;

    /**
     * Inicia o fluxo de consentimento.
     * Retorna a URL para redirecionar o usuário ao banco de origem.
     */
    @PostMapping("/consent")
    public ResponseEntity<OpenFinanceConsentResponse> iniciarConsentimento(
            @RequestParam String bankId,
            @AuthenticationPrincipal Jwt jwt) {

        AuthenticatedUser caller = userProvider.get(jwt);
        return ResponseEntity.ok(openFinanceService.iniciarConsentimento(bankId, caller));
    }

    /**
     * Callback após autorização no banco de origem.
     * O banco redireciona para este endpoint com o authorization code.
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam String code,
            @RequestParam String state) {

        openFinanceService.processarCallback(code, state);
        // Redirecionar para o frontend após processar
        return ResponseEntity.ok().build();
    }

    /**
     * Importa manualmente transações do banco conectado.
     * Usado quando o usuário quer sincronizar agora (sem esperar o scheduler).
     */
    @PostMapping("/sync")
    public ResponseEntity<OpenFinanceSyncResponse> sincronizar(
            @RequestParam String connectionId,
            @AuthenticationPrincipal Jwt jwt) {

        AuthenticatedUser caller = userProvider.get(jwt);
        return ResponseEntity.ok(openFinanceService.sincronizar(connectionId, caller));
    }

    /**
     * Lista bancos conectados pelo usuário.
     */
    @GetMapping("/connections")
    public ResponseEntity<?> listarConexoes(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser caller = userProvider.get(jwt);
        return ResponseEntity.ok(openFinanceService.listarConexoes(caller));
    }

    /**
     * Remove uma conexão com banco externo.
     */
    @DeleteMapping("/connections/{connectionId}")
    public ResponseEntity<Void> removerConexao(
            @PathVariable String connectionId,
            @AuthenticationPrincipal Jwt jwt) {

        AuthenticatedUser caller = userProvider.get(jwt);
        openFinanceService.removerConexao(connectionId, caller);
        return ResponseEntity.noContent().build();
    }
}
