# Quickstart: validação da API de Investimento em Bitcoins

**Feature**: 001-bitcoin-investment-api | **Plan**: [plan.md](./plan.md) | **Contrato**: [contracts/openapi.yaml](./contracts/openapi.yaml) | **Modelo**: [data-model.md](./data-model.md)

Guia para subir o ambiente e provar, de ponta a ponta, que a feature atende a spec. Não contém código de implementação.

## 1. Pré-requisitos

- JDK 21, Docker 24+ com Docker Compose v2, `curl` e `jq`.
- Copiar `.env.example` para `.env` e preencher:
  - `JWT_SECRET`: valor aleatório de pelo menos 32 bytes (ex.: `openssl rand -base64 48`).
  - `QUOTE_PROVIDER_URL`: padrão `https://www.mercadobitcoin.net/api/BTC/ticker/`.
  - `BREVO_API_KEY`: só no perfil `prod`; em dev, os e-mails vão para o Mailpit.

> **Risco conhecido** (ver [research.md R-06](./research.md#r-06-cotação-fonte-cache-e-indisponibilidade)): em 2026-11-09 a API v1 do Mercado Bitcoin respondeu 521. Se a fonte estiver fora, as rotas de cotação, compra, posição e resgate respondem `503 QUOTE_UNAVAILABLE`, como a spec exige. Para validar o fluxo completo localmente, aponte `QUOTE_PROVIDER_URL` para o stub WireMock do compose (`http://quote-stub:8080/api/BTC/ticker/`, perfil `stub`).

## 2. Verificação automatizada

```bash
./gradlew check            # unitários, integração (Testcontainers), contrato, ktlint, detekt
docker build -t kotlinbitcoins:local .
```

Resultado esperado: build verde. Os testes de contrato cobrem cada `code` de erro (SC-010), isolamento entre clientes (SC-004) e concorrência (SC-005).

## 3. Subir o ambiente

```bash
docker compose --profile stub up -d --build
docker compose ps          # todos "healthy": nginx, app (2 réplicas), postgres, redis, rabbitmq, mailpit, quote-stub
```

| Serviço | URL |
|---------|-----|
| API (via Nginx) | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Mailpit | http://localhost:8025 |
| RabbitMQ management | http://localhost:15672 |

O stub de cotação começa com `buy = 400000.00` e `sell = 400000.00`. Para mudar os preços durante o roteiro, use o endpoint de administração do WireMock (`POST http://localhost:8089/__admin/mappings`); o arquivo de mapeamento fica em `docker/quote-stub/mappings/ticker.json`. Depois de mudar os preços, aguarde 10 s (validade do cache) antes do passo seguinte.

## 4. Roteiro de validação ponta a ponta

Defina `API=http://localhost:8080/api/v1`.

| # | Passo | Resultado esperado | Spec |
|---|-------|--------------------|------|
| 1 | `POST $API/accounts` com `{"name":"Ana","email":"ana@example.com","password":"senha-segura"}` | `201`, `balance: "0.00"`, sem senha na resposta | US1-1 |
| 2 | Repetir o passo 1 com `ANA@example.com ` | `409 EMAIL_ALREADY_IN_USE` | US1-2, FR-002 |
| 3 | `POST $API/auth/login` com senha errada; depois com e-mail inexistente | os dois `401 INVALID_CREDENTIALS` com corpo idêntico | US1-5 |
| 4 | Login correto; guardar `accessToken` em `TOKEN` | `200`, `expiresIn: 3600` | US1-4 |
| 5 | `GET $API/me/balance` sem token; com token adulterado | `401 UNAUTHORIZED` | US1-6 |
| 6 | `POST $API/me/deposits` `{"amount":"1000.00"}` | `201`, `balance: "1000.00"`; e-mail com o valor no Mailpit em segundos | US2, FR-030 |
| 7 | Depositar `"0"`, `"-5"`, `"abc"`, `"10.123"` | `400 INVALID_AMOUNT`, saldo inalterado | US2-4 |
| 8 | `GET $API/quotes/current` | `buyPrice`, `sellPrice`, `quotedAt` com offset `-03:00` | US4-1, FR-034a |
| 9 | `POST $API/me/purchases` `{"amount":"400.00"}` | `201`, `btcAmount: "0.00100000"`, `quote: "400000.00"`, `balance: "600.00"` | US5-1 |
| 10 | `POST $API/me/purchases` `{"amount":"600.01"}` | `422 INSUFFICIENT_BALANCE`; saldo, investimentos e extrato inalterados | US5-2 |
| 11 | Mudar o stub para `buy = 440000.00`; `GET $API/me/investments` | `variationPercent: "10.00"`, `currentValue: "440.00"` | US6-1 |
| 12 | Comprar mais `"200.00"` (cotação de venda 400000.00 → 0.00050000 BTC); mudar `buy` para `200000.00` | posição: A vale `200.00`, B vale `100.00`, total `300.00` | — |
| 13 | `POST $API/me/redemptions` `{"amount":"250.00"}` | `201`: A liquidado inteiro (`200.00`); B parcial (`50.00`, `0.00025000` BTC); `reinvestment` com `0.00025000` BTC, `purchaseQuote: "400000.00"`, `investedAmount: "100.00"`, `purchasedAt` igual ao de B; `balance` +250.00 | US7, FR-019a, FR-020, FR-021 |
| 14 | `POST $API/me/redemptions` `{"amount":"50.01"}` | `422 REDEMPTION_EXCEEDS_AVAILABLE`, sem efeito | US7-6 |
| 15 | `GET $API/me/statement` | DEPOSIT, 2× PURCHASE, 2× REDEMPTION e 1× REINVESTMENT; as 3 últimas com o mesmo `requestId`; ordem decrescente | US8, FR-024, FR-024a |
| 16 | `GET $API/me/statement?from=2026-11-10&to=2026-11-01` | `422 INVALID_DATE_RANGE` | US8-3 |
| 17 | Criar a conta "Bia", depositar e comprar; `GET $API/market/daily-volume` | `boughtBtc` = soma das compras das duas contas; `soldBtc` = `0.00125000` (A inteiro + parte vendida de B; o reinvestimento não conta) | US9, FR-026 |
| 18 | Com o token de Bia: `GET $API/me/investments` e `GET $API/me/statement` | só dados de Bia | FR-033, SC-004 |
| 19 | Parar o stub (`docker compose stop quote-stub`); consultar a cotação logo em seguida e depois de 70 s | primeiro `200` com o `quotedAt` antigo; depois `503 QUOTE_UNAVAILABLE`; compra e resgate também `503` | US4-2/3, FR-012 |
| 20 | Após ≥ 20 min com o stub ativo: `GET $API/quotes/history` | pontos com `slotAt` em minutos múltiplos de 10, em ordem crescente, sem pontos inventados | US10, FR-027, FR-028 |

Conferências finais:
- **SC-002**: o saldo de Ana é igual a Σ DEPOSIT − Σ PURCHASE + Σ REDEMPTION do extrato.
- **SC-003**: o BTC em `GET /me/investments` é igual a Σ BTC de PURCHASE − Σ BTC de REDEMPTION.
- **FR-031**: com o Mailpit parado, um depósito continua retornando `201`; a mensagem é entregue quando o Mailpit volta (retry) ou acaba na DLQ `notifications.email.dlq` depois de esgotar as tentativas.
- **Logs**: `docker compose logs app` mostra JSON com `correlationId` e nenhuma senha ou token.

## 5. Limpeza

```bash
docker compose --profile stub down -v
```
