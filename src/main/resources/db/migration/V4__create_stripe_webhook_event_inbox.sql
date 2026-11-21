-- Inbox durável para deduplicar entregas do Stripe.
-- O evento e a alteração de assinatura são gravados na mesma transação.
CREATE TABLE stripe_webhook_events (
    event_id VARCHAR(255) PRIMARY KEY,
    event_type VARCHAR(255) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_stripe_webhook_events_received_at
    ON stripe_webhook_events (received_at);
