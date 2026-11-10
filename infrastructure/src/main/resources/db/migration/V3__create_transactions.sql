CREATE TABLE transactions (
    id              UUID          PRIMARY KEY,
    account_id      UUID          NOT NULL REFERENCES accounts (id),
    type            VARCHAR(16)   NOT NULL,
    amount          NUMERIC(19,2) NOT NULL,
    btc_amount_sats BIGINT        NULL,
    quote           NUMERIC(19,8) NULL,
    investment_id   UUID          NULL REFERENCES investments (id),
    request_id      UUID          NULL,
    occurred_at     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_transactions_type CHECK (type IN ('DEPOSIT', 'PURCHASE', 'REDEMPTION', 'REINVESTMENT')),
    -- A few-satoshi residue may be worth R$ 0.00 after FLOOR (data-model §2, "Resíduo de poucos satoshis").
    CONSTRAINT ck_transactions_amount CHECK (amount > 0 OR type IN ('REDEMPTION', 'REINVESTMENT')),
    CONSTRAINT ck_transactions_btc_data CHECK ((type = 'DEPOSIT') = (btc_amount_sats IS NULL AND quote IS NULL))
);

CREATE INDEX ix_transactions_statement ON transactions (account_id, occurred_at DESC);

CREATE INDEX ix_transactions_volume
    ON transactions (occurred_at, type) INCLUDE (btc_amount_sats)
    WHERE type IN ('PURCHASE', 'REDEMPTION');
