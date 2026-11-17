# Contrato de teste: fixtures do consumidor (`web/contract-fixtures/`)

**Feature**: `002-frontend-web` | Decisão: [research W-12](../research.md#w-12-testes-de-consumidor-fixtures-conformes-ao-contrato)

As fixtures são **exemplos** de respostas da API usados pelos testes do cliente (`MockEngine`). Elas não definem o contrato: são **validadas** contra `specs/001-bitcoin-investment-api/contracts/openapi.yaml` e contra respostas reais, no build da API.

## Layout

```text
web/contract-fixtures/
├── manifest.json
├── success/
│   ├── accounts-201.json
│   ├── auth-login-200.json
│   ├── me-200.json                      # depende da emenda GET /me (api-amendment-get-me.md)
│   ├── me-balance-200.json
│   ├── me-deposits-201.json
│   ├── quotes-current-200.json
│   ├── quotes-current-200-8-decimals.json
│   ├── quotes-history-200-with-gap.json
│   ├── quotes-history-200-empty.json
│   ├── me-purchases-201.json
│   ├── me-investments-200.json          # variação +, − e 0
│   ├── me-investments-200-empty.json
│   ├── me-redemptions-201-partial.json  # com reinvestment
│   ├── me-redemptions-201-full.json     # reinvestment null
│   ├── me-statement-200.json            # com grupo de resgate (2 resgates + 1 reinvestimento)
│   ├── me-statement-200-empty.json
│   ├── market-daily-volume-200.json
│   ├── limits-me-balance-200.json       # limites (SC-002): R$ 99999999999.99
│   └── limits-me-investments-200.json   # limites (SC-002): 0.00000001 BTC e cotação com 8 casas
└── problem/
    ├── VALIDATION_ERROR.json
    ├── INVALID_AMOUNT.json
    ├── INVALID_CREDENTIALS.json
    ├── UNAUTHORIZED.json
    ├── NOT_FOUND.json
    ├── EMAIL_ALREADY_IN_USE.json
    ├── INSUFFICIENT_BALANCE.json
    ├── REDEMPTION_EXCEEDS_AVAILABLE.json
    ├── INVALID_DATE_RANGE.json
    ├── QUOTE_UNAVAILABLE.json
    ├── SERVICE_UNAVAILABLE.json
    └── INTERNAL_ERROR.json
```

## `manifest.json`

```json
{
  "contract": "specs/001-bitcoin-investment-api/contracts/openapi.yaml",
  "fixtures": [
    {
      "file": "success/me-purchases-201.json",
      "method": "POST",
      "path": "/me/purchases",
      "status": 201,
      "scenario": "purchase-ok",
      "realScenario": "purchaseAfterDeposit"
    },
    {
      "file": "problem/INSUFFICIENT_BALANCE.json",
      "method": "POST",
      "path": "/me/purchases",
      "status": 422,
      "scenario": "insufficient-balance",
      "realScenario": "purchaseAboveBalance"
    }
  ]
}
```

| Campo | Obrigatório | Significado |
|-------|-------------|-------------|
| `file` | sim | caminho relativo a `web/contract-fixtures/` |
| `method`, `path` | sim | operação do contrato (path relativo a `servers.url` = `/api/v1`) |
| `status` | sim | status documentado para a operação |
| `scenario` | sim | chave usada pelo `MockEngine` dos testes de `:data` |
| `realScenario` | não | nome de um cenário executável no teste da API (MockMvc) cuja resposta real deve ter a **mesma forma** da fixture |
| `pendingAmendment` | não | operação ainda não presente no contrato (ex.: `"GET /me"`). A fixture é pulada com aviso pelas verificações 1, 3, 4 e 5 até a emenda. O campo MUST ser removido quando a emenda for aplicada |

Uma fixture Problem pode aparecer em várias operações (uma entrada de manifest por operação/status).

## Verificações (no build da API: `bootstrap/.../contract/WebClientFixturesContractTest.kt`)

1. Todo `file` existe, é JSON válido e é aceito pelo schema da resposta `method path status` do contrato.
2. O conjunto de `code` em `problem/*.json` é **igual** ao `enum` de `Problem.code` do contrato.
3. Toda entrada do manifest aponta para uma operação existente no contrato, com o status indicado.
4. Para cada `realScenario`: a resposta real tem a mesma forma (chaves recursivas, tipos JSON e padrão de cada string de valor) que a fixture.
5. *(ativada só na Phase 12, tarefa T216, para não deixar o `check` da raiz vermelho enquanto as stories acrescentam fixturas)* Toda operação presente no manifest tem ao menos uma fixture de sucesso.

## Uso no cliente

`:data` gera `ContractFixtures.kt` (task `generateContractFixtures`, entrada `web/contract-fixtures/**`, saída `build/generated/contractFixtures/commonTest`). Os testes usam `ContractFixtures.byScenario("purchase-ok")`. Editar uma fixture força a geração de novo (entradas declaradas da task).
