# Data Model: API de Investimento em Bitcoins

**Feature**: 001-bitcoin-investment-api | **Date**: 2026-11-09 | **Plan**: [plan.md](./plan.md)

Este documento separa o **modelo de domínio** (módulo `domain`, Kotlin puro) do **esquema relacional** (módulo `infrastructure`, Flyway). As entidades JPA espelham o esquema e são convertidas por mappers; o domínio não as conhece.

## 1. Value objects (domain)

| Tipo | Representação | Invariantes | Referência |
|------|---------------|-------------|------------|
| `Money` | `BigDecimal`, escala exatamente 2 | não aceita escala > 2 sem arredondamento explícito; operações retornam `Money` | FR-032 |
| `PositiveMoney` | `Money` > 0 | criado a partir de entrada do cliente; falha → `InvalidAmount` | FR-008, FR-013, FR-018 |
| `BtcAmount` | `Long` em satoshis | ≥ 0; formatação com 8 casas só na `api` | FR-032 |
| `QuotePrice` | `BigDecimal` > 0, escala da fonte (≤ 8) | nunca arredondado ao ser recebido | FR-011 |
| `Quote` | `buyPrice: QuotePrice`, `sellPrice: QuotePrice`, `quotedAt: Instant` | `quotedAt` = instante de recepção da fonte | FR-011, FR-012a |
| `Email` | `String` normalizado (`trim` + minúsculas) | formato válido | FR-002, FR-003 |
| `AccountId`, `InvestmentId`, `TransactionId`, `RedemptionRequestId` | `UUID` | — | — |
| `Outcome<E, T>` | sealed `Success(value)` / `Failure(error)` | — | Princípio III |

### Conversões (funções puras em `domain`)

| Função | Regra | Referência |
|--------|-------|------------|
| `BtcConversion.purchase(amount, sellPrice)` | `FLOOR(amount × 10⁸ ÷ sellPrice)` sat; `< 1` → `InvalidAmount` | FR-013, Edge Case "valor muito pequeno" |
| `BtcConversion.currentValue(sats, buyPrice)` | `FLOOR(sats × buyPrice ÷ 10⁸)` no centavo | FR-017 |
| `BtcConversion.satsToCover(amount, buyPrice)` | `CEILING(amount × 10⁸ ÷ buyPrice)` sat | FR-019a |
| `PriceVariation.percent(purchaseQuote, currentBuy)` | `(currentBuy − purchaseQuote) ÷ purchaseQuote × 100`, `HALF_UP` 2 casas | FR-017 |

## 2. Entidades de domínio

### Account

| Campo | Tipo | Regras |
|-------|------|--------|
| `id` | `AccountId` | gerado na criação |
| `name` | `String` | não vazio (trim) |
| `email` | `Email` | único |
| `passwordHash` | `String` | BCrypt; nunca exposto (FR-007) |
| `balance` | `Money` | ≥ 0 sempre (FR-010); inicia em 0,00 |
| `createdAt` | `Instant` | — |

Comportamentos: `deposit(PositiveMoney)`, `debit(PositiveMoney): Outcome<InsufficientBalance, Account>`, `credit(Money)`.

### Investment

| Campo | Tipo | Regras |
|-------|------|--------|
| `id` | `InvestmentId` | — |
| `accountId` | `AccountId` | dono (FR-033) |
| `purchasedAt` | `Instant` | data da compra; no reinvestimento, **copiada** do investimento de origem (FR-021) |
| `fifoSequence` | `Long` | desempate da fila FIFO; no reinvestimento, **copiado** da origem |
| `investedAmount` | `Money` | valor investido; no reinvestimento = `FLOOR(satsResidual × purchaseQuote ÷ 10⁸)` (FR-020) |
| `purchaseQuote` | `QuotePrice` | cotação de venda usada na compra; no reinvestimento, a cotação original (FR-020) |
| `btcAmount` | `BtcAmount` | > 0 |
| `status` | `ACTIVE` \| `CLOSED` | — |
| `origin` | `PURCHASE` \| `REINVESTMENT` | — |
| `originInvestmentId` | `InvestmentId?` | obrigatório se `origin = REINVESTMENT` |
| `closedAt` | `Instant?` | preenchido ao encerrar |

**Transições de estado**:

```text
            compra (FR-014)                 resgate total ou parcial (FR-019/020)
  (novo) ─────────────────────► ACTIVE ───────────────────────────────────────► CLOSED
                                  ▲                                                │
                                  │   liquidação parcial: cria NOVO Investment     │
                                  └──── (origin=REINVESTMENT, mesma purchasedAt, ◄─┘
                                         mesma fifoSequence, mesma purchaseQuote,
                                         btcAmount = residual)
```

`CLOSED` é terminal. Um investimento nunca é alterado depois de criado, exceto pela transição para `CLOSED`.

### Transaction (lançamento de extrato, imutável)

