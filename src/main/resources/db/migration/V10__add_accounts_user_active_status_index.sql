-- Corrige a cobertura do índice de contas ativas sem reescrever a migration V3.
-- O índice legado idx_accounts_user_active pode existir apenas em (user_id);
-- este índice composto atende consultas filtradas também por is_active.
CREATE INDEX IF NOT EXISTS idx_accounts_user_active_status
    ON accounts (user_id, is_active);
