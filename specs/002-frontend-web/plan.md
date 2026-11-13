# Implementation Plan: Cliente Web do Investimento em Bitcoins

**Branch**: `002-frontend-web` (trabalho no branch git `frontend`) | **Date**: 2026-11-11 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-frontend-web/spec.md`

## Summary

Cliente web (pt-BR, celular e desktop) para a API da 001. Ele consome o contrato autoritativo [`openapi.yaml`](../001-bitcoin-investment-api/contracts/openapi.yaml) por referência, sem redefini-lo. A stack é **Kotlin Multiplatform com único alvo wasmJs** e **Compose Multiplatform + Material 3** (UI em canvas). A navegação usa `navigation-compose` ligada ao fragmento da URL. Para estado, ViewModel + StateFlow com UI stateless. O HTTP é Ktor com DTOs à mão e erros RFC 9457 convertidos em resultados tipados. Valores ficam em decimal exato (bignum encapsulado em value objects), as datas em America/Sao_Paulo e a sessão em `sessionStorage` + `BroadcastChannel`. O cliente só calcula **estimativas rotuladas** e **somas de exibição**. Toda regra de negócio continua na API (Princípio II).

O código fica num build Gradle separado em `web/`, com seis módulos que tornam a regra de dependência verificável pelo compilador (tela não alcança HTTP). A entrega é por um nginx próprio, não-root, que serve o bundle e faz proxy de `/api/` para o nginx da API (mesma origem). Os testes cobrem unidades (Node), fixtures de consumidor validadas contra o contrato e contra respostas reais no build da API, e E2E com Playwright for Java + axe-core.

**Risco principal**: o alvo web do Compose está em **Beta**, com acessibilidade inicial, e WCAG 2.1 AA é MUST. Por isso a implementação começa por um **spike go/no-go de acessibilidade** ([research W-00](./research.md#w-00-gate-de-acessibilidade--spike-go-no-go-obrigatório-bloqueia-a-implementação)) com critérios explícitos e uma alternativa registrada (Compose HTML sobre Kotlin/JS).

**Pendência bloqueante externa**: o contrato da 001 **ainda não contém `GET /me`** (FR-005), apesar de o pedido indicá-lo como emendado. Verificado em 2026-11-11 nos branches `frontend`, `main` e `bitcoin/api`. A proposta de delta está em [contracts/api-amendment-get-me.md](./contracts/api-amendment-get-me.md) e MUST ser aplicada na origem (001) antes da entrega da saudação com nome (US2). O restante não depende dela.

Detalhes: [research.md](./research.md) · Modelo: [data-model.md](./data-model.md) · Contratos de UI/teste: [contracts/](./contracts/) · Validação: [quickstart.md](./quickstart.md)

## Technical Context

**Language/Version**: Kotlin 2.4.21 (Kotlin/Wasm, alvo único `wasmJs` browser). O módulo `:e2e` usa Kotlin/JVM 21. Versões em [W-01](./research.md#w-01-versões-e-status-da-plataforma).

**Primary Dependencies**: Compose Multiplatform 1.12.2 (alvo web **Beta**), Material 3 1.9.0, navigation-compose 2.10.0 (`bindToBrowserNavigation`, **Experimental**), lifecycle-viewmodel 2.11.0, Ktor Client 3.6.1 (engine Js), kotlinx-serialization 1.13.0, kotlinx-coroutines 1.11.0, kotlinx-datetime 0.8.0 + `@js-joda/timezone` (npm), ionspin bignum 0.3.10. Qualidade: ktlint + detekt. Sem biblioteca de DI (composição manual).

**Storage**: nenhuma persistência de dados de negócio. Sessão em `sessionStorage` (token + `expiresAt`); rota e filtros no fragmento da URL.

**Testing**: `kotlin-test` + `kotlinx-coroutines-test` (`commonTest`, executados em Node/headless). Ktor `MockEngine` com fixtures de `web/contract-fixtures/`. No build da API, `WebClientFixturesContractTest` (MockMvc + validador OpenAPI). E2E com Playwright for Java 1.64.0 + JUnit 5 + axe-core 4.14.0.

**Target Platform**: navegadores atuais com WasmGC (Chrome/Edge, Firefox, Safari desktop/iOS, Chrome Android). Entrega em contêiner `nginxinc/nginx-unprivileged:1.27-alpine` com brotli/gzip pré-comprimidos.

**Project Type**: aplicação web (SPA) cliente de API REST existente. Segundo componente implantável do produto.

**Performance Goals**: SC-010 no perfil "4G típico" (9.000/1.500 kbps, 60 ms de RTT). Navegação no app: conteúdo ou carregamento ≤ 1 s e dados ≤ 3 s (p95). Primeira carga: `#boot` ≤ 1 s e app utilizável ≤ 6 s. Bundle ≤ 3,5 MB em brotli (W-15): HTML de carregamento estático, pré-compressão brotli, `preload` do `.wasm`/fonte, cache `immutable` em artefatos com hash. SC-005: cotação exibida ≤ 25 s atrás (polling de 10 s sobre o cache de 10 s da API).

