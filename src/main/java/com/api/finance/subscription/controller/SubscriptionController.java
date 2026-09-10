package com.api.finance.subscription.controller;

import com.api.finance.config.AuthenticatedUser;
import com.api.finance.config.AuthenticatedUserProvider;
import com.api.finance.subscription.dto.SubscriptionResponse;
import com.api.finance.subscription.service.StripeService;
import com.api.finance.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
@Slf4j
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final StripeService stripeService;
    private final AuthenticatedUserProvider userProvider;

    @GetMapping("/me")
    public ResponseEntity<SubscriptionResponse> getMeuPlano(
            @AuthenticationPrincipal Jwt jwt) {

        AuthenticatedUser caller = userProvider.get(jwt);
        return ResponseEntity.ok(subscriptionService.getMeuPlano(caller));
    }

    /**
     * Inicia o fluxo de upgrade para PRO via Stripe Checkout Session.
     *
     * Retorna { "url": "https://checkout.stripe.com/c/pay/cs_test_..." }
     * e o frontend apenas faz window.location.href = res.url.
     *
     * Após o pagamento:
     * - Stripe redireciona o usuário para /app/subscription?success=true
     * - Stripe dispara webhook customer.subscription.updated
     * - StripeWebhookService ativa o PRO no banco
     */
    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> iniciarCheckout(
            @AuthenticationPrincipal Jwt jwt) {

        AuthenticatedUser caller = userProvider.get(jwt);

        log.info("[Subscription] Iniciando checkout para userId={}", caller.id());

        try {
            String url = stripeService.criarCheckoutSession(caller.id());
            return ResponseEntity.ok(new CheckoutResponse(url));

        } catch (Exception e) {
            log.error("[Subscription] Erro ao iniciar checkout", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/cancel")
    public ResponseEntity<Void> cancelarSubscription(
            @AuthenticationPrincipal Jwt jwt) {

        AuthenticatedUser caller = userProvider.get(jwt);

        log.info("[Subscription] Cancelando assinatura para userId={}", caller.id());

        try {
            subscriptionService.cancelarSubscricao(caller.id());
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("[Subscription] Erro ao cancelar assinatura", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // FIX: retorna "url" (Checkout Session) em vez de "clientSecret" (PaymentIntent)
    // O frontend recebe essa URL e faz window.location.href = res.url — sem precisar
    // de @stripe/stripe-js nem de formulário de cartão embutido.
    public record CheckoutResponse(String url) {}
}
