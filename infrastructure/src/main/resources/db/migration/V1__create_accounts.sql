CREATE TABLE accounts (
    id            UUID          PRIMARY KEY,
    name          VARCHAR(200)  NOT NULL,
    email         VARCHAR(320)  NOT NULL,
    password_hash VARCHAR(100)  NOT NULL,
    balance       NUMERIC(19,2) NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_accounts_email UNIQUE (email),
    CONSTRAINT ck_accounts_balance_non_negative CHECK (balance >= 0)
);
