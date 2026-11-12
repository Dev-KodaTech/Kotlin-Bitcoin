# Data Model: Cliente Web do Investimento em Bitcoins

**Feature**: `002-frontend-web` | **Date**: 2026-11-11 | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md)

Este é o modelo **do cliente**. Os dados autoritativos vêm do contrato da 001 ([openapi.yaml](../001-bitcoin-investment-api/contracts/openapi.yaml)). Aqui não há persistência: só valores em memória, `sessionStorage` (sessão) e URL (rota + filtros). Os nomes de tipos estão em inglês (convenção W-19) e o módulo de cada tipo vem entre colchetes.

---

## 1. Value objects [`:core/money`, `:core/time`]

Todos são imutáveis e criados por *factories* que devolvem `Outcome`. O construtor é privado. Internamente usam `BigDecimal` do bignum (W-06), sem expô-lo.

| Tipo | Origem | Invariantes | Operações expostas | Formato de exibição (FR-036) |
|------|--------|-------------|--------------------|------------------------------|
| `Money` | API `Money` (`^\d+\.\d{2}$`) ou entrada da pessoa (W-11) | ≥ 0, escala exata 2 | `parseApi`, `parseUserInput` → `Outcome<AmountInputError, Money>` (exige > 0), `toApiString()` (`"1234.56"`), `plus`, `compareTo`, `isZero`, `ZERO` | `R$ 1.234,56` (NBSP após `R$`) |
| `BtcAmount` | API `Btc` (`^\d+\.\d{8}$`) ou estimativa | ≥ 0, escala exata 8 | `parseApi`, `plus`, `isBelowOneSatoshi()` (`< 0.00000001`, inclusive zero) | `0,00100000 BTC` |
| `QuotePrice` | API `QuotePrice` (`^\d+(\.\d{1,8})?$`) | > 0, escala de 0 a 8 preservada | `parseApi`, `compareTo` | `R$ 400.000,00`, com todas as casas recebidas e mínimo 2 |
| `VariationPercent` | API `variationPercent` (`^-?\d+\.\d{2}$`) | escala 2, com sinal | `parseApi`, `trend: Trend` (`Up` > 0, `Down` < 0, `Flat` = 0) | `+10,00%` / `−5,00%` (U+2212) / `0,00%` |
| `ApiInstant` | API `Instant` (ISO-8601 com offset) | instante válido | `parseApi`, `toBrasilia(): LocalDateTime` | `dd/MM/yyyy HH:mm` (ou `:ss` na cotação), mais "(horário de Brasília)" por bloco |
| `CalendarDate` | entrada `dd/mm/aaaa`, URL/API `yyyy-MM-dd` | data válida do calendário | `parseUserInput`, `parseIso`, `toIso()`, `compareTo` | `dd/mm/aaaa` |

Uma string da API fora do padrão vira `ApiError.Unexpected` no mapeador de `:data` (W-05), nunca uma exceção.

**Erros de entrada** (`sealed interface AmountInputError`): `Empty`, `InvalidFormat`, `TooManyDecimals`, `NotPositive`. Todos levam à mesma mensagem da FR-040 (INVALID_AMOUNT). A distinção serve para testes e para eventual dica de formato.

---

## 2. Modelos de leitura [`:core`]

Produzidos pelos mapeadores de `:data` a partir dos DTOs. Os campos são os do contrato, já tipados.

| Modelo | Campos | Fonte |
|--------|--------|-------|
| `Profile` | `name: String`, `email: String`, `firstName` (derivado: primeira palavra de `name.trim()`; vazio → sem nome) | **`GET /me`: pendente de emenda na 001** ([contracts/api-amendment-get-me.md](./contracts/api-amendment-get-me.md)) |
| `Balance` | `balance: Money` | `GET /me/balance` |
| `Quote` | `buyPrice: QuotePrice`, `sellPrice: QuotePrice`, `quotedAt: ApiInstant` | `GET /quotes/current` |
| `Position` | `quote: Quote`, `totalCurrentValue: Money`, `investments: List<InvestmentPosition>` (ordem da API, FR-019) | `GET /me/investments` |
| `InvestmentPosition` | `id`, `purchasedAt: ApiInstant`, `investedAmount: Money`, `purchaseQuote: QuotePrice`, `btcAmount: BtcAmount`, `variationPercent: VariationPercent`, `currentValue: Money` | idem |
| `Statement` | `from: ApiInstant`, `to: ApiInstant`, `entries: List<StatementEntry>` (mais recente primeiro) | `GET /me/statement` |
| `StatementEntry` | `id`, `type: EntryType` (`Deposit`, `Purchase`, `Redemption`, `Reinvestment`), `amount: Money`, `btcAmount: BtcAmount?`, `quote: QuotePrice?`, `investmentId?`, `requestId?`, `occurredAt: ApiInstant` | idem |
| `DailyVolume` | `date: CalendarDate`, `boughtBtc: BtcAmount`, `soldBtc: BtcAmount` | `GET /market/daily-volume` |
| `QuoteHistory` | `points: List<HistoryPoint(slotAt, buyPrice, sellPrice)>` (cronológico) | `GET /quotes/history` |

