package com.api.finance.subscription.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Subscription;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.SubscriptionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Serviço para INICIAR pagamentos e assinaturas no Stripe.
 *
 * Este serviço cuida da parte PRÉ-PAGAMENTO:
 * - Criar um PaymentIntent (pagamento avulso)
 * - Criar uma Customer (para rastrear o usuário)
 * - Criar uma Subscription (assinatura recorrente)
 *
 * Após o pagamento ser bem-sucedido, os webhooks (StripeWebhookService)
 * ativarão o PRO no seu banco de dados.
 *
 * IMPORTANTE:
 * 1. Configure ${stripe.api-key} em application.yaml
 * 2. O Stripe é inicializado no construtor automaticamente
 *
 * FLUXO:
 * 1. Frontend chama POST /subscriptions/checkout
 * 2. Este service cria um PaymentIntent ou Subscription
 * 3. Retorna o clientSecret ou paymentUrl ao frontend
 * 4. Frontend redireciona para o checkout do Stripe
 * 5. Usuário paga
 * 6. Stripe dispara webhook para /webhooks/stripe
 * 7. StripeWebhookService ativa o PRO no banco
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripePaymentInitiationService {

    @Value("${stripe.api-key}")
    private String apiKey;

    private static final long PRECO_PRO_CENTAVOS = 1990; // R$ 19,90 = 1990 centavos

    /**
     * Cria um PaymentIntent para pagamento avulso (não-recorrente).
     *
     * Retorna o clientSecret que o frontend usa para exibir o Stripe Payment Element.
     */
    public PaymentIntentResponse criarPagamentoAvulso(UUID userId) throws StripeException {
        Stripe.apiKey = apiKey;

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                        .setAmount(PRECO_PRO_CENTAVOS)
                        .setCurrency("brl")
                        .putMetadata("user_id", userId.toString())
                        .build();

        PaymentIntent intent = PaymentIntent.create(params);
        log.info("[Stripe] PaymentIntent criado: id={} clientSecret={}", intent.getId(), intent.getClientSecret());

        return new PaymentIntentResponse(intent.getId(), intent.getClientSecret());
    }

    /**
     * Cria uma Subscription para pagamento recorrente (mensal).
     *
     * Fluxo:
     * 1. Cria um Customer (representa o usuário no Stripe)
     * 2. Cria uma Subscription para aquele Customer
     * 3. Retorna o URL de confirmação para o frontend
     */
    public SubscriptionResponse criarAssinatura(UUID userId, String email) throws StripeException {
        Stripe.apiKey = apiKey;

        // 1. Cria ou recupera um Customer
        Customer customer = obterOuCriarCustomer(userId, email);

        // 2. Cria a Subscription
        SubscriptionCreateParams params = SubscriptionCreateParams.builder()
                .setCustomer(customer.getId())
                .addItem(
                        SubscriptionCreateParams.Item.builder()
                                .setPrice("price_1234567890") // Você precisa configurar este price_id no Stripe dashboard
                                .build()
                )
                .build();

        Subscription subscription = Subscription.create(params);
        log.info("[Stripe] Subscription criada: id={} customer={}", subscription.getId(), customer.getId());

        return new SubscriptionResponse(subscription.getId(), customer.getId());
    }

    /**
     * Obtém ou cria um Customer Stripe para um usuário.
     *
     * O Stripe não consegue buscar por um ID customizado, então usamos a lista com filtro.
     * Alternativa: armazenar o stripe_customer_id no seu banco de dados.
     */
    private Customer obterOuCriarCustomer(UUID userId, String email) throws StripeException {
        // Para simplificar, sempre cria um novo customer
        // Idealmente, você deveria armazenar o stripe_customer_id na tabela User do seu banco
        
        CustomerCreateParams params = CustomerCreateParams.builder()
                .setEmail(email)
                .putMetadata("user_id", userId.toString())
                .build();

        return Customer.create(params);
    }

    // DTOs para resposta

    public record PaymentIntentResponse(
            String paymentIntentId,
            String clientSecret
    ) {}

    public record SubscriptionResponse(
            String subscriptionId,
            String customerId
    ) {}
}
