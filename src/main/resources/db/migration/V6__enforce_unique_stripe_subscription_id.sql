-- Uma subscription do Stripe deve apontar para no máximo um usuário local.
-- Não escolhe automaticamente um registro em caso de legado duplicado: falha
-- para que a reconciliação seja feita com os IDs reais do provedor.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM subscription
        WHERE stripe_subscription_id IS NOT NULL
        GROUP BY stripe_subscription_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Existem stripe_subscription_id duplicados em subscription; reconcilie-os antes de aplicar V6.';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS idx_subscription_stripe_subscription_unique
    ON subscription (stripe_subscription_id)
    WHERE stripe_subscription_id IS NOT NULL;
