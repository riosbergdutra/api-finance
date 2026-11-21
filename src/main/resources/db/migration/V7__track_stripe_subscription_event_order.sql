-- Guarda a ordem dos eventos Stripe aplicados a cada assinatura recorrente.
-- A comparação usa Event.created (Unix seconds) sob lock pessimista da linha local.
ALTER TABLE subscription
    ADD COLUMN stripe_last_event_created BIGINT,
    ADD COLUMN stripe_last_event_id VARCHAR(255);

CREATE INDEX idx_subscription_stripe_last_event_created
    ON subscription (stripe_last_event_created)
    WHERE stripe_last_event_created IS NOT NULL;