**Resultados de operação** (exibidos sem alteração, FR-017/FR-024):

| Modelo | Campos | Fonte |
|--------|--------|-------|
| `DepositResult` | `transactionId`, `amount: Money`, `balance: Money`, `occurredAt` | `POST /me/deposits` 201 |
| `PurchaseResult` | `investmentId`, `transactionId`, `amount: Money`, `btcAmount: BtcAmount`, `quote: QuotePrice`, `purchasedAt`, `balance: Money` | `POST /me/purchases` 201 |
| `RedemptionResult` | `requestId`, `amount: Money`, `btcSold: BtcAmount`, `quote: QuotePrice`, `balance: Money`, `occurredAt`, `liquidations: List<Liquidation(investmentId, amount, btcAmount, isPartial)>`, `reinvestment: Reinvestment?(investmentId, btcAmount, investedAmount, purchaseQuote, purchasedAt)` | `POST /me/redemptions` 201 |
| `LoginResult` | `accessToken: String` (nunca logado nem exibido), `expiresIn: Duration` | `POST /auth/login` 200 |
| `AccountCreated` | `id`, `name`, `email` (o saldo é ignorado: o painel consulta o saldo) | `POST /accounts` 201 |

---

## 3. Erros tipados [`:core/error`]

```text
sealed interface ApiError
├── Validation(fieldErrors: List<FieldError(field, message)>)   ← VALIDATION_ERROR
├── InvalidAmount                                               ← INVALID_AMOUNT
├── EmailAlreadyInUse                                           ← EMAIL_ALREADY_IN_USE
├── InvalidCredentials                                          ← INVALID_CREDENTIALS
├── Unauthorized                                                ← UNAUTHORIZED (dispara fluxo de sessão expirada)
├── InsufficientBalance                                         ← INSUFFICIENT_BALANCE
├── RedemptionExceedsAvailable                                  ← REDEMPTION_EXCEEDS_AVAILABLE
├── InvalidDateRange                                            ← INVALID_DATE_RANGE
├── QuoteUnavailable                                            ← QUOTE_UNAVAILABLE
├── ServiceUnavailable                                          ← SERVICE_UNAVAILABLE
├── Unexpected                                                  ← NOT_FOUND, INTERNAL_ERROR, code desconhecido, status/corpo fora do contrato
└── NoConnection                                                ← falha de transporte em leitura/login/cadastro

sealed interface OperationError        // só depósito, compra e resgate
├── Rejected(error: ApiError)          // resposta definitiva da API: nada foi efetivado
└── Uncertain                          // transporte caiu após o envio (W-05): orientar a conferir saldo/extrato, sem reenvio
```

A cobertura dos 12 `code` do enum `Problem.code` do contrato é garantida duas vezes: pelo `when` exaustivo em `ProblemMapper` (compilador) e pelo teste de cobertura de fixtures no build da API (W-12). As mensagens estão em [contracts/error-messages.md](./contracts/error-messages.md).

---

## 4. Estado das telas [`:state`]

Toda tela que carrega dados usa o bloco genérico (FR-042):

```text
sealed interface Loadable<out T> { Loading; Loaded(value: T); Empty; Failed(error: ApiError) }
```

`Empty` é usado só onde a spec define estado vazio (investimentos, extrato, histórico). A cotação tem um estado próprio, porque "indisponível" não é erro genérico (FR-013):

```text
sealed interface QuoteState { Loading; Available(quote: Quote); Unavailable; Failed(error: ApiError) }
```

`Unavailable` **nunca** carrega a cotação anterior. A UI não pode exibir valores antigos (US2-3).

### 4.1 Sessão [`:state/session`]

`Session(accessToken: String, expiresAt: Instant)`. O `toString()` é sobrescrito para `Session(****)` (FR-008).

```text
SessionState: Resolving ──(storage tem sessão válida | aba respondeu em ≤300 ms)──▶ Active(session)
              Resolving ──(nada)───────────────────────────────────────────────────▶ Anonymous(reason = None)
              Active ──(now ≥ expiresAt | 401 | msg "expired")──▶ Anonymous(reason = Expired)
              Active ──("Sair" | msg "logout")───────────────────▶ Anonymous(reason = LoggedOut)
              Anonymous ──(login ok | msg "share")───────────────▶ Active(session)
```

