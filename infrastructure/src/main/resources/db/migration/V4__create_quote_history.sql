CREATE TABLE quote_history (
    slot_at     TIMESTAMPTZ   PRIMARY KEY,
    buy_price   NUMERIC(19,8) NOT NULL,
    sell_price  NUMERIC(19,8) NOT NULL,
    captured_at TIMESTAMPTZ   NOT NULL
);