| Campo | Tipo | Regras |
|-------|------|--------|
| `id` | `TransactionId` | — |
| `accountId` | `AccountId` | — |
| `type` | `DEPOSIT` \| `PURCHASE` \| `REDEMPTION` \| `REINVESTMENT` | FR-023 |
| `amount` | `Money` | depósito: valor; compra: valor investido; resgate: valor creditado por aquele investimento; reinvestimento: valor investido do novo investimento |
| `btcAmount` | `BtcAmount?` | nulo só em `DEPOSIT` |
| `quote` | `QuotePrice?` | nulo só em `DEPOSIT`; `PURCHASE` = cotação de venda; `REDEMPTION` = cotação de compra do momento; `REINVESTMENT` = cotação original |
| `investmentId` | `InvestmentId?` | `PURCHASE`: criado; `REDEMPTION`: liquidado; `REINVESTMENT`: criado |
| `requestId` | `RedemptionRequestId?` | obrigatório em `REDEMPTION`/`REINVESTMENT` e igual em todas as linhas de um resgate (FR-024, FR-024a) |
| `occurredAt` | `Instant` | — |

**Resíduo de poucos satoshis**: um reinvestimento pode ter poucos satoshis cujo valor, após `FLOOR`, é R$ 0,00. Ele continua ativo, aparece na posição com valor R$ 0,00 e, no próximo resgate, é liquidado por inteiro primeiro (FIFO) com crédito R$ 0,00. É a consequência direta de FR-017/FR-019a: o cliente nunca recebe valor sem lastro e cede no máximo frações de centavo.

Invariantes verificadas em testes:
- `balance = Σ DEPOSIT − Σ PURCHASE + Σ REDEMPTION` (SC-002). `REINVESTMENT` não afeta saldo.
- `Σ btcAmount(ACTIVE) = Σ btc(PURCHASE) − Σ btc(REDEMPTION)` por conta (SC-003).
- Em um mesmo `requestId`: `Σ amount(REDEMPTION) = valor pedido`; no máximo uma linha `REINVESTMENT`.

### QuoteSnapshot (histórico)

| Campo | Tipo | Regras |
|-------|------|--------|
| `slotAt` | `Instant` | múltiplo exato de 10 min; único (FR-027) |
| `buyPrice`, `sellPrice` | `QuotePrice` | — |
| `capturedAt` | `Instant` | instante de recepção da fonte |

Expurgo: `slotAt < now − 90 dias` (FR-029).

### NotificationEvent (outbox)

| Campo | Tipo | Regras |
|-------|------|--------|
| `id` | `UUID` | chave de deduplicação |
| `type` | `DEPOSIT_CONFIRMED` \| `PURCHASE_CONFIRMED` \| `REDEMPTION_CONFIRMED` | FR-030 |
| `accountId`, `recipientEmail`, `recipientName` | — | dados do destinatário no momento da operação |
| `payload` | JSON | depósito: `amount`; compra: `amount`, `btcAmount`; resgate: `amount`, `btcAmount` (soma dos sats vendidos) |
| `createdAt`, `publishedAt?`, `sentAt?` | `Instant` | gravado na mesma transação da operação (FR-031) |

## 3. Agregados de leitura (sem persistência própria)

| Leitura | Composição | Referência |
|---------|------------|------------|
| `InvestmentPosition` | por investimento ativo: `purchasedAt`, `investedAmount`, `purchaseQuote`, `variationPercent`, `currentValue`; mais `totalCurrentValue` e a `Quote` usada | FR-016, FR-017, FR-022 |
| `Statement` | lista de `Transaction` no intervalo, ordem `occurredAt DESC` | FR-025 |
| `DailyVolume` | `Σ btc(PURCHASE)` e `Σ btc(REDEMPTION)` de todas as contas em `[início do dia, agora]` em America/Sao_Paulo | FR-026 |
| `QuoteHistory` | `QuoteSnapshot` com `slotAt > now − 24 h`, ordem crescente | FR-028 |

## 4. Esquema relacional (Flyway)

```text
V1__create_accounts.sql
V2__create_investments.sql
V3__create_transactions.sql
V4__create_quote_history.sql
V5__create_outbox_events.sql
V6__create_shedlock.sql
```

### accounts

| Coluna | Tipo | Restrições |
|--------|------|------------|
| `id` | `UUID` | PK |
| `name` | `VARCHAR(200)` | NOT NULL |
| `email` | `VARCHAR(320)` | NOT NULL, UNIQUE |
| `password_hash` | `VARCHAR(100)` | NOT NULL |
| `balance` | `NUMERIC(19,2)` | NOT NULL, `CHECK (balance >= 0)` |
| `created_at` | `TIMESTAMPTZ` | NOT NULL |

### investments

