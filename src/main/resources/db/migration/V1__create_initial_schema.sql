-- Schema inicial do FinanceFlow, alinhado às entidades JPA atuais.
-- Não adiciona FKs para user_id/account_id que hoje são UUIDs escalares nas entidades.

CREATE TABLE users (
    id UUID PRIMARY KEY,
    keycloak_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(255),
    locale VARCHAR(5) NOT NULL,
    is_active BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_users_keycloak_id UNIQUE (keycloak_id),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(30) NOT NULL,
    balance NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    color VARCHAR(7),
    icon VARCHAR(50),
    is_active BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_accounts_user_id ON accounts (user_id);

CREATE TABLE categories (
    id UUID PRIMARY KEY,
    user_id UUID,
    nome VARCHAR(80) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    icone VARCHAR(50),
    cor VARCHAR(7),
    parent_id UUID,
    sistema BOOLEAN NOT NULL,
    ativa BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id)
);
CREATE INDEX idx_cat_user_id ON categories (user_id);
CREATE INDEX idx_cat_sistema ON categories (sistema);

CREATE TABLE merchant_rules (
    id UUID PRIMARY KEY,
    user_id UUID,
    padrao_nome VARCHAR(100) NOT NULL,
    category_id UUID NOT NULL,
    prioridade INTEGER NOT NULL,
    CONSTRAINT fk_merchant_rules_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT uk_merchant_rules_user_pattern UNIQUE (user_id, padrao_nome)
);
CREATE INDEX idx_mr_user ON merchant_rules (user_id);

CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    account_id UUID NOT NULL,
    conta_destino_id UUID,
    category_id UUID,
    tipo VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    valor NUMERIC(19, 2) NOT NULL,
    descricao VARCHAR(200),
    estabelecimento VARCHAR(100),
    data DATE NOT NULL,
    hash_deduplicacao VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_transactions_category FOREIGN KEY (category_id) REFERENCES categories (id)
);
CREATE INDEX idx_trx_user_id ON transactions (user_id);
CREATE INDEX idx_trx_conta ON transactions (account_id);
CREATE INDEX idx_trx_data ON transactions (data);

CREATE TABLE budgets (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    category_id UUID,
    valor_limite NUMERIC(19, 2) NOT NULL,
    valor_gasto NUMERIC(19, 2) NOT NULL,
    mes INTEGER NOT NULL,
    ano INTEGER NOT NULL,
    alerta_em INTEGER,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_budgets_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT uk_budget_user_cat_mes_ano UNIQUE (user_id, category_id, mes, ano)
);
CREATE INDEX idx_budget_user ON budgets (user_id);

CREATE TABLE goals (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    nome VARCHAR(100) NOT NULL,
    valor_alvo NUMERIC(19, 2) NOT NULL,
    valor_atual NUMERIC(19, 2) NOT NULL,
    data_alvo DATE,
    concluida BOOLEAN NOT NULL,
    cor VARCHAR(7),
    icone VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_goal_user ON goals (user_id);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    tipo VARCHAR(40) NOT NULL,
    titulo VARCHAR(100) NOT NULL,
    mensagem VARCHAR(500) NOT NULL,
    lida BOOLEAN NOT NULL,
    entidade_tipo VARCHAR(30),
    entidade_id UUID,
    created_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_notif_user_lida ON notifications (user_id, lida, created_at);

CREATE TABLE subscription (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    plano VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    inicio_periodo DATE,
    fim_periodo DATE,
    stripe_payment_intent_id VARCHAR(255),
    stripe_subscription_id VARCHAR(255),
    CONSTRAINT uk_subscription_user_id UNIQUE (user_id)
);