Toda saída de `Active` limpa o `sessionStorage`, publica no `TabChannel` (se a origem for local) e emite `SessionEvent` para a navegação. `Expired` leva ao login com `returnTo` = rota atual e à mensagem "Sua sessão expirou. Entre novamente para continuar." `LoggedOut` leva ao login sem `returnTo` e sem mensagem.

### 4.2 Operações financeiras: máquina de estados (FR-043, SC-004)

Compra e resgate (o depósito usa o mesmo modelo, sem `Reviewing`):

```text
            onAmountChange
          ┌──────────────┐
          ▼              │
      Editing(input, inputError?) ──onReview [input válido]──▶ Reviewing(amount, estimate, warnings)
          ▲                                                        │   ▲ (cotação atualizada → estimate recalculada)
          │◀───────────── onEdit ──────────────────────────────────┘   │
          │                                                        onConfirm
          │                                                            ▼
          │                                                     Submitting(amount)   ← intenções ignoradas aqui
          │                                       ┌────────────────────┼─────────────────────┐
          │                                       ▼                    ▼                     ▼
          │                               Succeeded(result)   Rejected(amount, error)   Uncertain
          │                                       │                    │                     │
          └──────────── onNewOperation ───────────┴──── onEdit/onRetry*┘          (atalhos: saldo, extrato; sem reenvio)
```

- `onConfirm` só é aceito em `Reviewing` (ou `Editing` válido, no depósito). A transição para `Submitting` acontece **antes** da chamada (single-flight). Cliques, Enter e toques extras em `Submitting` não têm efeito.
- `Rejected` com `QuoteUnavailable`/`ServiceUnavailable` oferece "Tentar de novo", que volta a `Reviewing` com a mesma quantia e exige nova confirmação explícita. É uma nova operação, não reenvio automático. Com `InsufficientBalance`, oferece o saldo atual (recarregado) e um atalho para depositar. Com `RedemptionExceedsAvailable`, mostra o disponível atualizado (posição recarregada) e reabilita "Resgatar tudo". Com `InvalidAmount`, volta a `Editing` com a mensagem no campo.
- `Uncertain` **não** oferece "Tentar de novo" (FR-041, Edge Case).
- `Succeeded` mostra só o resultado da API. A estimativa é descartada do estado (Edge Case: "a tela de resultado não mostra a estimativa"). O foco vai ao título do resultado e há um anúncio *assertive* "Compra realizada" (FR-046/FR-047).
- O estado vive no ViewModel. Recarregar descarta tudo e volta a `Editing` vazio (FR-043).

### 4.3 Estimativas [`:core/estimate`] (rótulo obrigatório "Estimativa" na UI)

| Estimativa | Fórmula | Arredondamento | Avisos |
|-----------|---------|----------------|--------|
| `BuyEstimate` (FR-015) | `amount ÷ sellPrice` | truncada em 8 casas (`FLOOR`) | `belowOneSatoshi` se o resultado for `< 0,00000001` (FR-016): só avisa, não bloqueia |
| `RedeemEstimate` (FR-023) | `amount ÷ buyPrice` | truncada em 8 casas (`FLOOR`). É aproximação e **não** reproduz a liquidação FIFO da 001 (Assumptions da spec) | `fromRedeemAll` se o valor veio de "Resgatar tudo" (FR-022): aviso de que pode exceder o disponível se a cotação cair |

Cada estimativa carrega a `Quote` usada (preço + `quotedAt`) para exibir "cotação e instante usados na estimativa" (US4-1). Com `QuoteState.Unavailable`, a estimativa é `null` e a UI mostra "Cotação indisponível". A confirmação continua habilitada, porque a decisão é da API (US4-7).

### 4.4 Estados por tela

| ViewModel | `UiState` (campos principais) | Auto-refresh (W-10) |
|-----------|-------------------------------|---------------------|
| `RegisterViewModel` | `name`, `email`, `password`, `fieldErrors: Map<Field, FieldError>`, `formError?`, `isSubmitting`, `focusRequest: Field?` | não |
| `LoginViewModel` | `email`, `password`, `banner: LoginBanner?` (`SessionExpired`, `AccountCreated`, `InvalidCredentials`, erro genérico), `isSubmitting`, `returnTo: AppRoute?` | não |
| `DashboardViewModel` | `greeting: Greeting` (`Named(firstName)` / `Anonymous`), `balance: Loadable<Money>`, `quote: QuoteState`, `position: PositionTotal` (`Loading`/`Value(Money)`/`DependsOnQuote`/`NoInvestments`/`Failed`), `isAutoRefreshPaused` | cotação + posição |
| `DepositViewModel` | máquina §4.2 sem revisão | não |
| `PurchaseViewModel` | máquina §4.2 + `balance: Loadable<Money>`, `quote: QuoteState`, `estimate: BuyEstimate?` | cotação (em `Reviewing`) |
| `InvestmentsViewModel` | `position: Loadable<Position>` + `QuoteUnavailable` como estado próprio com "Tentar de novo", `isAutoRefreshPaused` | posição |
| `RedemptionViewModel` | máquina §4.2 + `available: Loadable<Position>` (total, cotação de compra, instante), `estimate: RedeemEstimate?`, `isAutoRefreshPaused` | posição |
| `StatementViewModel` | `filter: StatementFilter` (vem da rota), `filterInput: (fromText, toText)`, `filterError?`, `statement: Loadable<GroupedStatement>`. O anterior é mantido em erro de intervalo (FR-030) | não |
| `MarketViewModel` | `volume: Loadable<DailyVolume>`, `history: Loadable<ChartData>`, `isRefreshing` | não (botão "Atualizar", FR-035) |

