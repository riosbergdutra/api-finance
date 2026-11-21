-- Índices compostos para consultas recorrentes por usuário e período.
-- A migração não remove nem altera registros existentes.

-- Um orçamento geral (category_id IS NULL) por usuário/mês/ano.
-- PostgreSQL permite múltiplos NULL em UNIQUE composto, então a constraint
-- declarada em V1 não impede duplicidade de orçamentos gerais.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM budgets
        WHERE category_id IS NULL
        GROUP BY user_id, mes, ano
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Existem orçamentos gerais duplicados (category_id NULL) para o mesmo usuário/mês/ano. Corrija os registros antes de aplicar V3.';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS idx_budget_general_unique
    ON budgets (user_id, mes, ano)
    WHERE category_id IS NULL;

-- Lista, soma e paginação de transações por usuário e intervalo de datas.
CREATE INDEX IF NOT EXISTS idx_transactions_user_date
    ON transactions (user_id, data);

-- Consultas de metas pendentes e prazos por usuário.
CREATE INDEX IF NOT EXISTS idx_goals_user_pending_deadline
    ON goals (user_id, concluida, data_alvo);

-- Busca de assinaturas PRO por período de vencimento.
CREATE INDEX IF NOT EXISTS idx_subscription_plan_expiration
    ON subscription (plano, fim_periodo, status);

-- Contagem e soma de contas ativas por usuário.
CREATE INDEX IF NOT EXISTS idx_accounts_user_active
    ON accounts (user_id, is_active);

-- Busca dos orçamentos de um usuário por mês/ano.
CREATE INDEX IF NOT EXISTS idx_budgets_user_period
    ON budgets (user_id, ano, mes);
