CREATE SEQUENCE investments_fifo_seq;

CREATE TABLE investments (
    id                   UUID          PRIMARY KEY,
    account_id           UUID          NOT NULL REFERENCES accounts (id),
    purchased_at         TIMESTAMPTZ   NOT NULL,
    fifo_sequence        BIGINT        NOT NULL,
    invested_amount      NUMERIC(19,2) NOT NULL,
    purchase_quote       NUMERIC(19,8) NOT NULL,
    btc_amount_sats      BIGINT        NOT NULL,
    status               VARCHAR(16)   NOT NULL,
    origin               VARCHAR(16)   NOT NULL,
    origin_investment_id UUID          NULL REFERENCES investments (id),
    closed_at            TIMESTAMPTZ   NULL,
    CONSTRAINT ck_investments_invested_amount CHECK (invested_amount >= 0),
    CONSTRAINT ck_investments_purchase_quote CHECK (purchase_quote > 0),
    CONSTRAINT ck_investments_btc_amount CHECK (btc_amount_sats > 0),
    CONSTRAINT ck_investments_status CHECK (status IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT ck_investments_origin CHECK (origin IN ('PURCHASE', 'REINVESTMENT'))
);

CREATE INDEX ix_investments_active_fifo
    ON investments (account_id, purchased_at, fifo_sequence)
    WHERE status = 'ACTIVE';
