# Quickstart: validação do Cliente Web

**Feature**: `002-frontend-web` | **Plan**: [plan.md](./plan.md)

Guia para rodar e validar a feature de ponta a ponta. Os detalhes de decisão estão em [research.md](./research.md). As rotas estão em [contracts/ui-routes.md](./contracts/ui-routes.md) e as mensagens em [contracts/error-messages.md](./contracts/error-messages.md).

## 0. Pré-requisitos

- JDK 21, Docker com Compose v2 e um navegador atual (Chrome, Firefox ou Safari).
- `.env` na raiz (copiar de `.env.example`), o mesmo da 001.
- **Gate W-00 aprovado** (resultado registrado no research). Sem GO, só o spike é executável.
- **Emenda `GET /me`** aplicada na 001 para validar a saudação com nome ([contracts/api-amendment-get-me.md](./contracts/api-amendment-get-me.md)). Sem ela, o painel mostra "Olá!".

## 1. Verificações automatizadas (sem stack)

```bash
cd web
./gradlew check            # ktlint, detekt, testes de :core, :state, :data, :ui (Node/headless)
```

Esperado: build verde. Destaques que MUST passar: limites de precisão (SC-002), cobertura dos 12 `code` (SC-003), single-flight das operações (SC-004), `AutoRefresher` (FR-011), `SessionManager` (FR-004), contraste do tema (FR-045) e `ReturnDestination` (FR-007).

Na raiz (build da API), o teste das fixtures do cliente contra o contrato:

```bash
./gradlew :bootstrap:test --tests '*WebClientFixturesContractTest*'
```

## 2. Subir o stack completo

```bash
docker compose --profile stub up -d --build --wait   # API (2 réplicas) + nginx + quote-stub + web
```

Abrir `http://localhost:8081`. Esperado: o HTML de carregamento "Carregando…" aparece de imediato, seguido da tela de login.

Desenvolvimento só do cliente (com a API já em `localhost:8080`):

```bash
cd web && ./gradlew :app:wasmJsBrowserDevelopmentRun   # dev server em http://localhost:8082 com proxy /api → localhost:8080
```

## 3. Roteiro manual (MVP, P1)

| # | Passo | Esperado | Requisitos |
|---|-------|----------|------------|
| 1 | Abrir `#/painel` sem sessão | vai para `#/entrar?voltar=%2Fpainel` | FR-007 |
| 2 | Cadastro: enviar vazio | três mensagens junto aos campos, foco no nome | US1-2 |
| 3 | Cadastro válido ("Maria Souza") | chega ao painel com "Olá, Maria" | US1-1, US2-1 |
| 4 | Painel | saldo R$ 0,00, cotação com instante `dd/mm/aaaa hh:mm:ss (horário de Brasília)`, investimentos R$ 0,00 + convite | US2-1, US2-4 |
| 5 | Esperar ~10 s com foco no atalho "Comprar" | cotação muda e o foco continua no atalho | US2-2 |
| 6 | Pausar a atualização | estado visível "pausada", anunciado; a cotação para | US2-6 |
| 7 | Depositar `1.234,56` | "Depósito de R$ 1.234,56 realizado", novo saldo, data/hora | US3-1, US3-3 |
| 8 | Depositar `12,345`, `1e3`, `0` | mensagem de valor inválido, nada enviado (aba Rede vazia) | US3-2 |
| 9 | Comprar `400,00` → revisar | "Estimativa: … BTC", cotação de venda + instante e aviso de estimativa | US4-1 |
| 10 | Confirmar com clique duplo rápido | uma única compra no extrato | US4-8, SC-004 |
| 11 | Recarregar na tela de resultado | formulário vazio, nenhuma compra nova | FR-043 |
| 12 | Comprar acima do saldo | "Saldo insuficiente para esta compra." + saldo + "Depositar" | US4-4 |
| 13 | Recarregar no painel / abrir nova aba no endereço | continua conectado | US1-5 |
| 14 | "Sair" numa aba | a outra aba também vai ao login; voltar do navegador não mostra dados | US1-7, Edge Case |
| 15 | Fechar o navegador por completo e reabrir (sem "restaurar sessão") | pede login | US1-9 |

## 4. Roteiro manual (P2/P3)

| # | Passo | Esperado | Requisitos |
|---|-------|----------|------------|
| 16 | Meus investimentos com 2 compras | data, valor, cotação, variação com símbolo + texto + cor, valor atual e total | US5-1..3 |
| 17 | Resgatar: "Resgatar tudo" | campo preenchido com o disponível; a revisão avisa sobre queda de cotação | US6-5 |
| 18 | Resgate que liquida um total + um parcial | lista de liquidações (total/parcial) + reinvestimento com explicação | US6-3 |
| 19 | Extrato | grupo "Resgate — total R$ X" com reinvestimento "não altera o saldo" | US7-4 |
| 20 | Filtro `01/10/2026` a `09/11/2026`, depois recarregar | URL `#/extrato?de=2026-10-01&ate=2026-11-09`; o filtro se mantém | FR-027 |
| 21 | Filtro com inicial > final | mensagem junto às datas; o extrato anterior continua visível | US7-6 |
| 22 | Mercado | volume com 8 casas + data; gráfico com 2 linhas (contínua/círculo e tracejada/quadrado), lacuna sem ligação; "Ver dados em tabela" | US8-1..4 |
| 23 | Cotação indisponível: `docker compose stop quote-stub` e esperar > 60 s | painel: "Cotação indisponível no momento"; saldo, depósito, extrato e sair funcionam; ao religar, os valores voltam sozinhos | US2-3, SC-006 |

## 5. Ponta a ponta automatizado

```bash
docker compose --profile stub up -d --build --wait
cd web
./gradlew :e2e:e2eTest                       # Chromium, Firefox, WebKit
./gradlew :e2e:e2eTest -Pbrowsers=chromium   # rápido
```

Suítes (pacotes em `web/e2e/src/test/kotlin/.../`): `auth` (US1, retorno com filtro, destino externo recusado, multiaba, expiração via `clock` e 401 → SC-009), `operations` (US3, US4, US6, envio duplicado → SC-004, resultado incerto), `dashboard` (auto-refresh, pausa, aba oculta, SC-005, SC-006), `statement`, `market`, `layout` (320–1920 px e zoom 200% → SC-008) e `a11y` (axe em cada tela e estado → parte automatizável de SC-007). Relatórios ficam em `web/e2e/build/reports/`.

## 6. Checklist manual de acessibilidade (SC-007; antes de cada release)

Repetir a matriz do W-00 sobre o app completo: NVDA + Firefox/Chrome, VoiceOver + Safari (macOS e iOS), só teclado, zoom 200%, tema escuro e `prefers-reduced-motion`. Concluir cadastro, login, sair, depósito, compra, resgate, investimentos, extrato e mercado. Registrar o resultado em `specs/002-frontend-web/checklists/a11y-release.md`.

## 7. Entrega

```bash
docker build -t kotlinbitcoins-web web
curl -sI http://localhost:8081/ | grep -i content-security-policy        # inclui 'wasm-unsafe-eval'
curl -sI -H 'Accept-Encoding: br' http://localhost:8081/<app>.wasm | grep -iE 'content-type|content-encoding|cache-control'
# esperado: application/wasm, br, immutable
curl -s http://localhost:8081/api/v1/quotes/current -o /dev/null -w '%{http_code}\n'   # 401: proxy de mesma origem funcionando
```