**Constraints**: precisão exata sem `Double`/`Float` para valores (FR-038; detekt), fuso de Brasília independentemente do dispositivo (FR-037), token nunca em URL/cookie/log (FR-008), WCAG 2.1 AA (FR-046), 320–1920 px e zoom 200% sem rolagem horizontal (FR-044), envio único por confirmação (FR-043), nenhum texto fora do pt-BR (FR-048), CSP sem `unsafe-inline`/`unsafe-eval` (só `'wasm-unsafe-eval'`).

**Scale/Scope**: 8 user stories, 9 rotas, 9 ViewModels, 12 operações da API (11 existentes + `GET /me` pendente), 12 códigos de erro, 6 módulos Gradle.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution **v1.1.0** (emendada em 2026-11-11 para incluir o cliente web).

### Princípios

| Princípio | Como o plano atende | Status |
|-----------|---------------------|--------|
| **I. Arquitetura em Camadas** | Camadas do cliente em módulos Gradle separados: apresentação (`:ui`), estado/orquestração (`:state`), acesso a dados (`:data`) e núcleo puro (`:core`). `:ui` **não depende** de `:data`, então uma tela não consegue chamar a API (verificado pelo compilador). `:state` define as portas e `:data` as implementa. A composição fica só no `main()` do `:app` (`AppContainer`). [W-02](./research.md#w-02-módulos-gradle-e-regra-de-dependência) | ✅ |
| **II. Lógica de Negócio Isolada** | Saldo, conversões, FIFO, arredondamentos, período padrão do extrato e totais vêm da API e são exibidos sem alteração. O cliente calcula só: estimativas de compra/resgate **rotuladas** (FR-015/FR-023), a soma de exibição do grupo de resgate (FR-029) e a geometria do gráfico. A validação local é só de formato/UX e a API sempre revalida. A estimativa de resgate não reproduz FIFO (Assumptions). | ✅ |
| **III. Erro como Valor** | `ApiCall.execute` em `:data` é a única fronteira com exceções (Ktor/fetch). Ela converte Problem RFC 9457, transporte e respostas fora do contrato em `Outcome<ApiError, T>`/`Outcome<OperationError, T>` (`sealed`). Os `when` exaustivos em `:state`/`:ui` garantem pelo compilador que todos os códigos estão tratados. Todos os `code` do contrato estão mapeados para mensagens pt-BR ([contracts/error-messages.md](./contracts/error-messages.md)). O resultado incerto é um tipo próprio. | ✅ |
| **IV. TDD** | Red → Green → Refactor por tarefa. Unitários de estado/apresentação (`:core`, `:state`, `:ui`). Consumidor contra o contrato: fixtures validadas no build da API contra o `openapi.yaml` e contra respostas reais, com cobertura de todos os `code` ([W-12](./research.md#w-12-testes-de-consumidor-fixtures-conformes-ao-contrato)). E2E dos fluxos críticos (Playwright). CI com jobs `web` e `web-e2e`. | ✅ |
| **V. Simplicidade** | Sem DI, sem biblioteca de MVI, sem biblioteca de gráfico, sem gerador de cliente e sem geração de tema em runtime. Seis módulos, cada um justificado por uma regra verificável (W-02). Itens além do mínimo estão em Complexity Tracking. | ⚠️ ver Complexity Tracking |
| **VI. Agnosticismo Tecnológico** | Tecnologias escolhidas e justificadas só neste plan/research. `:core` e `:state` não dependem de Compose, Ktor nem de APIs do navegador. O navegador fica atrás de portas (W-09), o que também viabiliza o plano B do W-00. | ✅ |

### Engineering Standards

| Padrão | Atendimento |
|--------|-------------|
| SOLID / separação de responsabilidades | Um ViewModel por tela. Portas pequenas por necessidade (`QuoteGateway`, `StatementGateway`, …). Formato de entrada (`:core/format`), orquestração (`:state`), transporte (`:data`) e apresentação (`:ui`) separados. |
| Semântica HTTP | N/A para o cliente (não expõe API). O consumo respeita o contrato: GET para leitura e POST para ações. |
| Segurança e dados sensíveis | Token em `sessionStorage` + memória, nunca em URL, cookie ou log. Sem plugin de logging HTTP. `Session.toString()` mascarado. Sem segredos no bundle (só origem relativa). Sessão encerrada em `expiresAt` e em 401. CSP restritiva, `nosniff`, `no-referrer`, `frame-ancestors 'none'`. Mesma origem sem CORS. Nginx não-root. ([W-08](./research.md#w-08-sessão-sessionstorage--broadcastchannel), [W-15](./research.md#w-15-entrega-nginx-do-cliente-mesma-origem)) |
| Integridade de valores financeiros | `Money`, `BtcAmount`, `QuotePrice` e `VariationPercent` sobre decimal exato. Detekt proíbe `Double`/`Float`/`toDouble` em `:core`, `:state` e `:data` (detekt + `checkNoFloatingPoint`). Exibição com todos os dígitos e sem notação científica. Exceção controlada: `Float` só para pixel em `:ui/chart` (Complexity Tracking). |
| Contratos entre componentes | O contrato da 001 é referenciado e não copiado. As fixtures são exemplos validados contra ele no build da API. A emenda `GET /me` é proposta para aplicação **na origem** ([contracts/api-amendment-get-me.md](./contracts/api-amendment-get-me.md)). |
| **Acessibilidade (WCAG 2.1 AA)** | ✅ **GO no gate W-00 (T023, 2026-11-12)**, com as mitigações M1–M4 incorporadas ao `:app` ([W-00 Resultado](./research.md#w-00-resultado-2026-11-12--go-automatizado-t014t021-e-matriz-manual-t022-concluídos-decisão-registrada-t023)). Risco original: o alvo web da CMP é Beta e a acessibilidade é inicial (espelho DOM da árvore de semantics). Mitigações: (1) **gate go/no-go W-00** antes de qualquer User Story, com 12 critérios e matriz NVDA/VoiceOver/teclado/zoom/axe; (2) alternativa Compose HTML (Kotlin/JS) registrada, com camadas preparadas para a troca; (3) anúncios por `aria-live` real fora do canvas (W-09); (4) contraste verificado por teste (W-16); (5) axe em E2E + checklist manual por release (quickstart §6). **Se W-00 der NO-GO, o plan MUST ser revisado antes de continuar.** |
| Padronização e sintaxe | Código em inglês, pt-BR só em `strings.xml`. Pacote `com.kotlinbitcoins.web.<módulo>.<feature>`. ktlint + detekt no `./gradlew check` de `web/`. Estrutura de diretórios abaixo. ([W-19](./research.md#w-19-convenções-e-qualidade)) |

### Justificativa de componentes (ligação com a spec)

| Componente | Requisito atendido | Por quê |
|------------|--------------------|---------|
| Decimal exato (bignum) | FR-038, SC-002, FR-015 | A estimativa divide valores de escala 2 por cotações de escala 8 com resultado de 8 casas. Isso excede `Long` escalado. |
| kotlinx-datetime + `@js-joda/timezone` | FR-037 | No wasmJs, os fusos nomeados exigem a base do js-joda. |
| `BroadcastChannel` | FR-004 (nova aba), FR-006 (sair em todas), Edge Case "várias abas" | O `sessionStorage` é por aba. Só uma mensagem entre abas dá sessão compartilhada sem `localStorage`/cookie. |
| `AutoRefresher` + `PageVisibility` | FR-011, FR-012, FR-019, FR-047, SC-005 | Um mecanismo único e testável para cinco telas. |
| `DomAnnouncer` (aria-live fora do canvas) | FR-047, FR-046 | Garante o anúncio de mudanças de estado e de resultados independentemente do mapeamento Beta da CMP. |
| Nginx próprio + proxy | entrega; mesma origem (sem CORS); FR-008 | Separa a entrega do cliente da API e evita mudar a configuração da 001. |
| Fixtures + teste no build da API | Princípio IV (consumidor contra contrato), SC-003 | Prova a conformidade com o contrato sem copiá-lo. |
| Playwright + axe | SC-004, SC-007, SC-008, SC-009 | Os cenários exigem navegador real (multiaba, relógio, zoom, a11y). |

**Resultado do gate (pré-Phase 0)**: ✅ **PASS condicional**. Nenhuma violação MUST não justificada. A conformidade com WCAG 2.1 AA (MUST) depende do resultado do spike W-00, que é o primeiro item de execução. Os itens de Simplicidade estão justificados em Complexity Tracking.

**Re-check pós-Phase 1**: ✅ **PASS condicional** (mesmas condições).

**Gate W-00 (T023, 2026-11-12)**: ✅ **GO**. A matriz manual (T022) aprovou A1–A12 com as mitigações M1–M4 do spike, que passam a fazer parte do `:app` e são protegidas pelo E2E a cada atualização da CMP. A condição de acessibilidade do PASS condicional está satisfeita.
- O data-model não introduziu regra de negócio no cliente: as estimativas e a soma do grupo são rotuladas e documentadas (data-model §4.3, §5). "Disponível para resgate" é o `totalCurrentValue` da API, não um cálculo.
- O contrato de erros cobre os 12 `code` do enum, mais `NoConnection` e `Uncertain`.
- As rotas não carregam valores de formulário (FR-004), e o retorno pós-login aceita só rotas internas protegidas por construção (`AppRoute` tipado).
- Uma decisão surgiu no design e foi registrada: o resultado de operação é estado da mesma rota, não rota própria. Isso elimina por construção o reenvio por recarregamento/voltar (FR-043).
- Uma pendência externa foi identificada: `GET /me` não está no contrato da 001 (bloqueia só a saudação com nome).

## Project Structure

### Documentation (this feature)

```text
specs/002-frontend-web/
├── spec.md
├── plan.md                          # este arquivo
├── research.md                      # Phase 0 (W-00 … W-19; W-00 = gate de acessibilidade)
├── data-model.md                    # Phase 1
├── quickstart.md                    # Phase 1
├── contracts/                       # Phase 1: contratos do cliente (o contrato HTTP é o da 001, por referência)
│   ├── ui-routes.md                 # rotas, fragmentos, títulos, retorno pós-login
│   ├── error-messages.md            # code → mensagem/comportamento (FR-040/041)
│   ├── fixtures-manifest.md         # formato e verificações das fixtures de consumidor
│   └── api-amendment-get-me.md      # delta PENDENTE a aplicar na 001 (FR-005)
├── checklists/
│   └── requirements.md
└── tasks.md                         # Phase 2 (/speckit-tasks — ainda não criado)
```

### Source Code (repository root)

```text
web/                                          # build Gradle SEPARADO (não incluído no settings da raiz)
├── settings.gradle.kts                       # include(core, state, data, ui, app, e2e)
├── build.gradle.kts                          # ktlint + detekt em todos os módulos; toolchain JDK 21
├── gradle/libs.versions.toml                 # catálogo próprio (versões do W-01)
├── gradle/wrapper/ + gradlew                 # wrapper próprio
├── .editorconfig                             # regras ktlint (inclui exceção de nome para @Composable)
├── config/detekt/detekt.yml                  # regras gerais; detekt-precision.yml + checkNoFloatingPoint em :core/:state/:data (W-06)
├── Dockerfile                                # multi-stage: gradle → compress → brotli-module → nginx-unprivileged
├── docker/nginx.conf                         # MIME wasm, br/gzip static, cache, SPA fallback, CSP, proxy /api/
├── contract-fixtures/                        # fixtures de consumidor + manifest.json (W-12)
│
├── core/                                     # KMP wasmJs — puro (bignum, kotlinx-datetime)
│   └── src/
│       ├── commonMain/kotlin/com/kotlinbitcoins/web/core/
│       │   ├── money/                        # Money, BtcAmount, QuotePrice, VariationPercent (bignum encapsulado)
│       │   ├── format/                       # AmountInputParser, formatadores pt-BR, validadores de cadastro
│       │   ├── time/                         # BrasiliaTime, ApiInstant, CalendarDate
│       │   ├── estimate/                     # BuyEstimate, RedeemEstimate
│       │   ├── statement/                    # StatementGrouper, GroupedStatement, StatementFilter
│       │   ├── market/                       # ChartSeries (segmentos/lacunas), ChartSummary, TableRows
│       │   ├── navigation/                   # AppRoute, ReturnDestination
│       │   ├── model/                        # Profile, Quote, Position, Statement, resultados de operação…
│       │   └── error/                        # Outcome, ApiError, OperationError, AmountInputError
│       ├── commonTest/…
│       └── wasmJsTest/…                      # BrasiliaTime com base de fusos real
│
├── state/                                    # KMP wasmJs — ViewModels + portas (lifecycle-viewmodel, coroutines)
│   └── src/
│       ├── commonMain/kotlin/com/kotlinbitcoins/web/state/
│       │   ├── port/                         # *Gateway, SessionStorage, TabChannel, PageVisibility, Announcer, Clock, DocumentTitle, UserPreferences
│       │   ├── shared/                       # Loadable, QuoteState, AutoRefresher, OperationMachine
│       │   ├── session/                      # SessionManager, SessionGate, TabMessage
│       │   └── account/ dashboard/ deposit/ purchase/ investments/ redemption/ statement/ market/
│       └── commonTest/…                      # fakes das portas + TestScope
│
├── data/                                     # KMP wasmJs — Ktor + adaptadores do navegador
│   ├── build.gradle.kts                      # task generateContractFixtures → build/generated/…/commonTest
│   └── src/
│       ├── commonMain/kotlin/com/kotlinbitcoins/web/data/
│       │   ├── http/                         # HttpClientFactory, ApiCall (fronteira de erro), AuthHeaderPlugin, ProblemMapper
│       │   ├── dto/                          # DTOs à mão (strings para valores)
│       │   ├── mapper/                       # DTO → modelos de :core
│       │   └── gateway/                      # implementações das portas *Gateway
│       ├── wasmJsMain/kotlin/com/kotlinbitcoins/web/data/browser/
│       │   └── BrowserSessionStorage, BroadcastTabChannel, DocumentVisibility, DomAnnouncer, MediaPreferences, BrowserDocumentTitle
│       └── commonTest/…                      # MockEngine + ContractFixtures (gerado)
│
├── ui/                                       # KMP wasmJs + Compose (NÃO depende de :data)
│   └── src/
│       ├── commonMain/kotlin/com/kotlinbitcoins/web/ui/
│       │   ├── theme/                        # Color.kt (gerado da semente #F7931A), Type.kt (Inter + tnum), Theme.kt, ReducedMotion
│       │   ├── shell/                        # AppNavHost, navegação adaptativa (drawer/rail), SessionGate wiring, títulos
│       │   ├── components/                   # AmountField, LoadableBlock, QuoteBlock, ErrorBlock, PauseToggle, EstimateCard…
│       │   ├── chart/                        # PriceHistoryChart (Canvas; Float só aqui)
│       │   ├── shared/                       # ErrorMessages
│       │   └── account/ dashboard/ deposit/ purchase/ investments/ redemption/ statement/ market/   # *Screen stateless
│       ├── commonMain/composeResources/
│       │   ├── values/strings.xml            # todo texto pt-BR
│       │   └── font/                         # Inter (subset)
│       └── commonTest/…                      # ThemeContrastTest, ErrorMessagesTest
│
├── app/                                      # KMP wasmJs executável — borda
│   ├── webpack.config.d/devServerProxy.js    # dev: /api → localhost:8080
│   └── src/wasmJsMain/
│       ├── kotlin/com/kotlinbitcoins/web/app/
│       │   ├── main.kt                       # ComposeViewport, js-joda tz, leitura do hash, bindToBrowserNavigation, remoção do #boot
│       │   └── AppContainer.kt               # composição manual de tudo
│       └── resources/
│           ├── index.html                    # lang=pt-BR, #boot, regiões aria-live, preload
│           └── loading.css
│
└── e2e/                                      # JVM — Playwright for Java + axe-core (task e2eTest, fora do check)
    └── src/test/kotlin/com/kotlinbitcoins/web/e2e/
        ├── support/                          # fixtures de browser/contexto, ApiClient (verificação), Viewports, Axe
        └── auth/ operations/ dashboard/ statement/ market/ layout/ a11y/

web/spike-a11y/                               # W-00: descartável; removido após registrar o resultado

# Alterações fora de web/
docker-compose.yml                            # + serviço "web" (8081:8080, depends_on nginx healthy)
.github/workflows/ci.yml                      # + jobs "web" e "web-e2e"
bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/contract/WebClientFixturesContractTest.kt   # W-12
bootstrap/build.gradle.kts                    # + testImplementation do validador OpenAPI (única mudança no build da API)
```

**Structure Decision**: o cliente é um segundo componente implantável, com build próprio em `web/`, independente do build multi-módulo da API na raiz (que segue intacto, exceto pelo teste de contrato das fixtures). Os módulos correspondem às camadas do Princípio I para o cliente, mais o núcleo puro. Os pacotes por feature repetem os nomes das user stories em todos os módulos para facilitar a navegação. Os testes ficam no módulo do código testado. E2E fica em `:e2e`, que depende do stack em execução.

## Fases de execução (entrada para `/speckit-tasks`)

1. **Fase 0: Gate de acessibilidade (bloqueante)**: spike `web/spike-a11y/` e registro de "W-00 Resultado" no research. **NO-GO → revisar este plan.** Em paralelo, sem risco de retrabalho: build `web/` (catálogo, wrapper, ktlint/detekt) e `:core` (value objects, parsing, formatação), que independem da UI.
2. **Fundação**: `:core` completo; portas + `SessionManager` + `AutoRefresher` + `OperationMachine` em `:state`; `ApiCall`/`ProblemMapper`/DTOs + fixtures + `WebClientFixturesContractTest`; tema, shell de navegação, guarda, `index.html` e nginx/compose/CI.
3. **P1 (MVP)**: US1 → US2 (a saudação com nome depende da emenda `GET /me`) → US3 → US4.
4. **P2**: US5, US6, US7.
5. **P3**: US8.
6. **Fechamento**: E2E completo (SC-004/005/006/008/009), axe em todas as telas, checklist manual de SC-007 e medição de SC-010.

## Complexity Tracking

| Violation / acréscimo | Why Needed | Simpler Alternative Rejected Because |
|-----------------------|------------|--------------------------------------|
| 6 módulos Gradle no cliente | Torna verificáveis pelo compilador a regra "telas não chamam a API" (Princípio I) e a pureza do núcleo de precisão (FR-038). | 1–3 módulos deixam a regra só na convenção. Módulos por feature não adicionam nenhuma regra verificável. |
| Spike descartável `web/spike-a11y/` | Gate de risco do MUST de WCAG com plataforma Beta. | Construir direto e auditar no fim arrisca reescrever toda a `:ui`. |
| `Float` em `:ui/chart` (exceção à proibição de ponto flutuante) | `DrawScope` só aceita coordenadas `Float`. A conversão é geometria de pixel, não manipulação de valor. Rótulos, tabela e resumo usam decimal exato. | Não há API de desenho com decimal. A exceção fica confinada por `detekt-ui.yml` (`toFloat`/`toDouble` permitidos só em `ui/chart/ChartGeometry.kt`), e `:core`/`:state`/`:data` são cobertos por `detekt-precision.yml` + `checkNoFloatingPoint` (W-06). |
| Módulo brotli compilado no Dockerfile | SC-010: o bundle Compose/Skiko pesa vários MB, e brotli reduz cerca de 15–20% em relação ao gzip no `.wasm`. | Só gzip é mais simples, mas fica mais lento em rede móvel. Fica como fallback documentado se a compilação do módulo falhar. Imagens de terceiros com brotli trazem cadeia de suprimentos não oficial. |
| Protocolo `BroadcastChannel` para sessão entre abas | FR-004 (nova aba conectada) + clarificação "termina ao fechar o navegador". | `localStorage` sobrevive ao fechamento (viola US1-9). Cookie exigiria mudar o contrato da 001. |
| `DomAnnouncer` (DOM fora do canvas) | FR-047/FR-046 com plataforma Beta: `aria-live` real é a técnica mais compatível. | Depender só de `liveRegion` da CMP é incerto até o spike. Se o spike mostrar paridade, o adaptador continua fino. |
| Task de geração `ContractFixtures.kt` | Testes wasm (Node/navegador) não leem arquivos do repositório em runtime de forma portátil. | Duplicar o JSON em strings Kotlin à mão criaria duas fontes para as mesmas fixtures. |
