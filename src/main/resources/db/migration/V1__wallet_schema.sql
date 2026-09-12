CREATE TABLE users (
    id UUID PRIMARY KEY,
    external_id VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE api_tokens (
    id UUID PRIMARY KEY,
    token_hash CHAR(64) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(id),
    role VARCHAR(16) NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_api_tokens_active ON api_tokens(token_hash) WHERE active = TRUE;

CREATE TABLE wallets (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id),
    balance_paise BIGINT NOT NULL CHECK (balance_paise >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE transfers (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    actor_user_id UUID NOT NULL REFERENCES users(id),
    transfer_type VARCHAR(16) NOT NULL CHECK (transfer_type IN ('P2P', 'FUNDING')),
    from_wallet_id UUID NOT NULL REFERENCES wallets(id),
    to_wallet_id UUID NOT NULL REFERENCES wallets(id),
    amount_paise BIGINT NOT NULL CHECK (amount_paise > 0),
    request_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'DECLINED')),
    decline_reason VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_transfer_distinct_wallets CHECK (from_wallet_id <> to_wallet_id),
    CONSTRAINT chk_decline_reason CHECK (
        (status IN ('PENDING', 'COMPLETED') AND decline_reason IS NULL) OR
        (status = 'DECLINED' AND decline_reason IS NOT NULL)
    )
);
CREATE INDEX idx_transfers_from_wallet ON transfers(from_wallet_id, created_at DESC);
CREATE INDEX idx_transfers_to_wallet ON transfers(to_wallet_id, created_at DESC);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    transfer_id UUID NOT NULL REFERENCES transfers(id),
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    direction VARCHAR(8) NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount_paise BIGINT NOT NULL CHECK (amount_paise > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ledger_entry_per_wallet_transfer UNIQUE (transfer_id, wallet_id)
);
CREATE INDEX idx_ledger_entries_wallet ON ledger_entries(wallet_id, created_at DESC);

-- This wallet is the only source of demo funding. Funding is a normal balanced transfer.
INSERT INTO users (id, external_id) VALUES ('00000000-0000-0000-0000-000000000001', 'SYSTEM_TREASURY');
INSERT INTO wallets (id, user_id, balance_paise) VALUES
    ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 1000000000000000);