| Coluna | Tipo | Restrições |
|--------|------|------------|
| `id` | `UUID` | PK |
| `account_id` | `UUID` | NOT NULL, FK → accounts |
| `purchased_at` | `TIMESTAMPTZ` | NOT NULL |
| `fifo_sequence` | `BIGINT` | NOT NULL (valor de `investments_fifo_seq` na compra; copiado no reinvestimento) |
| `invested_amount` | `NUMERIC(19,2)` | NOT NULL, `CHECK (>= 0)` (reinvestimento de poucos satoshis pode valer R$ 0,00 após o `FLOOR`) |
| `purchase_quote` | `NUMERIC(19,8)` | NOT NULL, `CHECK (> 0)` |
| `btc_amount_sats` | `BIGINT` | NOT NULL, `CHECK (> 0)` |
| `status` | `VARCHAR(16)` | NOT NULL, `CHECK IN ('ACTIVE','CLOSED')` |
| `origin` | `VARCHAR(16)` | NOT NULL, `CHECK IN ('PURCHASE','REINVESTMENT')` |
| `origin_investment_id` | `UUID` | NULL, FK → investments |
| `closed_at` | `TIMESTAMPTZ` | NULL |

Índice: `ix_investments_active_fifo (account_id, purchased_at, fifo_sequence) WHERE status = 'ACTIVE'`.

### transactions

| Coluna | Tipo | Restrições |
|--------|------|------------|
| `id` | `UUID` | PK |
| `account_id` | `UUID` | NOT NULL, FK → accounts |
| `type` | `VARCHAR(16)` | NOT NULL, `CHECK IN ('DEPOSIT','PURCHASE','REDEMPTION','REINVESTMENT')` |
| `amount` | `NUMERIC(19,2)` | NOT NULL, `CHECK (amount > 0 OR type IN ('REDEMPTION','REINVESTMENT'))` — ver "Resíduo de poucos satoshis" |
| `btc_amount_sats` | `BIGINT` | NULL |
| `quote` | `NUMERIC(19,8)` | NULL |
| `investment_id` | `UUID` | NULL, FK → investments |
| `request_id` | `UUID` | NULL |
| `occurred_at` | `TIMESTAMPTZ` | NOT NULL |

Restrição: `CHECK ((type = 'DEPOSIT') = (btc_amount_sats IS NULL AND quote IS NULL))`.
Índices: `ix_transactions_statement (account_id, occurred_at DESC)`; `ix_transactions_volume (occurred_at, type) INCLUDE (btc_amount_sats) WHERE type IN ('PURCHASE','REDEMPTION')`.

### quote_history

| Coluna | Tipo | Restrições |
|--------|------|------------|
| `slot_at` | `TIMESTAMPTZ` | PK (único; idempotência do job) |
| `buy_price` | `NUMERIC(19,8)` | NOT NULL |
| `sell_price` | `NUMERIC(19,8)` | NOT NULL |
| `captured_at` | `TIMESTAMPTZ` | NOT NULL |

### outbox_events

| Coluna | Tipo | Restrições |
|--------|------|------------|
| `id` | `UUID` | PK |
| `type` | `VARCHAR(32)` | NOT NULL |
| `payload` | `JSONB` | NOT NULL (sem senha nem token) |
| `created_at` | `TIMESTAMPTZ` | NOT NULL |
| `published_at` | `TIMESTAMPTZ` | NULL |
| `sent_at` | `TIMESTAMPTZ` | NULL |

Índice: `ix_outbox_unpublished (created_at) WHERE published_at IS NULL`.

### shedlock

Esquema padrão do ShedLock (`name` PK, `lock_until`, `locked_at`, `locked_by`).

## 5. Portas (application) × dados

| Porta | Operações principais |
|-------|----------------------|
| `AccountRepository` | `save`, `findByEmail`, `findById`, `findByIdForUpdate` (`FOR UPDATE`), `existsByEmail` |
| `InvestmentRepository` | `save`, `findActiveByAccountInFifoOrder`, `close`, `nextFifoSequence` |
| `TransactionRepository` | `saveAll`, `findByAccountBetween`, `sumBtcByTypeBetween` |
| `QuoteHistoryRepository` | `saveIfAbsent(slot)`, `findSince`, `deleteOlderThan` |
| `QuoteProvider` | `fetchCurrent(): Outcome<QuoteProviderError, Quote>` |
| `QuoteCache` | `get(): Quote?`, `put(Quote)` |
| `NotificationPublisher` | `publish(NotificationEvent)` (grava no outbox, mesma transação) |
| `EmailSender` | `send(EmailMessage): Outcome<EmailSendError, Unit>` |
| `PasswordHasher` | `hash`, `matches` |
| `TokenIssuer` | `issue(AccountId): AccessToken` |
| `TransactionRunner` | `run { ... }` com rollback em `Failure` |
| `Clock` | `now(): Instant` |

Todas as operações de porta que tocam infraestrutura devolvem `Outcome<InfrastructureFailure, T>` (ex.: `findById(id): Outcome<InfrastructureFailure, Account?>`); a tabela acima omite esse envelope por brevidade. Única exceção: `QuoteCache` é best-effort (falha do Redis = cache vazio, só registrada em log; R-06). `AccountRepository.save` devolve `Outcome<SaveAccountError, Account>`, com `SaveAccountError = EmailTaken | Infrastructure(InfrastructureFailure)`: a violação de `UNIQUE` em `accounts.email` vira `EmailTaken`, o que cobre cadastros simultâneos com o mesmo e-mail (FR-002).