**Regras de dependência entre blocos** (FR-013): cada bloco tem o próprio `Loadable` e a falha de um não altera os outros. No painel, `position = DependsOnQuote` quando a posição falha com `QuoteUnavailable` (US2-3). A posição sem investimentos vira `NoInvestments`, que mostra `R$ 0,00` e o convite (US2-4).

---

## 5. Extrato agrupado [`:core/statement`]

```text
GroupedStatement(from, to, items: List<StatementItem>)
sealed interface StatementItem
├── Single(entry)                                       // Deposit, Purchase, Redemption/Reinvestment sem requestId
└── RedemptionGroup(requestId, entries, totalRedeemed: Money, occurredAt)
```

- `StatementGrouper.group(entries)` é uma função pura. Ela percorre na ordem da API e reúne **todas** as entradas com o mesmo `requestId` não nulo num grupo, posicionado onde aparece a primeira entrada do grupo. A ordem interna é a da API.
- `totalRedeemed = Σ amount` das entradas `Redemption` do grupo (soma exata em `Money`, **sem** `Reinvestment`, FR-029).
- Um grupo cortado pelo filtro continua agrupado com as entradas presentes (Edge Case).
- `Reinvestment` é exibida com o rótulo "não altera o saldo".

**Filtro** (`StatementFilter`): `Default` (sem parâmetros, período padrão da API) ou `Range(from: CalendarDate, to: CalendarDate)`. A validação local exige as duas datas, datas válidas e `from ≤ to` → `FilterError.InvalidRange` (mensagem da FR-040). Um `INVALID_DATE_RANGE` da API produz o mesmo erro. O filtro é parte da rota (W-04).

---

## 6. Mercado [`:core/market`]

- `ChartData(buy: List<Segment>, sell: List<Segment>, xRange: (start, end), yRange: (min: QuotePrice, max: QuotePrice), summary: ChartSummary)`.
- `Segment = List<HistoryPoint>`. Um segmento quebra quando `slotAt[i+1] − slotAt[i] > 10 min` (FR-033). Ver W-13.
- `ChartSummary`: por série, `min`, `max`, `last` (valor + instante), em `QuotePrice` exato (FR-034).
- `TableRows`: uma linha por slot de 10 min entre o primeiro e o último ponto. Slots sem ponto aparecem como `Missing(slotAt)` ("sem registro").

---

## 7. Rotas [`:core/navigation`]

`sealed interface AppRoute` com `toFragment()` e `companion parse(fragment): AppRoute?`. Convenção: a rota do extrato guarda os parâmetros brutos do fragmento (`Statement(from: String?, to: String?)`, para preservar a entrada inválida e exibir o erro). O `StatementFilter` tipado (§5) é obtido ao resolver a rota (`toFilter(): Outcome<FilterError, StatementFilter>`). A tabela completa (fragmentos, parâmetros, proteção, títulos) está em [contracts/ui-routes.md](./contracts/ui-routes.md).

`ReturnDestination.resolve(raw: String?): AppRoute`. Devolve a rota só se `parse(raw)` resultar numa rota **protegida**. Caso contrário, devolve `Dashboard` (FR-007, Edge Case).

---

## 8. Mensagens entre abas [`:state/session`, serialização em `:data`]

`TabMessage` (JSON no `BroadcastChannel("kb-session")`):

| `type` | Campos | Emissor → efeito |
|--------|--------|------------------|
| `request` | (nenhum) | aba sem sessão → abas com sessão respondem `share` |
| `share` | `accessToken`, `expiresAtEpochMs` | aba com sessão/recém-logada → receptora grava no `sessionStorage` e fica `Active` |
| `logout` | (nenhum) | "Sair" → receptoras ficam `Anonymous(LoggedOut)` |
| `expired` | (nenhum) | expiração/401 → receptoras ficam `Anonymous(Expired)` |

Mensagens malformadas são ignoradas sem log de conteúdo.
