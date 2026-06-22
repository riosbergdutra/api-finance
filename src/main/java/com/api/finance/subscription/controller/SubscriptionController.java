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

        return ResponseEntity.ok(
                subscriptionService.getMeuPlano(caller)
        );
    }

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> iniciarCheckout(
            @AuthenticationPrincipal Jwt jwt) {

        AuthenticatedUser caller = userProvider.get(jwt);

        log.info(
                "[Subscription] Iniciando checkout para userId={}",
                caller.id()
        );

        try {
            String clientSecret =
                    stripeService.criarPaymentIntent(caller.id());

            return ResponseEntity.ok(
                    new CheckoutResponse(clientSecret)
            );

        } catch (Exception e) {
            log.error(
                    "[Subscription] Erro ao iniciar checkout",
                    e
            );

            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/cancel")
    public ResponseEntity<Void> cancelarSubscription(
            @AuthenticationPrincipal Jwt jwt) {

        AuthenticatedUser caller = userProvider.get(jwt);

        log.info(
                "[Subscription] Cancelando assinatura para userId={}",
                caller.id()
        );

        try {
            subscriptionService.cancelarSubscricao(
                    caller.id()
            );

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error(
                    "[Subscription] Erro ao cancelar assinatura",
                    e
            );

            return ResponseEntity.internalServerError().build();
        }
    }

    public record CheckoutResponse(String clientSecret) {}
}