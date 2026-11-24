-- Guarda a ordem dos eventos Stripe aplicados a cada assinatura recorrente.
-- A comparação usa Event.created (Unix seconds) sob lock pessimista da linha local.
ALTER TABLE subscription
    ADD COLUMN stripe_last_event_created BIGINT,
    ADD COLUMN stripe_last_event_id VARCHAR(255);
