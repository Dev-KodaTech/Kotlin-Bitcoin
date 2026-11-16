---

description: "Lista de tarefas da feature 002-frontend-web"
---

# Tasks: Cliente Web do Investimento em Bitcoins

**Input**: documentos de design em `/specs/002-frontend-web/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: **obrigatórios**. A Constitution (Princípio IV) exige TDD (Red → Green → Refactor). O cliente web precisa de testes unitários de estado/apresentação, testes de consumidor contra o contrato e E2E dos fluxos críticos. Em cada fase, as tarefas de teste vêm antes da implementação e **MUST falhar** antes de a implementação começar.

**Organization**: tarefas agrupadas por user story (US1 … US8 da spec), para que cada uma seja implementada e testada de forma independente.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivos diferentes, sem dependência de tarefa incompleta)
- **[Story]**: user story da tarefa (US1 … US8)
- Toda tarefa traz o caminho exato do arquivo

## Path Conventions

Build Gradle separado em `web/` (plan.md → Project Structure). Pacote raiz `com.kotlinbitcoins.web.<módulo>`.

| Módulo | Código | Testes |
|--------|--------|--------|
| `:core` | `web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/` | `web/core/src/commonTest/kotlin/com/kotlinbitcoins/web/core/` (+ `wasmJsTest/`) |
| `:state` | `web/state/src/commonMain/kotlin/com/kotlinbitcoins/web/state/` | `web/state/src/commonTest/kotlin/com/kotlinbitcoins/web/state/` |
| `:data` | `web/data/src/commonMain/kotlin/com/kotlinbitcoins/web/data/` e `web/data/src/wasmJsMain/kotlin/com/kotlinbitcoins/web/data/browser/` | `web/data/src/commonTest/kotlin/com/kotlinbitcoins/web/data/` |
| `:ui` | `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/` + `web/ui/src/commonMain/composeResources/` | `web/ui/src/commonTest/kotlin/com/kotlinbitcoins/web/ui/` |
| `:app` | `web/app/src/wasmJsMain/kotlin/com/kotlinbitcoins/web/app/` + `web/app/src/wasmJsMain/resources/` | — (coberto por E2E) |
| `:e2e` | — | `web/e2e/src/test/kotlin/com/kotlinbitcoins/web/e2e/` |
| fixtures | `web/contract-fixtures/` | validadas em `bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/contract/WebClientFixturesContractTest.kt` |

## Regras transversais (valem para todas as tarefas)

- **Camadas (Princípio I)**: `:ui` MUST NOT depender de `:data` nem de Ktor. Toda chamada à API passa por uma porta definida em `:state/port` e implementada em `:data/gateway`. A composição acontece só em `web/app/.../AppContainer.kt`.
- **Regra de negócio (Princípio II)**: o cliente só calcula estimativas **rotuladas** ("Estimativa") e somas de exibição (total do grupo de resgate). Saldo, totais, disponível para resgate, variação e período padrão vêm da API sem alteração.
- **Erros (Princípio III)**: exceções só dentro de `web/data/.../http/ApiCall.kt` e dos adaptadores `browser/`. Tudo que sai de `:data` é `Outcome<ApiError, T>` ou `Outcome<OperationError, T>` (data-model §3). `when` sobre esses tipos é sempre exaustivo, sem `else`.
- **Precisão (FR-038)**: valores só em `Money`, `BtcAmount`, `QuotePrice` e `VariationPercent`. `Double`/`Float`/`toDouble`/`toFloat` são proibidos em `:core`, `:state` e `:data` (`detekt-precision.yml` + `checkNoFloatingPoint`, T006). Em `:ui`, `Float` é usado só como tipo do Compose (dimensões, pesos, cores), e a conversão de um valor financeiro para `Float` só existe em `ui/chart/ChartGeometry.kt` (coordenadas de pixel), garantido por `detekt-ui.yml` (T006).
- **Tempo**: só pela porta `Clock` (`:state`) e por `BrasiliaTime` (`:core`). Toda data/hora exibida está em America/Sao_Paulo, com o rótulo "(horário de Brasília)".
- **Segurança (FR-008)**: o token nunca aparece em URL, cookie, `localStorage`, `println`/console ou `toString()`. Não há plugin `Logging` do Ktor.
- **Textos**: todo texto visível ou anunciado fica em `web/ui/src/commonMain/composeResources/values/strings.xml` (pt-BR). Código em inglês. Telas `XxxScreen`, ViewModels `XxxViewModel`, estados `XxxUiState`, DTOs `XxxDto`, portas `XxxGateway`.
- **Acessibilidade (FR-046)**: todo campo tem rótulo visível e associado. Erros ficam nas semantics do campo (`error(...)`). Cada tela tem um `h1` (`semantics { heading() }`) e define `document.title` (contracts/ui-routes.md). Depois de um envio, o foco vai ao primeiro erro ou ao título do resultado. Atualizações periódicas não usam `liveRegion`. Anúncios só pela porta `Announcer`.
- **E2E**: seletores e técnica de interação seguem o resultado do spike, registrado em research.md "W-00 Resultado" (T023). Cada teste E2E de tela inclui uma auditoria axe (`Axe.assertNoViolations(page)`).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: build Gradle separado em `web/`, módulos vazios e qualidade de código.

- [X] T001 Criar `web/settings.gradle.kts` com `rootProject.name = "kotlin-bitcoins-web"`, `pluginManagement` (repositórios `gradlePluginPortal()`, `google()`, `mavenCentral()`), `dependencyResolutionManagement` com `RepositoriesMode.FAIL_ON_PROJECT_REPOS` (`google()`, `mavenCentral()`) e `include("core", "state", "data", "ui", "app", "e2e", "spike-a11y")`. Criar os diretórios `src/commonMain/kotlin/com/kotlinbitcoins/web/<módulo>/` e `src/commonTest/kotlin/com/kotlinbitcoins/web/<módulo>/` de `core`, `state`, `data` e `ui`, e `web/app/src/wasmJsMain/` e `web/e2e/src/test/kotlin/com/kotlinbitcoins/web/e2e/`
- [X] T002 Gerar o Gradle wrapper próprio em `web/` (`web/gradlew`, `web/gradlew.bat`, `web/gradle/wrapper/gradle-wrapper.properties`), na mesma versão de Gradle do wrapper da raiz, se compatível com KGP 2.4.21 e com o plugin Compose 1.12.2. Caso contrário, usar a mínima suportada e registrar em research.md W-01
- [X] T003 Criar `web/gradle/libs.versions.toml` com as versões de research.md W-01: Kotlin 2.4.21; Compose Multiplatform 1.12.2 (plugin `org.jetbrains.compose` + `org.jetbrains.kotlin.plugin.compose`); `org.jetbrains.compose.material3:material3` 1.9.0; `org.jetbrains.androidx.navigation:navigation-compose` 2.10.0; `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel` e `lifecycle-viewmodel-compose` 2.11.0; Ktor 3.6.1 (`ktor-client-core`, `ktor-client-js`, `ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json`, `ktor-client-mock`); kotlinx-serialization-json 1.13.0 (+ plugin `org.jetbrains.kotlin.plugin.serialization`); kotlinx-coroutines-core/-test 1.11.0; kotlinx-datetime 0.8.0; `com.ionspin.kotlin:bignum` 0.3.10; Playwright 1.64.0; `com.deque.html.axe-core:playwright` 4.14.0; JUnit 5 e AssertJ (mesmas versões da raiz); plugins ktlint-gradle 14.2.0 (engine 1.8.0) e detekt 1.23.8 (com a mesma nota de classpath Kotlin do catálogo da raiz). Versão fixada do npm `@js-joda/timezone`
- [X] T004 Configurar `web/build.gradle.kts`: plugins declarados com `apply false`; ktlint e detekt aplicados a todos os subprojetos (config `web/config/detekt/detekt.yml`, `buildUponDefaultConfig = true`); `check` de cada subprojeto dependendo de `ktlintCheck` e `detekt`; toolchain JDK 21
- [X] T005 [P] Criar `web/.editorconfig` com as mesmas regras do `.editorconfig` da raiz (`ktlint_code_style = ktlint_official`, indentação 4, `max_line_length = 120`) e `ktlint_function_naming_ignore_when_annotated_with = Composable`
- [X] T006 [P] Criar `web/config/detekt/detekt.yml` (`maxIssues: 0`, regras de complexidade ativas, `FunctionNaming.ignoreAnnotated: [Composable]`, `style.ForbiddenMethodCall` para `kotlin.text.format` e `kotlin.io.println` em todos os módulos) e `web/config/detekt/detekt-precision.yml` (`style.ForbiddenMethodCall` para `kotlin.Number.toDouble`, `kotlin.Number.toFloat`, `kotlin.text.toDouble` e `kotlin.text.toFloat`), aplicado só a `:core`, `:state` e `:data`. Registrar em `web/build.gradle.kts` a tarefa `checkNoFloatingPoint`, ligada ao `check`: ela remove comentários (`//…`, `/* … */`) e literais de string antes de procurar e falha se aparecer uso de tipo (`:\s*(Double|Float)\b`, `<(Double|Float)\b`, `\b(Double|Float)\s*\(`, `as\s+(Double|Float)\b`) ou literal `\b\d+(\.\d+)?[fF]\b` em `src/*Main` desses três módulos (research.md W-06). Criar também `web/config/detekt/detekt-ui.yml`, aplicado só a `:ui`: `style.ForbiddenMethodCall` para `kotlin.text.toFloat` e `kotlin.text.toDouble`, com `excludes` só para `**/ui/chart/ChartGeometry.kt`. Assim, a conversão de um valor financeiro (via `toPlainString()`) para `Float` fica confinada pelo compilador de regras a esse arquivo; o restante do `:ui` usa `Float` livremente como tipo do Compose
- [X] T007 [P] Configurar `web/core/build.gradle.kts`: `kotlin("multiplatform")` + `kotlin("plugin.serialization")`, alvo único `wasmJs { nodejs() }` (testes em Node), `commonMain` com `bignum`, `kotlinx-datetime` e `kotlinx-serialization-core` (rotas `@Serializable`), `wasmJsTest` com `implementation(npm("@js-joda/timezone", <versão>))`, `commonTest` com `kotlin("test")`. Nenhuma outra dependência (`:core` é puro, W-02)
- [X] T008 [P] Configurar `web/state/build.gradle.kts`: KMP `wasmJs { nodejs() }`; `commonMain` com `api(project(":core"))`, `lifecycle-viewmodel` e `kotlinx-coroutines-core`; `commonTest` com `kotlin("test")` e `kotlinx-coroutines-test`. Sem Compose e sem Ktor
- [X] T009 [P] Configurar `web/data/build.gradle.kts`: KMP `wasmJs { browser(); nodejs() }`; `commonMain` com `implementation(project(":state"))`, `ktor-client-core`, `ktor-client-js`, `ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json` e `kotlinx-serialization-json`; `commonTest` com `ktor-client-mock`, `kotlin("test")` e `kotlinx-coroutines-test`. Sem Compose
- [X] T010 [P] Configurar `web/ui/build.gradle.kts`: KMP `wasmJs { browser() }` + plugins Compose; `commonMain` com `implementation(project(":state"))`, `compose.runtime`, `compose.foundation`, `compose.ui`, `compose.components.resources`, `material3`, `navigation-compose` e `lifecycle-viewmodel-compose`; `commonTest` com `kotlin("test")`; `compose.resources { packageOfResClass = "com.kotlinbitcoins.web.ui.resources" }`. Testes do `:ui` rodam no Chrome headless pelo executor de testes de navegador da KGP (`browser { testTask { useKarma { useChromeHeadless() } } }`). Se a KGP 2.4.21 indicar outro executor para wasmJs, usar o indicado e registrar em research.md W-18. **Proibido** depender de `:data` ou de Ktor (W-02)
- [X] T011 [P] Configurar `web/app/build.gradle.kts`: KMP `wasmJs { browser { commonWebpackConfig { outputFileName = "app.js" } }; binaries.executable() }`, dependências de `:core`, `:state`, `:data` e `:ui`, mais `implementation(npm("@js-joda/timezone", <versão>))`
- [X] T012 [P] Configurar `web/e2e/build.gradle.kts`: `kotlin("jvm")`, JUnit 5, AssertJ, Playwright e axe-core Playwright; desabilitar a tarefa `test` padrão e registrar a tarefa `e2eTest` (`Test`, JUnit Platform) com `systemProperty("e2e.baseUrl", findProperty("baseUrl") ?: "http://localhost:8081")`, `systemProperty("e2e.apiUrl", findProperty("apiUrl") ?: "http://localhost:8081/api/v1")` e `systemProperty("e2e.browsers", findProperty("browsers") ?: "chromium,firefox,webkit")`. A tarefa `e2eTest` **não** entra no `check`
- [X] T013 [P] Criar `web/.gitignore` com `build/`, `.gradle/` e `.kotlin/`. `kotlin-js-store/` (yarn.lock) **é** versionado

**Checkpoint**: `cd web && ./gradlew check` executa com sucesso nos módulos vazios. `./gradlew check` na raiz continua igual.

---

## Phase 2: Gate de acessibilidade — spike go/no-go (BLOQUEANTE)

**Purpose**: decidir se Compose Multiplatform web (Beta) atende WCAG 2.1 AA (research.md W-00). **Nenhuma tarefa de `:ui`, `:app` ou de user story começa antes de T023.** As tarefas de `:core`, `:state` e `:data` da Phase 3 (T024–T080) MAY rodar em paralelo a esta fase, porque não dependem da UI.

- [X] T014 Configurar `web/spike-a11y/build.gradle.kts` (KMP `wasmJs { browser(); binaries.executable() }`, Compose, Material 3, navigation-compose) e compilar `./gradlew :spike-a11y:wasmJsBrowserDistribution`. Confirmar a compatibilidade Kotlin 2.4.21 ↔ CMP 1.12.2 ↔ bignum 0.3.10 (incluir um uso trivial de `BigDecimal`) e kotlinx-datetime + `@js-joda/timezone` (`TimeZone.of("America/Sao_Paulo")`). Registrar o resultado em `specs/002-frontend-web/research.md` (W-01, W-06, W-07)
- [X] T015 [P] Criar `web/spike-a11y/src/wasmJsMain/resources/index.html` (`<html lang="pt-BR">`, `<div id="live-polite" aria-live="polite">` e `<div id="live-assertive" role="alert">` visualmente ocultos, sem script ou estilo inline) e `web/spike-a11y/webpack.config.d/devServer.js` (`config.devServer.port = 8082` — a 8080 é do nginx da API — e o cabeçalho CSP exato de research.md W-15)
- [X] T016 [P] Criar `web/spike-a11y/src/wasmJsMain/kotlin/com/kotlinbitcoins/web/spike/LoginSpike.kt`: campos e-mail e senha com rótulo visível, botão "Entrar" e mensagem "E-mail ou senha incorretos." em região de alerta ao enviar
- [X] T017 [P] Criar `web/spike-a11y/src/wasmJsMain/kotlin/com/kotlinbitcoins/web/spike/RegisterFormSpike.kt`: nome, e-mail e senha, cada um com erro próprio (`semantics { error(msg) }` + texto abaixo do campo). Ao enviar inválido, o foco vai ao primeiro campo inválido. Inclui um campo de valor com `KeyboardType.Decimal` (critério A11)
- [X] T018 [P] Criar `web/spike-a11y/src/wasmJsMain/kotlin/com/kotlinbitcoins/web/spike/StatementTableSpike.kt`: 30 linhas no formato do extrato (tipo, valor, BTC, cotação, data/hora), com um grupo de resgate em `LazyColumn`. Duas variantes alternáveis: (a) layout tabular com semantics de cabeçalho/célula, se a CMP 1.12 expuser; (b) cartões com `semantics(mergeDescendants = true)` e texto completo (fallback do W-14)
- [X] T019 [P] Criar `web/spike-a11y/src/wasmJsMain/kotlin/com/kotlinbitcoins/web/spike/LiveRegionSpike.kt`: botões que alternam "Cotação indisponível no momento"/"Cotação restabelecida" por duas técnicas (`Modifier.semantics { liveRegion = LiveRegionMode.Polite }` e escrita no `#live-polite` via interop) e um contador que muda a cada 10 s **sem** anúncio
- [X] T020 Criar `web/spike-a11y/src/wasmJsMain/kotlin/com/kotlinbitcoins/web/spike/main.kt`: `ComposeViewport`, `document.title` por tela, três destinos type-safe com `navController.bindToBrowserNavigation(getBackStackEntryRoute = …)` gerando `#/login`, `#/form` e `#/tabela?filtro=x`, e leitura de `window.location.hash` na inicialização. Isso valida W-04: voltar/avançar, recarregar e parâmetro no fragmento
- [X] T021 Criar `web/e2e/src/test/kotlin/com/kotlinbitcoins/web/e2e/spike/SpikeA11yE2ETest.kt`: para as três telas, rodar axe (tags `wcag2a`, `wcag2aa`, `wcag21a`, `wcag21aa`), registrar o snapshot de acessibilidade do Playwright (`page.accessibility`/`ariaSnapshot`), testar seletores `getByRole`/`getByLabel`, a navegação só por Tab/Enter, a ausência de violações de CSP no console, voltar/avançar/recarregar preservando `#/tabela?filtro=x` e o viewport de 320 px e 640 px com `deviceScaleFactor` 2 (zoom 200%). Executar contra o spike em `wasmJsBrowserDevelopmentRun` com `./gradlew :e2e:e2eTest -PbaseUrl=http://localhost:8082 --tests '*SpikeA11y*'`. O teste é autossuficiente (não usa o suporte de T101)
- [X] T022 Executar a matriz manual de research.md W-00 (NVDA + Firefox/Chrome, VoiceOver + Safari/Chrome macOS, VoiceOver + Safari iOS, só teclado, zoom 200%, teclado virtual Android/iOS) sobre o spike e registrar, em `specs/002-frontend-web/research.md`, a seção "W-00 Resultado": GO/NO-GO por critério A1–A12, versões de navegador/leitor, evidências, técnica de anúncio escolhida (W-09), variante de tabela escolhida (W-14), estratégia de seletores do E2E (W-17) e o idioma dos textos internos dos componentes Material 3 lidos pelo leitor de tela (por exemplo, botões de fechar menu/drawer, `TopAppBar`, `TextField` com erro). Se algum vier em inglês, registrar a sobrescrita por `semantics { contentDescription/stateDescription }` com texto de `strings.xml` como regra para o `:ui` (FR-048)
- [X] T023 Decisão do gate, registrada em `specs/002-frontend-web/plan.md` (Constitution Check → Acessibilidade): **GO** → seguir. **NO-GO parcial aceito** → registrar a mitigação em Complexity Tracking do plan.md. **NO-GO** → PARAR, revisar plan.md/research.md com `/speckit-plan` (plano B Compose HTML/Kotlin-JS, W-00) e regenerar este tasks.md antes de continuar

**Checkpoint**: gate W-00 decidido e registrado. Só com GO (ou NO-GO parcial aceito) as fases de UI continuam.

---

## Phase 3: Foundational (Blocking Prerequisites)

**Purpose**: núcleo de precisão e formatação, portas, sessão, auto-refresh, fronteira HTTP, fixtures, tema, shell de navegação, `index.html`, entrega e suporte de E2E.

**⚠️ CRITICAL**: nenhuma user story começa antes desta fase. As seções `:core`, `:state` e `:data` MAY começar durante a Phase 2. As seções `:ui`, `:app`, entrega e E2E dependem de T023 = GO.

### Testes de `:core` (escrever primeiro, devem falhar)

- [X] T024 [P] Teste de `Money` em `web/core/src/commonTest/kotlin/com/kotlinbitcoins/web/core/money/MoneyTest.kt`: `parseApi` aceita só `^\d+\.\d{2}$`; `"99999999999.99"` → `"R$ 99.999.999.999,99"`; `"0.01"` → `"R$ 0,01"`; o separador após `R$` é U+00A0; `toApiString()` de `1234,56` = `"1234.56"`; `plus` é exato; não há notação científica em nenhum caso; `"1e3"`, `"-1.00"` e `"1.0"` falham
- [X] T025 [P] Teste de `BtcAmount` em `web/core/src/commonTest/kotlin/com/kotlinbitcoins/web/core/money/BtcAmountTest.kt`: `parseApi` aceita só `^\d+\.\d{8}$`; `"0.00000001"` → `"0,00000001 BTC"`; `"21000000.00000000"` → `"21.000.000,00000000 BTC"`; `isBelowOneSatoshi()` é verdadeiro só para zero
- [X] T026 [P] Teste de `QuotePrice` em `web/core/src/commonTest/kotlin/com/kotlinbitcoins/web/core/money/QuotePriceTest.kt`: `parseApi` aceita `^\d+(\.\d{1,8})?$` e exige > 0; `"400000"` → `"R$ 400.000,00"`; `"400000.5"` → `"R$ 400.000,50"`; `"400000.12345"` → `"R$ 400.000,12345"`; `"400000.12345678"` → `"R$ 400.000,12345678"` (todas as casas, mínimo 2, sem arredondar)
- [X] T027 [P] Teste de `VariationPercent` em `web/core/src/commonTest/kotlin/com/kotlinbitcoins/web/core/money/VariationPercentTest.kt`: `parseApi` aceita `^-?\d+\.\d{2}$`; `"10.00"` → `"+10,00%"` com `Trend.Up`; `"-5.00"` → `"−5,00%"` (sinal U+2212) com `Trend.Down`; `"0.00"` → `"0,00%"` com `Trend.Flat`
- [X] T028 [P] Teste de `AmountInputParser` em `web/core/src/commonTest/kotlin/com/kotlinbitcoins/web/core/format/AmountInputParserTest.kt` (tabela de research.md W-11): aceita `"1234,56"`, `"1.234,56"`, `"1234"`, `"1.234"`, `" 100,00 "` e `"0,01"`; recusa `""` (`Empty`), `"0"` e `"0,00"` (`NotPositive`), `"-5"`, `"R$ -5"`, `"1,234.56"`, `"1e3"`, `"1.23,4"`, `"abc"` e um valor com mais de 17 dígitos inteiros (`InvalidFormat`), `"12,345"` (`TooManyDecimals`)
- [X] T029 [P] Teste de `BrasiliaTime`/`ApiInstant` em `web/core/src/commonTest/kotlin/com/kotlinbitcoins/web/core/time/BrasiliaTimeTest.kt`: `"2026-11-09T08:10:00-03:00"` → `"09/11/2026 08:10"`; com segundos → `"09/11/2026 08:10:00"`; `"2026-11-09T11:10:00Z"` → `"09/11/2026 08:10"` (fuso do dispositivo irrelevante); instante inválido falha
- [X] T030 [P] Teste de `CalendarDate` em `web/core/src/commonTest/kotlin/com/kotlinbitcoins/web/core/time/CalendarDateTest.kt`: `parseUserInput("01/10/2026")` → `toIso()` = `"2026-10-01"`; `"31/02/2026"`, `"1/10/2026"` e `"2026-10-01"` são recusados como entrada; `parseIso` faz a ida e volta
- [X] T031 [P] Teste da base de fusos real em `web/core/src/wasmJsTest/kotlin/com/kotlinbitcoins/web/core/time/BrasiliaTimeZoneDataTest.kt`: referencia `@JsModule("@js-joda/timezone") external object JsJodaTimeZoneModule` e verifica que `TimeZone.of("America/Sao_Paulo")` não lança exceção (W-07)
- [X] T032 [P] Teste de `AppRoute` em `web/core/src/commonTest/kotlin/com/kotlinbitcoins/web/core/navigation/AppRouteTest.kt`: ida e volta `toFragment`/`parse` para cada linha da tabela de `contracts/ui-routes.md`; `""`, `"#"`, `"#/"` e `"#/desconhecida"` → `Dashboard`; `#/extrato?de=2026-10-01&ate=2026-11-09` → `Statement(Range)`; só `de` ou data inválida → `Statement(Default)` com entrada preservada para exibir o erro; parâmetros extras são ignorados; `Statement("2026-10-01", "2026-11-09").toFilter()` → `Range`, `Statement(null, null).toFilter()` → `Default`, `Statement("2026-10-01", null).toFilter()` e `Statement("2026-11-09", "2026-10-01").toFilter()` → `FilterError.InvalidRange`
- [X] T033 [P] Teste de `ReturnDestination` em `web/core/src/commonTest/kotlin/com/kotlinbitcoins/web/core/navigation/ReturnDestinationTest.kt`: aceita `"/extrato?de=2026-10-01&ate=2026-11-09"` e `"/investimentos"`; resolve para `Dashboard` os casos `"https://evil.example"`, `"//evil.example"`, `"javascript:alert(1)"`, `"/entrar"`, `"/cadastro"`, `"../"`, `""`, `null` e valor duplamente codificado

### Implementação de `:core`

- [X] T034 Criar `web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/error/Outcome.kt` (`Success`/`Failure`, `map`, `flatMap`, `fold`) e, em `web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/error/`, `ApiError.kt` (`Validation(fieldErrors: List<FieldError(field, message)>)`, `InvalidAmount`, `EmailAlreadyInUse`, `InvalidCredentials`, `Unauthorized`, `InsufficientBalance`, `RedemptionExceedsAvailable`, `InvalidDateRange`, `QuoteUnavailable`, `ServiceUnavailable`, `Unexpected`, `NoConnection`), `OperationError.kt` (`Rejected(error: ApiError)`, `Uncertain`) e `AmountInputError.kt` (`Empty`, `InvalidFormat`, `TooManyDecimals`, `NotPositive`), conforme data-model §1 e §3
- [X] T035 Criar `web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/money/DecimalSupport.kt` (`internal`: criação de `BigDecimal` a partir de string validada, `toPlainString`, divisão com `DecimalMode(…, RoundingMode.FLOOR, scale = 8)`) e `PtBrNumberFormat.kt` (`internal`: agrupamento de milhar com `.` e vírgula decimal sobre `toPlainString`, sem `Intl`/`String.format`)
- [X] T036 [P] Implementar `Money` em `web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/money/Money.kt` (construtor privado, "≥ 0, escala exata 2", `parseApi`, `toApiString`, `plus`, `compareTo`, `isZero`, `ZERO`, `format()` → `R$ 1.234,56`)
- [X] T037 [P] Implementar `BtcAmount` em `web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/money/BtcAmount.kt` ("≥ 0, escala exata 8", `parseApi`, `plus`, `isBelowOneSatoshi`, `format()` → `0,00100000 BTC`)
- [X] T038 [P] Implementar `QuotePrice` em `web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/money/QuotePrice.kt` ("> 0, escala de 0 a 8 preservada", `parseApi`, `compareTo`, `format()` com todas as casas e mínimo 2)
- [X] T039 [P] Implementar `VariationPercent` e `Trend` em `web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/money/VariationPercent.kt` ("escala 2, com sinal", `format()` com `+`, `−` U+2212 ou sem sinal)
- [X] T040 Implementar `AmountInputParser` em `web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/format/AmountInputParser.kt` (regex de W-11, até 17 dígitos inteiros, `Outcome<AmountInputError, Money>` exigindo > 0)
- [X] T041 Implementar `BrasiliaTime.kt`, `ApiInstant.kt` e `CalendarDate.kt` em `web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/time/` (`TimeZone.of("America/Sao_Paulo")`, formatos `dd/MM/yyyy HH:mm` e `dd/MM/yyyy HH:mm:ss` via `LocalDateTime.Format`, `CalendarDate` com `parseUserInput("dd/mm/aaaa")`, `parseIso`, `toIso`, `compareTo`)
- [X] T042 Implementar `StatementFilter` (`Default` | `Range(from: CalendarDate, to: CalendarDate)`, com validação "ambas obrigatórias juntas, datas válidas, from ≤ to" → `FilterError.InvalidRange`) em `web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/statement/StatementFilter.kt`
- [X] T043 Implementar `AppRoute` (`@Serializable` sealed: `Login(returnTo: String?)`, `Register`, `Dashboard`, `Deposit`, `Purchase`, `Investments`, `Redemption`, `Statement(from: String?, to: String?)`, `Market`; `isProtected`, `toFragment()`, `parse(fragment)`; `Statement` guarda os parâmetros brutos do fragmento e expõe `toFilter(): Outcome<FilterError, StatementFilter>`, conforme data-model §7) em `web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/navigation/AppRoute.kt`, seguindo `contracts/ui-routes.md`
- [X] T044 Implementar `ReturnDestination.resolve(raw: String?): AppRoute` em `web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/navigation/ReturnDestination.kt` (aceita só rota **protegida** reconhecida por `AppRoute.parse`; senão `Dashboard`)
- [X] T045 [P] Criar os modelos compartilhados em `web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/model/`: `Quote.kt` (`buyPrice`, `sellPrice`, `quotedAt: ApiInstant`), `Balance.kt` (`balance: Money`), `Position.kt` (`quote: Quote`, `totalCurrentValue: Money`, `investments: List<InvestmentPosition>`) e `InvestmentPosition.kt` (`id`, `purchasedAt`, `investedAmount`, `purchaseQuote`, `btcAmount`, `variationPercent`, `currentValue`), conforme data-model §2

### Testes de `:state` (escrever primeiro, devem falhar)

- [X] T046 Criar os fakes em `web/state/src/commonTest/kotlin/com/kotlinbitcoins/web/state/support/`: `FakeSessionStorage.kt`, `FakeTabChannel.kt` (pares de canais conectados para simular abas), `FakePageVisibility.kt`, `FakeAnnouncer.kt` (registra anúncios polite/assertive), `TestClock.kt` (ligado ao `TestScope`) e `FakeDocumentTitle.kt`
- [X] T047 [P] Teste de `AutoRefresher` em `web/state/src/commonTest/kotlin/com/kotlinbitcoins/web/state/shared/AutoRefresherTest.kt` (tempo virtual): executa ao iniciar; repete 10 s após o **término** do ciclo anterior (sem sobreposição com um refresh lento de 15 s); com página oculta não executa; ao ficar visível executa **imediatamente**; com pausa não executa; ao retomar executa imediatamente
- [X] T048 [P] Teste de `OperationMachine` em `web/state/src/commonTest/kotlin/com/kotlinbitcoins/web/state/shared/OperationMachineTest.kt` (data-model §4.2): `onConfirm` só em `Reviewing` (ou `Editing` válido no modo sem revisão); passa para `Submitting` antes de chamar o gateway; 5 `onConfirm` seguidos → **1** chamada; `Rejected` com `QuoteUnavailable`/`ServiceUnavailable` → `onRetry` volta a `Reviewing` sem enviar; `Uncertain` não aceita `onRetry`; `Succeeded` não mantém a estimativa; `onNewOperation` volta a `Editing` vazio
- [X] T049 [P] Teste de `SessionManager` em `web/state/src/commonTest/kotlin/com/kotlinbitcoins/web/state/session/SessionManagerTest.kt` (research.md W-08): restaura do storage se `now < expiresAt`; sem storage envia `request` e aceita `share` em até 300 ms; sem resposta → `Anonymous(None)`; responde `request` quando `Active`; expira exatamente em `expiresAt` (timer) e também quando `now ≥ expiresAt` em `tokenForRequest()`; `onUnauthorized()` → `Anonymous(Expired)` + mensagem `expired`; `logout()` → `Anonymous(LoggedOut)` + mensagem `logout` + storage limpo; mensagens `logout`/`expired` recebidas → mesmos estados; login → `share` publicado; `Session.toString()` não contém o token
- [X] T050 [P] Teste de `SessionGate` em `web/state/src/commonTest/kotlin/com/kotlinbitcoins/web/state/session/SessionGateTest.kt`: rota protegida sem sessão → `Login(returnTo = fragmento da rota)`; `Login`/`Register` com sessão → `Dashboard`; evento `Expired` em `Statement(Range)` → `Login(returnTo = "/extrato?de=…&ate=…")` com banner `SessionExpired`; `LoggedOut` → `Login` sem `returnTo` e sem banner; enquanto `Resolving`, nenhuma rota protegida é exibida
- [X] T051 [P] Teste de `QuoteAvailabilityTracker` em `web/state/src/commonTest/kotlin/com/kotlinbitcoins/web/state/shared/QuoteAvailabilityTrackerTest.kt`: anuncia (polite) "Cotação indisponível no momento" só na transição `Available → Unavailable` e "Cotação restabelecida" só em `Unavailable → Available`; nada a cada ciclo com o mesmo estado nem no primeiro carregamento disponível (FR-047)

### Implementação de `:state`

- [X] T052 Criar as portas em `web/state/src/commonMain/kotlin/com/kotlinbitcoins/web/state/port/`: `SessionStorage.kt` (`read/write/clear` de `StoredSession(accessToken, expiresAtEpochMs)`), `TabChannel.kt` (`send(TabMessage)`, `messages: Flow<TabMessage>`), `PageVisibility.kt` (`isVisible: StateFlow<Boolean>`), `Announcer.kt` (`polite(text)`, `assertive(text)`), `Clock.kt` (`now(): Instant`), `DocumentTitle.kt` (`set(text)`), `SessionTokenProvider.kt` (`tokenForRequest(): String?`, `onUnauthorized()`; usada por `:data` em vez da classe concreta `SessionManager`) e `UserPreferences.kt` (`prefersReducedMotion: StateFlow<Boolean>`, `prefersDark: StateFlow<Boolean>`)
- [X] T053 [P] Implementar `Loadable` (`Loading`, `Loaded`, `Empty`, `Failed(error: ApiError)`) e `QuoteState` (`Loading`, `Available(quote)`, `Unavailable`, `Failed(error)`; `Unavailable` nunca carrega cotação anterior) em `web/state/src/commonMain/kotlin/com/kotlinbitcoins/web/state/shared/Loadable.kt` e `QuoteState.kt`
- [X] T054 Implementar `AutoRefresher` em `web/state/src/commonMain/kotlin/com/kotlinbitcoins/web/state/shared/AutoRefresher.kt` (`interval = 10.seconds`, `combine(visible, paused)`, sem sobreposição, execução imediata ao voltar, `start(scope)`/`setPaused`)
- [X] T055 Implementar `OperationMachine` genérico (`OperationState<A, R>`: `Editing(input, inputError?)`, `Reviewing(amount)`, `Submitting(amount)`, `Succeeded(result)`, `Rejected(amount, error)`, `Uncertain`; parâmetro `hasReview`) em `web/state/src/commonMain/kotlin/com/kotlinbitcoins/web/state/shared/OperationMachine.kt`
- [X] T056 [P] Implementar `QuoteAvailabilityTracker` em `web/state/src/commonMain/kotlin/com/kotlinbitcoins/web/state/shared/QuoteAvailabilityTracker.kt`. Os textos de anúncio são injetados como parâmetros (`:state` não conhece strings de UI)
- [X] T057 Implementar `Session.kt` (`toString()` = `Session(****)`), `SessionState.kt` (`Resolving`, `Active`, `Anonymous(reason: None|Expired|LoggedOut)`), `TabMessage.kt` (`Request`, `Share(accessToken, expiresAtEpochMs)`, `Logout`, `Expired`) e `SessionManager.kt` (implementa a porta `SessionTokenProvider`; `state: StateFlow<SessionState>`, `events: Flow<SessionEvent>`, `start()`, `onLoggedIn(LoginResult-like token + expiresIn)`, `tokenForRequest()`, `onUnauthorized()`, `logout()`) em `web/state/src/commonMain/kotlin/com/kotlinbitcoins/web/state/session/`
- [X] T058 Implementar `SessionGate` (`resolve(requested: AppRoute, state: SessionState): AppRoute` + `LoginBanner`) em `web/state/src/commonMain/kotlin/com/kotlinbitcoins/web/state/session/SessionGate.kt`

### Fixtures de consumidor e fronteira HTTP de `:data` (testes primeiro)

- [X] T059 Criar as 12 fixtures Problem em `web/contract-fixtures/problem/` (`VALIDATION_ERROR.json` com `errors[{field: "email", message: "E-mail em formato inválido."}]`, `INVALID_AMOUNT.json`, `INVALID_CREDENTIALS.json`, `UNAUTHORIZED.json`, `NOT_FOUND.json`, `EMAIL_ALREADY_IN_USE.json`, `INSUFFICIENT_BALANCE.json`, `REDEMPTION_EXCEEDS_AVAILABLE.json`, `INVALID_DATE_RANGE.json`, `QUOTE_UNAVAILABLE.json`, `SERVICE_UNAVAILABLE.json`, `INTERNAL_ERROR.json`), cada uma com `type`, `title`, `status`, `code` e `detail` como os `examples` do contrato da 001. Criar `web/contract-fixtures/manifest.json` (formato de `contracts/fixtures-manifest.md`) com uma entrada por par operação/status em que cada código está documentado no `openapi.yaml`
- [X] T060 Implementar a tarefa `generateContractFixtures` em `web/data/build.gradle.kts`: lê `web/contract-fixtures/manifest.json` e os arquivos citados (inputs declarados) e gera `build/generated/contractFixtures/commonTest/kotlin/com/kotlinbitcoins/web/data/fixtures/ContractFixtures.kt` com `byScenario(name)`, `byFile(path)` e a lista de entradas (`method`, `path`, `status`, `scenario`). Registrar o diretório gerado em `commonTest` e a dependência das tarefas de compilação de teste
- [X] T061 [P] Teste de `ProblemMapper` em `web/data/src/commonTest/kotlin/com/kotlinbitcoins/web/data/http/ProblemMapperTest.kt`: cada uma das 12 fixtures Problem → o `ApiError` da tabela de `contracts/error-messages.md` (`NOT_FOUND`/`INTERNAL_ERROR` → `Unexpected`); `code` desconhecido → `Unexpected`; `VALIDATION_ERROR` preserva `errors[]`; o `detail` não aparece em nenhum `ApiError`; e o conjunto de `code` testados é igual ao conjunto de arquivos em `ContractFixtures` (`problem/`)
- [X] T062 [P] Teste de `ApiCall` em `web/data/src/commonTest/kotlin/com/kotlinbitcoins/web/data/http/ApiCallTest.kt` (MockEngine): 2xx com corpo válido → `Success`; 2xx com campo de valor fora do padrão (`"amount": "1.5"`) → `Unexpected`; status não documentado sem Problem → `Unexpected`; exceção de transporte em GET e em `POST /auth/login` → `NoConnection`; exceção de transporte, timeout, 502 e 504 sem Problem em `POST /me/deposits|purchases|redemptions` → `OperationError.Uncertain`; Problem `SERVICE_UNAVAILABLE`/`INTERNAL_ERROR` em POST financeiro → `OperationError.Rejected`; 401 `UNAUTHORIZED` → `ApiError.Unauthorized` e chamada a `SessionTokenProvider.onUnauthorized()` (fake)
- [X] T063 [P] Teste do `AuthHeaderPlugin` e do cliente em `web/data/src/commonTest/kotlin/com/kotlinbitcoins/web/data/http/HttpClientSecurityTest.kt`: `Authorization: Bearer <token>` presente em `/me/*`, `/quotes/*` e `/market/*`; ausente em `POST /accounts` e `POST /auth/login`; nenhuma URL contém o token; com o fake de `SessionTokenProvider` devolvendo `null` (sessão expirada), a requisição não é enviada; o cliente criado por `HttpClientFactory` não tem o plugin `Logging` instalado
- [X] T064 [P] Teste do codec das mensagens entre abas em `web/data/src/commonTest/kotlin/com/kotlinbitcoins/web/data/session/TabMessageCodecTest.kt` (data-model §8): ida e volta de `request`, `share`, `logout` e `expired`; JSON malformado ou `type` desconhecido → ignorado (`null`), sem exceção
- [X] T065 Implementar `HttpClientFactory` em `web/data/src/commonMain/kotlin/com/kotlinbitcoins/web/data/http/HttpClientFactory.kt` (`HttpClient(Js)`, `ContentNegotiation(json { ignoreUnknownKeys = true; explicitNulls = false })`, `HttpTimeout(requestTimeoutMillis = 30_000)`, `defaultRequest { url(baseUrl) }` com `baseUrl` recebido como parâmetro, `expectSuccess = false`, **sem** `Logging`). Aceitar um `HttpClientEngine` injetável para os testes
- [X] T066 Implementar `ProblemDto` e `ProblemMapper` em `web/data/src/commonMain/kotlin/com/kotlinbitcoins/web/data/http/ProblemMapper.kt` (`when` exaustivo sobre os 12 códigos conhecidos + desconhecido)
- [X] T067 Implementar `ApiCall` em `web/data/src/commonMain/kotlin/com/kotlinbitcoins/web/data/http/ApiCall.kt`: `suspend fun <T> read(...)` → `Outcome<ApiError, T>` e `suspend fun <T> financial(...)` → `Outcome<OperationError, T>`, com o único `runCatching`/`try` do módulo, mapeamento de DTO via lambda que devolve `Outcome` e notificação de 401 pela porta `SessionTokenProvider.onUnauthorized()` (W-05)
- [X] T068 Implementar `AuthHeaderPlugin` em `web/data/src/commonMain/kotlin/com/kotlinbitcoins/web/data/http/AuthHeaderPlugin.kt` (`createClientPlugin`; lê `SessionTokenProvider.tokenForRequest()` no envio; ignora `/accounts` e `/auth/login`)
- [X] T069 [P] Implementar `TabMessageCodec` em `web/data/src/commonMain/kotlin/com/kotlinbitcoins/web/data/session/TabMessageCodec.kt` (kotlinx-serialization com discriminador `type`)

### Adaptadores do navegador (`:data/wasmJsMain`, cobertos por E2E)

- [X] T070 [P] Implementar `BrowserSessionStorage` (chave `kb.session`, JSON `{accessToken, expiresAtEpochMs}`, sem log) em `web/data/src/wasmJsMain/kotlin/com/kotlinbitcoins/web/data/browser/BrowserSessionStorage.kt`
- [X] T071 [P] Implementar `BroadcastTabChannel` (`external class BroadcastChannel`, canal `kb-session`, usa `TabMessageCodec`, `close()` no encerramento) em `web/data/src/wasmJsMain/kotlin/com/kotlinbitcoins/web/data/browser/BroadcastTabChannel.kt`
- [X] T072 [P] Implementar `DocumentVisibility` (`document.visibilityState` + listener `visibilitychange` → `StateFlow<Boolean>`) em `web/data/src/wasmJsMain/kotlin/com/kotlinbitcoins/web/data/browser/DocumentVisibility.kt`
- [X] T073 [P] Implementar `DomAnnouncer` (escreve em `#live-polite`/`#live-assertive`: limpa o texto e o redefine no próximo frame para reanunciar textos iguais) em `web/data/src/wasmJsMain/kotlin/com/kotlinbitcoins/web/data/browser/DomAnnouncer.kt`, usando a técnica escolhida em W-00 Resultado
- [X] T074 [P] Implementar `MediaPreferences` (`matchMedia('(prefers-reduced-motion: reduce)')` e `('(prefers-color-scheme: dark)')` com listeners `change`) em `web/data/src/wasmJsMain/kotlin/com/kotlinbitcoins/web/data/browser/MediaPreferences.kt`
- [X] T075 [P] Implementar `BrowserDocumentTitle` (`document.title`) e `SystemClock` (`Clock.System`) em `web/data/src/wasmJsMain/kotlin/com/kotlinbitcoins/web/data/browser/BrowserDocumentTitle.kt` e `web/data/src/commonMain/kotlin/com/kotlinbitcoins/web/data/time/SystemClock.kt`

### Teste das fixtures no build da API (W-12)

- [X] T076 Adicionar `swagger-request-validator-core` 3.0.0 em `gradle/libs.versions.toml` (raiz) e como `testImplementation` em `bootstrap/build.gradle.kts`. É a única mudança de build do lado da API
- [X] T077 Criar `bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/contract/WebClientFixturesContractTest.kt` (estende `ApplicationTestBase`, lê `web/contract-fixtures/manifest.json` por caminho relativo à raiz, como `OpenApiConformanceTest` lê o contrato). Verificações: (1) cada fixture é aceita pelo schema da resposta `method path status` do `openapi.yaml`, via `OpenApiInteractionValidator.validateResponse`. Se a v3.0.0 não tratar `type: [x, 'null']` do OpenAPI 3.1, trocar para `com.networknt:json-schema-validator` (dialeto 2020-12) e registrar em research.md W-12. (2) O conjunto de `code` em `problem/*.json` é igual ao `enum` de `Problem.code`. (3) Toda operação do manifest existe no contrato com o status indicado. (4) Para cada `realScenario`, executa o cenário real (mapa nome → lambda com `ApiClient`/MockMvc) e compara a forma: chaves recursivas, tipos JSON e `pattern` de cada string de valor. Entradas com `pendingAmendment` são puladas com `Assumptions.abort`/aviso

### Tema, textos e componentes de `:ui` (dependem de T023 = GO)

- [X] T078 [P] Teste de contraste em `web/ui/src/commonTest/kotlin/com/kotlinbitcoins/web/ui/theme/ThemeContrastTest.kt` (luminância relativa WCAG): nos esquemas claro e escuro, ≥ 4,5:1 para `onPrimary/primary`, `onPrimaryContainer/primaryContainer`, `onSecondary/secondary`, `onSurface/surface`, `onSurfaceVariant/surface`, `onSurface/surfaceContainer`, `onError/error`, `onErrorContainer/errorContainer`, `primary/surface` (links e texto de destaque); ≥ 3:1 para `outline/surface`, cor de foco, cores de alta/queda e as duas cores de série do gráfico contra `surface`
- [X] T079 [P] Teste de mensagens em `web/ui/src/commonTest/kotlin/com/kotlinbitcoins/web/ui/shared/ErrorMessagesTest.kt`: todo subtipo de `ApiError` e `OperationError` (enumerados por lista exaustiva com `when`) tem chave de string não vazia; nenhum texto em `strings.xml` de erro contém `code`, `HTTP`, `exception`, `null`, `stack`, `{` ou nomes de enum (`INVALID_AMOUNT` …) — SC-003
- [X] T080 Gerar com o Material Theme Builder (semente `#F7931A`, export Jetpack Compose) os esquemas claro e escuro e salvar em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/theme/Color.kt` (comentário com semente, ferramenta e data). Acrescentar `successColor`/`declineColor` (alta/queda) e `chartBuyColor`/`chartSellColor`, ajustando tons até o T078 passar. O laranja `#F7931A` não é usado em texto
- [X] T081 [P] Adicionar Inter Regular/Medium/SemiBold (OFL, subconjunto Latin + Latin-1 + `R$ − ↑ ↓ →`) em `web/ui/src/commonMain/composeResources/font/` e criar `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/theme/Type.kt` (`Typography` M3 com Inter; estilo `numeric` com `fontFeatureSettings = "tnum"`)
- [X] T082 Criar `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/theme/Theme.kt` (`AppTheme` com `isSystemInDarkTheme()` + atualização por `UserPreferences.prefersDark`) e `ReducedMotion.kt` (`LocalReducedMotion`; com `true`, transições `None` e indicadores de progresso trocados por texto estático "Carregando…")
- [X] T083 Criar `web/ui/src/commonMain/composeResources/values/strings.xml` com os textos compartilhados: nome do app, rótulos de navegação (Painel, Depositar, Comprar, Meus investimentos, Resgatar, Extrato, Mercado, Sair, Menu), "Carregando…", "Tentar de novo", "(horário de Brasília)", "Estimativa", títulos de página (`contracts/ui-routes.md`), todas as mensagens de `contracts/error-messages.md` e os anúncios "Cotação indisponível no momento", "Cotação restabelecida", "Atualização automática pausada" e "Atualização automática retomada"
- [X] T084 Implementar `ErrorMessages` (`@Composable fun ApiError.message(context: ErrorContext)` e `OperationError.message(...)` com `when` exaustivo; `ErrorContext.Purchase` acrescenta o complemento de 1 satoshi ao `InvalidAmount`) em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/shared/ErrorMessages.kt`
- [X] T085 [P] Implementar `LoadableBlock.kt` (estados carregando/vazio/erro distintos, FR-042) e `ErrorBlock.kt` (mensagem, botão "Tentar de novo" opcional, título focável, sem `detail` técnico) em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/components/`
- [X] T086 [P] Implementar `AmountField` (rótulo visível, prefixo "R$", `KeyboardType.Decimal`, `isError` + `semantics { error(msg) }`, texto de erro abaixo, `FocusRequester` exposto, sem máscara que mova o cursor) em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/components/AmountField.kt`
- [X] T087 [P] Implementar `SubmitButton` (com `isSubmitting`: desabilitado, rótulo "Processando…", `semantics { disabled() }`; não reage a clique, toque ou Enter) em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/components/SubmitButton.kt`
- [X] T088 [P] Implementar `FormErrorSummary` (resumo "Revise os campos destacados." com lista de erros; cada item move o foco ao campo) em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/components/FormErrorSummary.kt`
- [X] T089 [P] Implementar `QuoteBlock` (compra, venda e instante `dd/mm/aaaa hh:mm:ss (horário de Brasília)`; `Unavailable` mostra só "Cotação indisponível no momento", sem valores; sem `liveRegion`; estilo `numeric`) em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/components/QuoteBlock.kt`
- [X] T090 [P] Implementar `PauseToggle` (rótulo visível "Atualização automática: ativa"/"pausada", `semantics { stateDescription }`, alvo ≥ 48 dp) em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/components/PauseToggle.kt`
- [X] T091 Implementar `AdaptiveScaffold` em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/shell/AdaptiveScaffold.kt` (`BoxWithConstraints`: < 600 dp → `TopAppBar` + botão "Menu" + `ModalNavigationDrawer`; 600–839 dp → `NavigationRail`; ≥ 840 dp → `PermanentNavigationDrawer`; os 7 destinos + "Sair"; item atual com `selected`; conteúdo com largura máxima de 720 dp; sem `horizontalScroll`)
- [X] T092 Implementar `AppNavHost` em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/shell/AppNavHost.kt`: `NavHost` com as rotas `AppRoute` (telas temporárias "Em construção" para os destinos ainda não implementados); observa `SessionManager.events` e aplica `SessionGate` a cada mudança de destino (`Login` com `returnTo` e banner, `popUpTo(0)` no logout); define `document.title` pela porta `DocumentTitle`; move o foco ao `h1` da tela a cada destino; transições respeitam `LocalReducedMotion`. Recebe as fábricas de ViewModel por parâmetro (sem conhecer `:data`)

### Borda (`:app`) e entrega

- [X] T093 Criar `web/app/src/wasmJsMain/resources/index.html` (`<html lang="pt-BR">`, `<title>Investimento em Bitcoins</title>`, `<link rel="stylesheet" href="loading.css">`, `<link rel="preload">` só da fonte (o preload dos `.wasm` com hash é injetado no build, T098), `<script src="compat.js">` antes do app, `<div id="boot" role="status">` com marca e "Carregando…", `<noscript>` em pt-BR, `#live-polite` e `#live-assertive` visualmente ocultos, sem script/estilo inline) e `web/app/src/wasmJsMain/resources/loading.css` (spinner desligado em `prefers-reduced-motion`, cores claras/escuras por `prefers-color-scheme`). Criar `web/app/src/wasmJsMain/resources/compat.js` (arquivo externo, compatível com a CSP): testa WasmGC **e** exception handling com `WebAssembly.validate` sobre dois módulos mínimos (um com tipo GC, outro com `try`/`throw`), requisitos da Kotlin/Wasm (research.md W-01); se algum falhar, troca o conteúdo de `#boot` por "Seu navegador não é compatível. Atualize para a versão mais recente." e não carrega o `app.js`; se passar, insere o `<script src="app.js">`
- [X] T094 Implementar `AppContainer` em `web/app/src/wasmJsMain/kotlin/com/kotlinbitcoins/web/app/AppContainer.kt`: cria `HttpClient` (`baseUrl = window.location.origin + "/api/v1/"`), `SessionManager` (com `BrowserSessionStorage`, `BroadcastTabChannel`, `SystemClock`), adaptadores do navegador e as fábricas de ViewModel. Os gateways e ViewModels são acrescentados por cada user story
- [X] T095 Implementar `main.kt` em `web/app/src/wasmJsMain/kotlin/com/kotlinbitcoins/web/app/main.kt`: referência obrigatória a `@JsModule("@js-joda/timezone") external object JsJodaTimeZoneModule`; `AppContainer`; `sessionManager.start()`; lê `window.location.hash` → `AppRoute.parse`; `ComposeViewport(document.body!!) { AppTheme { AppNavHost(...) } }`; `navController.bindToBrowserNavigation(getBackStackEntryRoute = { it.toRoute<AppRoute>().toFragment() })` depois da navegação inicial; remove `#boot` no primeiro frame
- [X] T096 [P] Criar `web/app/webpack.config.d/devServerProxy.js` (`config.devServer.port = 8082`; proxy `/api` → `http://localhost:8080` no `wasmJsBrowserDevelopmentRun`; mais o cabeçalho CSP de W-15)
- [X] T097 [P] Criar `web/docker/nginx.conf` conforme research.md W-15: `listen 8080`; `types { application/wasm wasm; }`; `gzip_static on; brotli_static on;` + `gzip` dinâmico de fallback; `Cache-Control: public, max-age=31536000, immutable` para nomes com hash (`\.[0-9a-f]{8,}\.(wasm|js|mjs)$`) e `no-cache` para `index.html`, entrada sem hash e `composeResources/`; `try_files $uri $uri/ /index.html`; CSP, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `Permissions-Policy`; `location /api/ { proxy_pass http://nginx:8080; }` repassando `Authorization` e `X-Correlation-Id`, com `proxy_read_timeout 30s`
- [X] T098 Criar `web/Dockerfile` multi-stage (contexto `web/`): `gradle:jdk21` → `./gradlew :app:wasmJsBrowserDistribution --no-daemon`; estágio `compress`, que primeiro roda `web/docker/inject-preload.sh` (lista os `*.wasm` com hash da distribuição e insere um `<link rel="preload" as="fetch" crossorigin href="…">` para cada um no `index.html`) e depois pré-comprime (`.gz` com `gzip -9 -k` e `.br` com `brotli -q 11 -k` em `*.wasm *.js *.mjs *.css *.html *.json *.ttf`); estágio `brotli-module` compilando `ngx_brotli` como módulo dinâmico contra o fonte da versão exata do nginx da imagem final; final `nginxinc/nginx-unprivileged:1.27-alpine` com `load_module` e os arquivos em `/usr/share/nginx/html`. Criar também `web/.dockerignore` (`build/`, `.gradle/`, `.kotlin/`, `e2e/`, `spike-a11y/`). Se o módulo brotli falhar, manter só gzip e registrar em plan.md Complexity Tracking
- [X] T099 Adicionar o serviço `web` em `docker-compose.yml` (`build: ./web`, `ports: ["8081:8080"]`, `depends_on: nginx: condition: service_healthy`, healthcheck `wget -q -O /dev/null http://127.0.0.1:8080/index.html`) e corrigir o comando de build de `specs/002-frontend-web/quickstart.md` §7 para `docker build -t kotlinbitcoins-web web`
- [X] T100 Adicionar o job `web` em `.github/workflows/ci.yml` (JDK 21 Temurin, `gradle/actions/setup-gradle@v4`, `browser-actions/setup-chrome@v1` com `CHROME_BIN` exportado para os testes de navegador do `:ui`, `working-directory: web`): `./gradlew check --no-daemon`, `./gradlew :app:wasmJsBrowserDistribution --no-daemon` e `docker build -t kotlinbitcoins-web:ci web`. O job `build` existente continua igual (o T077 entra no `check` da raiz)

### Suporte de E2E

- [X] T101 Criar o suporte em `web/e2e/src/test/kotlin/com/kotlinbitcoins/web/e2e/support/`: `E2eBase.kt` (Playwright por navegador de `e2e.browsers`, contexto novo por teste com `locale = "pt-BR"` e `timezoneId = "America/New_York"`, para provar FR-037; captura de console e falha em violação de CSP), `ApiClient.kt` (`java.net.http`: `register`, `login`, `deposit`, `purchase`, `statement` em `e2e.apiUrl`, para preparar dados e verificar efeitos), `Viewports.kt` (320, 375, 768, 1280, 1920 e zoom 200% = 640 × 400 com `deviceScaleFactor` 2), `Axe.kt` (`assertNoViolations(page)` com tags `wcag2a`, `wcag2aa`, `wcag21a`, `wcag21aa`), `Ui.kt` (seletores/ações por teclado conforme W-00 Resultado) e `NoHorizontalScroll.kt` (`scrollWidth <= clientWidth` e caixas dos nós do espelho dentro do viewport)
- [X] T102 Teste de fumaça em `web/e2e/src/test/kotlin/com/kotlinbitcoins/web/e2e/smoke/AppShellE2ETest.kt`: `#boot` visível antes do Wasm e removido depois; `document.title` e `lang="pt-BR"`; resposta do `.wasm` com `Content-Type: application/wasm`, `Content-Encoding: br` e `immutable`; `index.html` com `no-cache` e CSP com `'wasm-unsafe-eval'`; `GET /api/v1/quotes/current` sem token → 401 pela mesma origem; 0 violações de CSP no console; navegador sem WasmGC simulado (`page.addInitScript` sobrescrevendo `WebAssembly.validate` para devolver `false`) → `#boot` mostra "Seu navegador não é compatível. Atualize para a versão mais recente." e `app.js` não é requisitado

**Checkpoint**: `cd web && ./gradlew check` verde (núcleo, estado, HTTP, fixtures, tema, mensagens); `./gradlew check` da raiz verde com `WebClientFixturesContractTest`; `docker compose --profile stub up` serve o shell em `http://localhost:8081` e `AppShellE2ETest` passa. As user stories podem começar.

---

## Phase 4: User Story 1 - Cadastro, login e sessão (Priority: P1) 🎯 MVP

**Goal**: criar conta com entrada automática, entrar e sair, sessão que sobrevive a recarregamento e novas abas, expira com aviso e volta à página de origem.

**Independent Test**: cadastrar pela interface → painel; sair; abrir página protegida → login; entrar; recarregar → sessão continua (spec US1).

### Tests for User Story 1 ⚠️

- [X] T103 [P] [US1] Teste de `RegistrationValidator` em `web/core/src/commonTest/kotlin/com/kotlinbitcoins/web/core/format/RegistrationValidatorTest.kt`: nome "não vazio (ignorando espaços nas extremidades) e com no máximo 200 caracteres" (contados como `String.length` em UTF-16 após `trim()`, como a 001 faz em `Account.isValidName` e no `@Size(max = 200)`: 200 ✓, 201 ✗, `"   "` ✗, 100 emojis = 200 unidades ✓, 101 emojis ✗); e-mail `^[^\s@]+@[^\s@]+\.[^\s@]+$` com no máximo 320; senha "no mínimo 8 caracteres e no máximo 72 bytes em UTF-8" (mínimo contado em code points, como `PasswordPolicy` da 001; `"1234567"` ✗; 72 bytes ASCII ✓; 73 bytes ✗; 19 emojis de 4 bytes = 76 bytes ✗ como `PasswordTooLong`); validação devolve todos os erros de uma vez, na ordem nome, e-mail, senha
- [X] T104 [P] [US1] Criar `web/contract-fixtures/success/accounts-201.json` e `web/contract-fixtures/success/auth-login-200.json` e registrar no `manifest.json`: `POST /accounts 201` (`realScenario: "registerAccount"`), `POST /auth/login 200` (`realScenario: "loginOk"`), `EMAIL_ALREADY_IN_USE` em `POST /accounts 409` (`realScenario: "registerDuplicateEmail"`), `VALIDATION_ERROR` em `POST /accounts 400` e `POST /auth/login 400`, e `INVALID_CREDENTIALS` em `POST /auth/login 401` (`realScenario: "loginWrongPassword"`). Acrescentar os cenários reais ao mapa do `WebClientFixturesContractTest`
- [X] T105 [P] [US1] Teste de `HttpAuthGateway` em `web/data/src/commonTest/kotlin/com/kotlinbitcoins/web/data/gateway/HttpAuthGatewayTest.kt` (MockEngine + `ContractFixtures`): `register` envia `{name, email, password}` e mapeia 201 → `AccountCreated`; `login` mapeia 200 → `LoginResult(accessToken, expiresIn = 3600.seconds)`; 409 → `EmailAlreadyInUse`; 400 → `Validation(fieldErrors)`; 401 → `InvalidCredentials`; transporte → `NoConnection`
- [X] T106 [P] [US1] Teste de `RegisterViewModel` em `web/state/src/commonTest/kotlin/com/kotlinbitcoins/web/state/account/RegisterViewModelTest.kt`: envio inválido → erro por campo, `focusRequest` = primeiro inválido, gateway não chamado; sucesso → login automático com as mesmas credenciais → `SessionManager.onLoggedIn` → navegação para `Dashboard`; `EmailAlreadyInUse` → erro no campo e-mail com atalho para login, nome e e-mail mantidos e senha limpa; `Validation` → erros por campo pelo `field` (desconhecido vai para o resumo); conta criada mas login falhou → `Login` com banner `AccountCreated`; duplo envio → 1 chamada
- [X] T107 [P] [US1] Teste de `LoginViewModel` em `web/state/src/commonTest/kotlin/com/kotlinbitcoins/web/state/account/LoginViewModelTest.kt`: `InvalidCredentials` → banner genérico idêntico para e-mail inexistente e senha errada; sucesso → `ReturnDestination.resolve(returnTo)` (filtro do extrato preservado; destino externo → `Dashboard`) com substituição da entrada de histórico; banner `SessionExpired` quando a rota pede; `NoConnection` → mensagem genérica de conexão; duplo envio → 1 chamada
- [X] T108 [P] [US1] E2E em `web/e2e/src/test/kotlin/com/kotlinbitcoins/web/e2e/auth/RegisterAndLoginE2ETest.kt`: US1-1 (cadastro → painel), US1-2 (três erros por campo, foco no nome, nenhuma requisição enviada), US1-3 (e-mail duplicado com atalho e dados mantidos sem a senha), US1-4 (mesma mensagem nos dois casos), US1-7 (Sair → login; voltar do navegador não mostra dados), US1-8 (acesso direto a `#/investimentos` → login → `#/investimentos`), o Edge Case "conta criada mas entrada automática falha" (via `page.route` abortando o login) e axe em cadastro e login, com e sem erros
- [X] T109 [P] [US1] E2E em `web/e2e/src/test/kotlin/com/kotlinbitcoins/web/e2e/auth/SessionPersistenceE2ETest.kt`: US1-5 (recarregar e nova aba no mesmo contexto continuam conectadas), US1-9 (fechar o contexto e abrir um novo, sem restauração de sessão → login; ver Assumptions da spec), "Sair" em uma aba leva a outra aba ao login (Edge Case "várias abas"), e o token ausente de `localStorage`, cookies e URLs de todas as requisições
- [X] T110 [P] [US1] E2E em `web/e2e/src/test/kotlin/com/kotlinbitcoins/web/e2e/auth/SessionExpiryE2ETest.kt` (SC-009): com `page.clock().install()`, avançar além de `expiresIn` → login com "Sua sessão expirou. Entre novamente para continuar." → após entrar, volta à mesma página; 401 simulado por `page.route` numa leitura → mesmo fluxo; expiração com compra em revisão → volta a `#/comprar` com o formulário vazio (Edge Case); expiração propaga para a segunda aba
- [X] T111 [P] [US1] E2E em `web/e2e/src/test/kotlin/com/kotlinbitcoins/web/e2e/auth/ReturnDestinationE2ETest.kt`: `#/entrar?voltar=https%3A%2F%2Fevil.example`, `voltar=%2F%2Fevil.example` e `voltar=javascript%3Aalert(1)` → após login, `#/painel` e nenhuma navegação para fora da origem

### Implementation for User Story 1

- [X] T112 [P] [US1] Implementar `RegistrationValidator` (`RegistrationField`, `RegistrationError`: `NameBlank`, `NameTooLong`, `EmailInvalid`, `PasswordTooShort`, `PasswordTooLong`) em `web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/format/RegistrationValidator.kt`
- [X] T113 [P] [US1] Criar `AccountCreated.kt` (`id`, `name`, `email`) e `LoginResult.kt` (`accessToken`, `expiresIn: Duration`; `toString()` mascarado) em `web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/model/`
- [X] T114 [US1] Criar a porta `AuthGateway` (`register(name, email, password): Outcome<ApiError, AccountCreated>`, `login(email, password): Outcome<ApiError, LoginResult>`) em `web/state/src/commonMain/kotlin/com/kotlinbitcoins/web/state/port/AuthGateway.kt`
- [X] T115 [US1] Criar `RegisterAccountRequestDto`, `AccountResponseDto`, `LoginRequestDto` e `LoginResponseDto` em `web/data/src/commonMain/kotlin/com/kotlinbitcoins/web/data/dto/AccountDtos.kt` e implementar `HttpAuthGateway` em `web/data/src/commonMain/kotlin/com/kotlinbitcoins/web/data/gateway/HttpAuthGateway.kt` (via `ApiCall.read`)
- [X] T116 [US1] Implementar `RegisterViewModel` + `RegisterUiState` em `web/state/src/commonMain/kotlin/com/kotlinbitcoins/web/state/account/RegisterViewModel.kt`
- [X] T117 [US1] Implementar `LoginViewModel` + `LoginUiState` (`LoginBanner`: `SessionExpired`, `AccountCreated`, `InvalidCredentials`, `Error(ApiError)`) em `web/state/src/commonMain/kotlin/com/kotlinbitcoins/web/state/account/LoginViewModel.kt`
- [X] T118 [US1] Acrescentar a `web/ui/src/commonMain/composeResources/values/strings.xml` os textos de cadastro e login (rótulos, mensagens de validação de `contracts/error-messages.md`, banners "Sua sessão expirou. Entre novamente para continuar." e "Conta criada. Entre com seu e-mail e senha.", links "Criar conta"/"Entrar")
- [X] T119 [P] [US1] Implementar `RegisterScreen` (stateless; `h1` "Criar conta"; campos com rótulo, `autofill` e `ImeAction`; `FormErrorSummary`; foco no primeiro inválido via `focusRequest`; link "Entrar" no erro de e-mail duplicado; `SubmitButton`) em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/account/RegisterScreen.kt`
- [X] T120 [P] [US1] Implementar `LoginScreen` (stateless; `h1` "Entrar"; banner em região de alerta com foco; campos e-mail/senha; link "Criar conta"; `SubmitButton`) em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/account/LoginScreen.kt`
- [X] T121 [US1] Ligar `HttpAuthGateway`, `RegisterViewModel` e `LoginViewModel` em `web/app/src/wasmJsMain/kotlin/com/kotlinbitcoins/web/app/AppContainer.kt` e os destinos `Login`/`Register` + ação "Sair" (`SessionManager.logout()`) em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/shell/AppNavHost.kt`

**Checkpoint**: US1 completa e testável sozinha (T103–T111 verdes). Cadastro, login, sair, persistência e expiração funcionam.

---

## Phase 5: User Story 2 - Painel (Priority: P1)

**Goal**: saudação com primeiro nome, saldo, cotação com instante atualizada a cada ~10 s (com pausa), valor atual total dos investimentos e atalhos. Cada bloco falha de forma independente.

**Independent Test**: conta com saldo e investimentos → saudação, saldo, cotação com instante, total e três atalhos; a cotação muda sozinha; cotação indisponível não afeta o resto (spec US2).

### Emenda `GET /me` na 001 (pré-requisito da saudação com nome — FR-005, contracts/api-amendment-get-me.md)

- [X] T122 [US2] Aplicar a emenda na origem: novo requisito de consulta de perfil (001/FR-036; não confundir com o FR-036 da 002) com cenário de aceitação em `specs/001-bitcoin-investment-api/spec.md` e o delta de `specs/002-frontend-web/contracts/api-amendment-get-me.md` (`GET /me` → 200 `ProfileResponse {name, email}` "sem dados sensíveis e sem identificador de outro cliente", 401) em `specs/001-bitcoin-investment-api/contracts/openapi.yaml`. Atualizar também `specs/001-bitcoin-investment-api/plan.md` (Scale/Scope: 12 endpoints), `specs/001-bitcoin-investment-api/data-model.md` (se listar operações) e `specs/001-bitcoin-investment-api/tasks.md` (uma seção "Emenda GET /me" com T123–T125 desta lista, para rastreabilidade na 001)
- [X] T123 [P] [US2] Teste do caso de uso em `application/src/test/kotlin/com/kotlinbitcoins/application/account/GetProfileTest.kt` (conta existente → nome e e-mail; conta inexistente → erro tipado; falha de infraestrutura → `ServiceUnavailable`)
- [X] T124 [P] [US2] Teste do controller em `api/src/test/kotlin/com/kotlinbitcoins/api/account/ProfileControllerTest.kt` (200 com só `name` e `email`; 401 sem token) e teste de contrato em `bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/contract/ProfileContractTest.kt`. Acrescentar o caso `GET /me` em `bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/isolation/AccountIsolationTest.kt`
- [X] T125 [US2] Implementar `GetProfile` em `application/src/main/kotlin/com/kotlinbitcoins/application/account/GetProfile.kt` (usa `AccountRepository.findById`), `ProfileController` em `api/src/main/kotlin/com/kotlinbitcoins/api/account/ProfileController.kt` (`ProfileResponse` em `api/src/main/kotlin/com/kotlinbitcoins/api/account/AccountDtos.kt`) e o bean em `bootstrap/src/main/kotlin/com/kotlinbitcoins/bootstrap/UseCaseConfiguration.kt`. Confirmar `OpenApiConformanceTest` verde
- [X] T126 [US2] Remover `pendingAmendment` da entrada `GET /me` em `web/contract-fixtures/manifest.json` e reduzir `specs/002-frontend-web/contracts/api-amendment-get-me.md` a um link para a operação no `openapi.yaml` da 001 (status: aplicada)

### Tests for User Story 2 ⚠️

- [X] T127 [P] [US2] Criar fixtures e registrar no `manifest.json`: `web/contract-fixtures/success/me-200.json` (`GET /me 200`, `realScenario: "profile"`, `pendingAmendment: "GET /me"` até T126), `me-balance-200.json` (`realScenario: "balanceAfterDeposit"`), `quotes-current-200.json`, `quotes-current-200-8-decimals.json` (`buyPrice: "400000.12345678"`), `me-investments-200.json` (três investimentos com `variationPercent` `"10.00"`, `"-5.00"` e `"0.00"`; `realScenario: "positionAfterPurchase"`) e `me-investments-200-empty.json`; mais `QUOTE_UNAVAILABLE` em `GET /quotes/current 503` e `GET /me/investments 503`, e `UNAUTHORIZED` em `GET /me/balance 401`. Implementar os cenários reais citados no mapa nome → lambda do `bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/contract/WebClientFixturesContractTest.kt` (T077)
- [X] T128 [P] [US2] Teste de `Profile` em `web/core/src/commonTest/kotlin/com/kotlinbitcoins/web/core/model/ProfileTest.kt`: `"Maria Souza"` → `firstName = "Maria"`; `"  Ana  "` → `"Ana"`; `""` → sem nome
- [X] T129 [P] [US2] Testes de gateways em `web/data/src/commonTest/kotlin/com/kotlinbitcoins/web/data/gateway/`: `HttpProfileGatewayTest.kt` (`GET /me` → `Profile`), `HttpAccountGatewayBalanceTest.kt` (`GET /me/balance` → `Money`), `HttpQuoteGatewayCurrentTest.kt` (cotação com 2 e 8 casas preservadas; 503 → `QuoteUnavailable`) e `HttpInvestmentGatewayPositionTest.kt` (ordem preservada, lista vazia, 503 → `QuoteUnavailable`)
- [X] T130 [P] [US2] Teste de `DashboardViewModel` em `web/state/src/commonTest/kotlin/com/kotlinbitcoins/web/state/dashboard/DashboardViewModelTest.kt`: perfil ok → `Greeting.Named("Maria")`; perfil falhou → `Greeting.Anonymous` sem afetar os outros blocos (US2-7); saldo, cotação e posição em `Loadable`/`QuoteState` independentes (FR-013); cotação indisponível → `QuoteState.Unavailable` sem valores antigos e posição `DependsOnQuote`, com novas tentativas a cada ciclo (US2-3); sem investimentos → `NoInvestments` (US2-4); cotação e posição atualizam a cada 10 s, perfil e saldo não; pausa suspende e é anunciada; anúncios só nas transições de disponibilidade
- [X] T131 [P] [US2] E2E em `web/e2e/src/test/kotlin/com/kotlinbitcoins/web/e2e/dashboard/DashboardE2ETest.kt`: US2-1 ("Olá, Maria", `R$ 1.234,56` preparado via `ApiClient`, cotação com instante "(horário de Brasília)" em contexto com `timezoneId` de Nova York), US2-4 (`R$ 0,00` + convite), US2-5 (três atalhos), US2-7 (`page.route` 500 em `/api/v1/me` → "Olá!") e axe nos estados carregado, vazio e indisponível
- [X] T132 [P] [US2] E2E em `web/e2e/src/test/kotlin/com/kotlinbitcoins/web/e2e/dashboard/AutoRefreshE2ETest.kt`: US2-2 (cotação muda em ~10 s e o foco continua no atalho "Comprar"), US2-6 (pausa visível, anunciada no `#live-polite` e sem novas requisições a `/quotes/current` por 25 s), aba oculta (sobrescrever `document.visibilityState` + `visibilitychange`) suspende e, ao voltar, faz requisição imediata, e SC-005 (`now − quotedAt ≤ 25 s` amostrado por 2 min com o quote-stub)
- [X] T133 [P] [US2] E2E em `web/e2e/src/test/kotlin/com/kotlinbitcoins/web/e2e/dashboard/QuoteUnavailableE2ETest.kt`: US2-3 com `page.route` devolvendo a fixture `QUOTE_UNAVAILABLE` (bloco "Cotação indisponível no momento", total "depende da cotação", saldo, atalhos e Sair funcionando, anúncio único) e retorno automático dos valores ao remover a rota (Edge Case "cotação volta"); variante lenta com `docker compose stop quote-stub` marcada `@Tag("slow")` (SC-006)

### Implementation for User Story 2

- [X] T134 [P] [US2] Criar `Profile` (`name`, `email`, `firstName` derivado da "primeira palavra de `name.trim()`") em `web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/model/Profile.kt`
- [X] T135 [US2] Criar as portas em `web/state/src/commonMain/kotlin/com/kotlinbitcoins/web/state/port/`: `ProfileGateway.kt` (`profile()`), `AccountGateway.kt` (`balance()`), `QuoteGateway.kt` (`current()`) e `InvestmentGateway.kt` (`position()`), todas devolvendo `Outcome<ApiError, T>`
- [X] T136 [US2] Criar `ProfileDto`, `BalanceDto`, `QuoteDto` e `PositionDto`/`InvestmentPositionDto` em `web/data/src/commonMain/kotlin/com/kotlinbitcoins/web/data/dto/` (`ProfileDto.kt`, `BalanceDto.kt`, `QuoteDto.kt` — um DTO por arquivo, como exigem ktlint `standard:filename` e detekt `MatchingDeclarationName` — e `PositionDtos.kt`), os mapeadores em `web/data/src/commonMain/kotlin/com/kotlinbitcoins/web/data/mapper/` (`QuoteMapper.kt`, `PositionMapper.kt`; valor fora do padrão → `Unexpected`) e os gateways `HttpProfileGateway.kt`, `HttpAccountGateway.kt`, `HttpQuoteGateway.kt` e `HttpInvestmentGateway.kt` em `web/data/src/commonMain/kotlin/com/kotlinbitcoins/web/data/gateway/`
- [X] T137 [US2] Implementar `DashboardViewModel` + `DashboardUiState` (`Greeting`, `balance: Loadable<Money>`, `quote: QuoteState`, `position: PositionTotal` = `Loading|Value|DependsOnQuote|NoInvestments|Failed`, `isAutoRefreshPaused`) com `AutoRefresher` e `QuoteAvailabilityTracker` em `web/state/src/commonMain/kotlin/com/kotlinbitcoins/web/state/dashboard/DashboardViewModel.kt`
- [X] T138 [US2] Acrescentar a `web/ui/src/commonMain/composeResources/values/strings.xml` os textos do painel ("Olá, %1$s", "Olá!", "Saldo", "Cotação de compra", "Cotação de venda", "Valor atual dos investimentos", "Depende da cotação", convite "Você ainda não tem investimentos. Faça sua primeira compra.", atalhos)
- [X] T139 [US2] Implementar `DashboardScreen` (stateless; `h1` com a saudação; blocos de saldo, `QuoteBlock` e total, cada um com `LoadableBlock`; `PauseToggle`; atalhos "Depositar", "Comprar" e "Resgatar"; valores atualizados sem trocar a estrutura nem o foco) em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/dashboard/DashboardScreen.kt`
- [X] T140 [US2] Ligar os quatro gateways e o `DashboardViewModel` em `web/app/src/wasmJsMain/kotlin/com/kotlinbitcoins/web/app/AppContainer.kt` e o destino `Dashboard` em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/shell/AppNavHost.kt`

**Checkpoint**: US1 e US2 funcionam de forma independente. Painel com atualização automática, pausa e falha independente por bloco.

---

## Phase 6: User Story 3 - Depósito (Priority: P1)

**Goal**: depositar um valor em R$ (padrão brasileiro), ver confirmação com valor, data/hora e novo saldo, com envio único.

**Independent Test**: conta nova → depositar R$ 100,00 → confirmação e novo saldo R$ 100,00, também no painel (spec US3).

### Tests for User Story 3 ⚠️

- [X] T141 [P] [US3] Criar `web/contract-fixtures/success/me-deposits-201.json` e registrar no `manifest.json` (`POST /me/deposits 201`, `realScenario: "depositOk"`) mais `INVALID_AMOUNT` em `POST /me/deposits 400` (`realScenario: "depositZero"`). Implementar os cenários reais citados no mapa nome → lambda do `bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/contract/WebClientFixturesContractTest.kt` (T077)
- [X] T142 [P] [US3] Teste do depósito no gateway em `web/data/src/commonTest/kotlin/com/kotlinbitcoins/web/data/gateway/HttpAccountGatewayDepositTest.kt`: envia `{"amount": "1234.56"}`; 201 → `DepositResult`; 400 → `Rejected(InvalidAmount)`; transporte/502 → `Uncertain`
- [X] T143 [P] [US3] Teste de `DepositViewModel` em `web/state/src/commonTest/kotlin/com/kotlinbitcoins/web/state/deposit/DepositViewModelTest.kt`: `"100,00"` com saldo 50 → `Succeeded` com valor, novo saldo `R$ 150,00` e data/hora da API (US3-1); `""`, `"0"`, `"-5"`, `"abc"` e `"1,234"` → erro no campo sem chamar o gateway (US3-2); `"1.234,56"` envia `"1234.56"` (US3-3); 5 confirmações → 1 chamada (US3-4); `Uncertain` sem retry; anúncio assertive "Depósito realizado" e foco no resultado
- [X] T144 [P] [US3] E2E em `web/e2e/src/test/kotlin/com/kotlinbitcoins/web/e2e/operations/DepositE2ETest.kt`: US3-1, US3-2 (nenhuma requisição `POST /me/deposits` em entradas inválidas), US3-3, painel com o novo saldo, axe nos estados edição, erro e resultado
- [X] T145 [P] [US3] E2E de envio duplicado e resultado incerto em `web/e2e/src/test/kotlin/com/kotlinbitcoins/web/e2e/operations/DepositSubmissionSafetyE2ETest.kt` (SC-004, FR-043): `dblclick`, Enter ×5, toque duplo (`hasTouch`), recarregar durante o envio (resposta atrasada por `page.route`) e voltar/avançar após o resultado. Em cada caso, o `ApiClient.statement()` mostra **exatamente um** depósito. Resultado incerto: `page.route` com `route.fetch()` (a requisição chega à API) seguido de `route.abort()` → mensagem "Não foi possível confirmar…" com links para Painel e Extrato e **sem** botão de reenvio

### Implementation for User Story 3

- [X] T146 [P] [US3] Criar `DepositResult` (`transactionId`, `amount: Money`, `balance: Money`, `occurredAt: ApiInstant`) em `web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/model/DepositResult.kt`
- [X] T147 [US3] Acrescentar `deposit(amount: Money): Outcome<OperationError, DepositResult>` em `web/state/src/commonMain/kotlin/com/kotlinbitcoins/web/state/port/AccountGateway.kt`, `AmountRequestDto`/`DepositResponseDto` em `web/data/src/commonMain/kotlin/com/kotlinbitcoins/web/data/dto/DepositDtos.kt` e a implementação via `ApiCall.financial` em `web/data/src/commonMain/kotlin/com/kotlinbitcoins/web/data/gateway/HttpAccountGateway.kt`
- [X] T148 [US3] Implementar `DepositViewModel` (`OperationMachine` com `hasReview = false`; parsing com `AmountInputParser`) em `web/state/src/commonMain/kotlin/com/kotlinbitcoins/web/state/deposit/DepositViewModel.kt`
- [X] T149 [P] [US3] Implementar `UncertainOutcomeBlock` (região de alerta; texto de `OperationError.Uncertain`; links "Ver saldo" → Painel e "Ver extrato"; sem reenvio) em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/components/UncertainOutcomeBlock.kt`
- [X] T150 [US3] Acrescentar a `web/ui/src/commonMain/composeResources/values/strings.xml` os textos do depósito ("Valor do depósito", "Depositar", "Depósito de %1$s realizado", "Novo saldo", "Data e hora", "Novo depósito", "Depósito realizado" para o anúncio)
- [X] T151 [US3] Implementar `DepositScreen` (stateless; `h1` "Depositar"; `AmountField`; `SubmitButton`; resultado com foco no título e sufixo de título "Depósito realizado"; `ErrorBlock`/`UncertainOutcomeBlock`) em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/deposit/DepositScreen.kt`
- [X] T152 [US3] Ligar `DepositViewModel` em `web/app/src/wasmJsMain/kotlin/com/kotlinbitcoins/web/app/AppContainer.kt` e o destino `Deposit` em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/shell/AppNavHost.kt`

**Checkpoint**: US1–US3 completas. Há saldo para investir.

---

## Phase 7: User Story 4 - Compra de bitcoin (Priority: P1) 🎯 fecha o MVP

**Goal**: informar valor → revisar com estimativa rotulada (recalculada a cada cotação) → confirmar → ver só o resultado da API, com tratamento de saldo insuficiente, valor inválido/1 satoshi e cotação indisponível.

**Independent Test**: saldo R$ 1.000,00, comprar R$ 400,00, conferir estimativa e aviso, confirmar → valor investido, BTC, cotação e novo saldo R$ 600,00 (spec US4).

### Tests for User Story 4 ⚠️

- [X] T153 [P] [US4] Criar `web/contract-fixtures/success/me-purchases-201.json` e registrar no `manifest.json` (`POST /me/purchases 201`, `realScenario: "purchaseAfterDeposit"`) mais `INSUFFICIENT_BALANCE` (422, `realScenario: "purchaseAboveBalance"`), `INVALID_AMOUNT` (400, `realScenario: "purchaseBelowOneSatoshi"`) e `QUOTE_UNAVAILABLE` (503) em `POST /me/purchases`. Implementar os cenários reais citados no mapa nome → lambda do `bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/contract/WebClientFixturesContractTest.kt` (T077)
- [X] T154 [P] [US4] Teste de `BuyEstimate` em `web/core/src/commonTest/kotlin/com/kotlinbitcoins/web/core/estimate/BuyEstimateTest.kt`: `400.00 ÷ 400000.00` = `0,00100000 BTC`; truncamento (não arredondamento) na 8ª casa (`100.00 ÷ 300000.00` = `0,00033333`); `0.01 ÷ 400000.00` → `belowOneSatoshi = true` (zero BTC); a estimativa carrega a `Quote` usada (preço de venda + `quotedAt`)
- [X] T155 [P] [US4] Teste da compra no gateway em `web/data/src/commonTest/kotlin/com/kotlinbitcoins/web/data/gateway/HttpInvestmentGatewayPurchaseTest.kt`: 201 → `PurchaseResult` com BTC de 8 casas e cotação com todas as casas; 422 → `Rejected(InsufficientBalance)`; 400 → `Rejected(InvalidAmount)`; 503 → `Rejected(QuoteUnavailable)`; transporte → `Uncertain`
- [X] T156 [P] [US4] Teste de `PurchaseViewModel` em `web/state/src/commonTest/kotlin/com/kotlinbitcoins/web/state/purchase/PurchaseViewModelTest.kt`: valor inválido não avança (US4-5); a revisão mostra saldo, `BuyEstimate`, cotação e instante (US4-1); uma cotação nova em `Reviewing` recalcula a estimativa e o instante (US4-2); auto-refresh só em `Reviewing`; abaixo de 1 satoshi avisa e **mantém** a confirmação habilitada (US4-6, FR-016); `Succeeded` só com os campos da API, sem estimativa (US4-3); `InsufficientBalance` recarrega o saldo e oferece "Depositar" (US4-4); `InvalidAmount` → `Editing` com mensagem e complemento de satoshi; cotação indisponível na revisão → estimativa `null` com confirmação habilitada; `Rejected(QuoteUnavailable)` → "Tentar de novo" volta a `Reviewing` sem enviar (US4-7); 5 confirmações → 1 chamada (US4-8)
- [X] T157 [P] [US4] E2E em `web/e2e/src/test/kotlin/com/kotlinbitcoins/web/e2e/operations/PurchaseE2ETest.kt`: US4-1 a US4-7 (cotação controlada pelo quote-stub; indisponibilidade via `page.route`), verificando que a tela de resultado não contém a palavra "Estimativa" e com axe em edição, revisão, resultado e cada erro
- [X] T158 [P] [US4] E2E em `web/e2e/src/test/kotlin/com/kotlinbitcoins/web/e2e/operations/PurchaseSubmissionSafetyE2ETest.kt`: US4-8 / SC-004 com os mesmos cinco cenários de T145, mais o resultado incerto na compra

### Implementation for User Story 4

- [X] T159 [P] [US4] Implementar `BuyEstimate` (`of(amount: Money, quote: Quote): BuyEstimate` com `btc`, `quote`, `belowOneSatoshi`; divisão de `DecimalSupport` truncada em 8 casas) em `web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/estimate/BuyEstimate.kt`
- [X] T160 [P] [US4] Criar `PurchaseResult` (`investmentId`, `transactionId`, `amount`, `btcAmount`, `quote: QuotePrice`, `purchasedAt`, `balance`) em `web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/model/PurchaseResult.kt`
- [X] T161 [US4] Acrescentar `purchase(amount: Money): Outcome<OperationError, PurchaseResult>` em `web/state/src/commonMain/kotlin/com/kotlinbitcoins/web/state/port/InvestmentGateway.kt`, `PurchaseResponseDto` em `web/data/src/commonMain/kotlin/com/kotlinbitcoins/web/data/dto/PurchaseDtos.kt` e a implementação via `ApiCall.financial` em `web/data/src/commonMain/kotlin/com/kotlinbitcoins/web/data/gateway/HttpInvestmentGateway.kt`
- [X] T162 [US4] Implementar `PurchaseViewModel` (`OperationMachine` com revisão; `AutoRefresher` da cotação ativo só em `Reviewing`; `balance`, `quote`, `estimate`) em `web/state/src/commonMain/kotlin/com/kotlinbitcoins/web/state/purchase/PurchaseViewModel.kt`
- [X] T163 [P] [US4] Implementar `EstimateCard` (rótulo "Estimativa:", valor BTC, cotação e instante usados, aviso configurável, aviso de 1 satoshi, estado "Cotação indisponível"; sem `liveRegion`) em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/components/EstimateCard.kt`
- [X] T164 [US4] Acrescentar a `web/ui/src/commonMain/composeResources/values/strings.xml` os textos da compra ("Valor a investir", "Revisar compra", "Confirmar compra", "Valor estimado. A quantidade final usa a cotação do momento da compra.", "O valor provavelmente é pequeno demais para comprar a menor fração de bitcoin (0,00000001 BTC).", "Valor investido", "BTC comprado", "Cotação utilizada", "Compra realizada", "Saldo disponível")
- [X] T165 [US4] Implementar `PurchaseScreen` (stateless; etapas edição → revisão → resultado/erro/incerto; título com sufixo por etapa; foco no título de cada etapa; atalho "Depositar" no saldo insuficiente; `PauseToggle` na revisão) em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/purchase/PurchaseScreen.kt`
- [X] T166 [US4] Ligar `PurchaseViewModel` em `web/app/src/wasmJsMain/kotlin/com/kotlinbitcoins/web/app/AppContainer.kt` e o destino `Purchase` em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/shell/AppNavHost.kt`

**Checkpoint**: **MVP completo** (US1–US4). Validar com quickstart §3 (passos 1–15) e SC-001 (cadastro → depósito → primeira compra em menos de 3 min, no celular e no desktop).

---

## Phase 8: User Story 5 - Meus investimentos (Priority: P2)

**Goal**: listar investimentos ativos (ordem da API) com variação diferenciada além da cor, total e cotação de referência, com atualização automática.

**Independent Test**: dois investimentos (alta e queda) → campos, diferenciação sem depender de cor, total; conta vazia → estado vazio (spec US5).

### Tests for User Story 5 ⚠️

- [X] T167 [P] [US5] Teste de `InvestmentsViewModel` em `web/state/src/commonTest/kotlin/com/kotlinbitcoins/web/state/investments/InvestmentsViewModelTest.kt`: `Loaded` preserva a ordem da API (FR-019); lista vazia → `Empty` (US5-4); `QuoteUnavailable` → estado próprio com "Tentar de novo" (US5-5); atualização a cada 10 s, suspensa com a página oculta e com pausa (US5-6); anúncios só nas transições de disponibilidade
- [X] T168 [P] [US5] E2E em `web/e2e/src/test/kotlin/com/kotlinbitcoins/web/e2e/investments/InvestmentsE2ETest.kt`: US5-1 a US5-6 (variação `"+10,00%"` com símbolo e texto "alta", `"−5,00%"` com "queda", `"0,00%"` neutra, via `page.route` com a fixture `me-investments-200.json`), layout de cartões a 320 px e axe nos estados carregado, vazio e indisponível

### Implementation for User Story 5

- [X] T169 [US5] Implementar `InvestmentsViewModel` + `InvestmentsUiState` (`position: Loadable<Position>`, indisponível próprio, `isAutoRefreshPaused`) com `AutoRefresher` em `web/state/src/commonMain/kotlin/com/kotlinbitcoins/web/state/investments/InvestmentsViewModel.kt`
- [X] T170 [P] [US5] Implementar `VariationBadge` (por `Trend`: `Up` → "↑" + "alta", `Down` → "↓" + "queda", `Flat` → "–" + "sem variação"; cor de alta/queda do tema além do texto, FR-020) em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/components/VariationBadge.kt`
- [X] T171 [US5] Acrescentar a `web/ui/src/commonMain/composeResources/values/strings.xml` os textos de investimentos ("Data da compra", "Valor investido", "Cotação na compra", "Variação", "Valor bruto atual", "Valor atual total", "Cotação de referência", estado vazio "Você não tem investimentos ativos." + "Comprar bitcoin", descrição de leitura do cartão)
- [X] T172 [US5] Implementar `InvestmentsScreen` (stateless; `h1`; total + `QuoteBlock` de referência; `LazyColumn` de cartões com `semantics(mergeDescendants = true)` e texto completo; layout tabular no expandido conforme a variante de W-00 Resultado; `PauseToggle`; estado vazio com atalho) em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/investments/InvestmentsScreen.kt`
- [X] T173 [US5] Ligar `InvestmentsViewModel` em `web/app/src/wasmJsMain/kotlin/com/kotlinbitcoins/web/app/AppContainer.kt` e o destino `Investments` em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/shell/AppNavHost.kt`

**Checkpoint**: US5 completa. A pessoa acompanha o resultado das compras.

---

## Phase 9: User Story 6 - Resgate (Priority: P2)

**Goal**: ver o disponível (com cotação e instante), "Resgatar tudo", revisar com estimativa rotulada, confirmar e ver liquidações e reinvestimento.

**Independent Test**: dois investimentos → resgatar um valor que liquida o primeiro inteiro e o segundo em parte → valor, BTC vendido, cotação, saldo, duas liquidações (total/parcial) e reinvestimento (spec US6).

### Tests for User Story 6 ⚠️

- [X] T174 [P] [US6] Criar `web/contract-fixtures/success/me-redemptions-201-partial.json` (duas liquidações, uma com `isPartial: true`, e `reinvestment` preenchido) e `me-redemptions-201-full.json` (`reinvestment: null`) e registrar no `manifest.json` (`POST /me/redemptions 201`, `realScenario: "redemptionPartial"` e `"redemptionFull"`) mais `REDEMPTION_EXCEEDS_AVAILABLE` (422, `realScenario: "redemptionAboveAvailable"`), `INVALID_AMOUNT` (400) e `QUOTE_UNAVAILABLE` (503) em `POST /me/redemptions`. Implementar os cenários reais citados no mapa nome → lambda do `bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/contract/WebClientFixturesContractTest.kt` (T077)
- [X] T175 [P] [US6] Teste de `RedeemEstimate` em `web/core/src/commonTest/kotlin/com/kotlinbitcoins/web/core/estimate/RedeemEstimateTest.kt`: `500.00 ÷ 440000.00` truncado em 8 casas (`0,00113636`); `fromRedeemAll` propagado; carrega a `Quote` (preço de compra + instante)
- [X] T176 [P] [US6] Teste do resgate no gateway em `web/data/src/commonTest/kotlin/com/kotlinbitcoins/web/data/gateway/HttpInvestmentGatewayRedemptionTest.kt`: 201 parcial → `RedemptionResult` com liquidações e `Reinvestment`; 201 total → `reinvestment == null`; 422 → `Rejected(RedemptionExceedsAvailable)`; 400 → `Rejected(InvalidAmount)`; 503 → `Rejected(QuoteUnavailable)`; transporte → `Uncertain`
- [X] T177 [P] [US6] Teste de `RedemptionViewModel` em `web/state/src/commonTest/kotlin/com/kotlinbitcoins/web/state/redemption/RedemptionViewModelTest.kt`: disponível = `totalCurrentValue` com cotação de compra e instante, atualizado a cada 10 s (US6-1); "Resgatar tudo" preenche o campo com o disponível exibido sem confirmar (US6-5); a revisão mostra `RedeemEstimate` e o aviso, mais o aviso de queda quando `fromRedeemAll` (US6-2); `Succeeded` com liquidações e reinvestimento (US6-3), sem seção quando `null` (US6-4); `RedemptionExceedsAvailable` → recarrega o disponível, mostra o valor atualizado e reabilita "Resgatar tudo" (US6-5/6); conta vazia → `R$ 0,00` + convite e o envio continua permitido (US6-7); `InvalidAmount`/`QuoteUnavailable` com retry (US6-8); 5 confirmações → 1 chamada (US6-9)
- [X] T178 [P] [US6] E2E em `web/e2e/src/test/kotlin/com/kotlinbitcoins/web/e2e/operations/RedemptionE2ETest.kt`: US6-1 a US6-8 (dados preparados com `ApiClient`: depósito + duas compras com cotações diferentes no quote-stub), com axe em edição, revisão, resultado com reinvestimento e cada erro
- [X] T179 [P] [US6] E2E em `web/e2e/src/test/kotlin/com/kotlinbitcoins/web/e2e/operations/RedemptionSubmissionSafetyE2ETest.kt`: US6-9 / SC-004 com os cinco cenários de T145, mais o resultado incerto no resgate

### Implementation for User Story 6

- [X] T180 [P] [US6] Implementar `RedeemEstimate` (`of(amount: Money, quote: Quote, fromRedeemAll: Boolean)`, truncado em 8 casas, "aproximação que **não** reproduz a liquidação FIFO") em `web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/estimate/RedeemEstimate.kt`
- [X] T181 [P] [US6] Criar `RedemptionResult` (`requestId`, `amount`, `btcSold`, `quote`, `balance`, `occurredAt`, `liquidations: List<Liquidation(investmentId, amount, btcAmount, isPartial)>`, `reinvestment: Reinvestment?(investmentId, btcAmount, investedAmount, purchaseQuote, purchasedAt)`) em `web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/model/RedemptionResult.kt`
- [X] T182 [US6] Acrescentar `redeem(amount: Money): Outcome<OperationError, RedemptionResult>` em `web/state/src/commonMain/kotlin/com/kotlinbitcoins/web/state/port/InvestmentGateway.kt`, `RedemptionResponseDto` (+ `LiquidationDto`, `ReinvestmentDto` anulável) em `web/data/src/commonMain/kotlin/com/kotlinbitcoins/web/data/dto/RedemptionDtos.kt` e a implementação via `ApiCall.financial` em `web/data/src/commonMain/kotlin/com/kotlinbitcoins/web/data/gateway/HttpInvestmentGateway.kt`
- [X] T183 [US6] Implementar `RedemptionViewModel` (`OperationMachine` com revisão; `available: Loadable<Position>` com `AutoRefresher`; `onRedeemAll`; `estimate`) em `web/state/src/commonMain/kotlin/com/kotlinbitcoins/web/state/redemption/RedemptionViewModel.kt`
- [X] T184 [US6] Acrescentar a `web/ui/src/commonMain/composeResources/values/strings.xml` os textos do resgate ("Disponível para resgate: %1$s", "Valor estimado que muda com a cotação.", "Resgatar tudo", "Revisar resgate", "Confirmar resgate", "Valor estimado. A quantidade final usa a cotação do momento do resgate.", "Se a cotação cair antes da confirmação, o valor pode ficar acima do disponível.", "Valor resgatado", "BTC vendido", "Investimentos liquidados", "Total"/"Parcial", "Reinvestimento", explicação "O BTC que sobrou do investimento parcialmente resgatado continua investido, com a cotação e a data da compra original.", "Resgate realizado")
- [X] T185 [P] [US6] Implementar `RedemptionResultContent` (resumo, lista de liquidações com "Total"/"Parcial" em texto e seção de reinvestimento com explicação só quando existir) em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/redemption/RedemptionResultContent.kt`
- [X] T186 [US6] Implementar `RedemptionScreen` (stateless; `h1` "Resgatar"; bloco do disponível com cotação e instante; `AmountField` + "Resgatar tudo"; revisão com `EstimateCard`; resultado/erros/incerto; `PauseToggle`; estado vazio com convite para comprar) em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/redemption/RedemptionScreen.kt`
- [X] T187 [US6] Ligar `RedemptionViewModel` em `web/app/src/wasmJsMain/kotlin/com/kotlinbitcoins/web/app/AppContainer.kt` e o destino `Redemption` em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/shell/AppNavHost.kt`

**Checkpoint**: ciclo de investimento completo (compra → acompanhamento → resgate).

---

## Phase 10: User Story 7 - Extrato (Priority: P2)

**Goal**: transações do período padrão ou filtrado (datas de Brasília, inclusivas), da mais recente para a mais antiga, com grupo de resgate somado e filtro na URL.

**Independent Test**: depósito, compra e resgate com duas linhas de resgate + reinvestimento → extrato padrão, ordem, agrupamento, filtro válido, intervalo inválido e estado vazio (spec US7).

### Tests for User Story 7 ⚠️

- [X] T188 [P] [US7] Criar `web/contract-fixtures/success/me-statement-200.json` (depósito, compra e um grupo com duas linhas `REDEMPTION` + uma `REINVESTMENT` com o mesmo `requestId`) e `me-statement-200-empty.json` e registrar no `manifest.json` (`GET /me/statement 200`, `realScenario: "statementAfterRedemption"`) mais `INVALID_DATE_RANGE` em `GET /me/statement 422` (`realScenario: "statementInvalidRange"`). Implementar os cenários reais citados no mapa nome → lambda do `bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/contract/WebClientFixturesContractTest.kt` (T077)
- [X] T189 [P] [US7] Teste de `StatementGrouper` em `web/core/src/commonTest/kotlin/com/kotlinbitcoins/web/core/statement/StatementGrouperTest.kt`: entradas sem `requestId` → `Single` na ordem da API; o mesmo `requestId` vira um `RedemptionGroup` na posição da primeira entrada; `totalRedeemed` = soma exata **só** das linhas `REDEMPTION` (`"250.00" + "250.01"` = `R$ 500,01`, sem a de reinvestimento); grupo cortado pelo filtro (só uma linha) continua `RedemptionGroup`
- [X] T190 [P] [US7] Teste de `StatementFilter` em `web/core/src/commonTest/kotlin/com/kotlinbitcoins/web/core/statement/StatementFilterTest.kt`: "ambas obrigatórias juntas" (só uma → `InvalidRange`), datas inválidas → `InvalidRange`, `from > to` → `InvalidRange`, `from == to` válido
- [X] T191 [P] [US7] Teste de `HttpStatementGateway` em `web/data/src/commonTest/kotlin/com/kotlinbitcoins/web/data/gateway/HttpStatementGatewayTest.kt`: `Default` → `GET /me/statement` sem query; `Range` → `?from=2026-10-01&to=2026-11-09`; mapeia `type`, `btcAmount`/`quote` nulos, `requestId`; 422 → `InvalidDateRange`
- [X] T192 [P] [US7] Teste de `StatementViewModel` em `web/state/src/commonTest/kotlin/com/kotlinbitcoins/web/state/statement/StatementViewModelTest.kt`: carrega pelo filtro da rota; exibe `from`/`to` da resposta (US7-1); aplicar filtro válido emite navegação com **substituição** de `Statement(from, to)` (US7-5); inválido local ou `INVALID_DATE_RANGE` da API → erro nos campos com o extrato anterior mantido (US7-6, FR-030); limpar → `Default`; vazio → `Empty` com o período (US7-7)
- [X] T193 [P] [US7] E2E em `web/e2e/src/test/kotlin/com/kotlinbitcoins/web/e2e/statement/StatementE2ETest.kt`: US7-1 a US7-7; o filtro `#/extrato?de=…&ate=…` sobrevive a recarregar e a voltar/avançar; expiração de sessão com filtro aplicado → login → volta ao extrato com o mesmo filtro (US1-6, SC-009); 300 lançamentos (`ApiClient`) navegáveis por teclado; Edge Case "sem conexão ao abrir uma tela": com o app carregado, `context.setOffline(true)` e navegar para `#/extrato` → mensagem "Não foi possível conectar…" com "Tentar de novo" e navegação intacta; `setOffline(false)` + "Tentar de novo" → extrato carregado; axe nos estados carregado, vazio, erro de intervalo e sem conexão

### Implementation for User Story 7

- [X] T194 [P] [US7] Criar `Statement.kt` (`from`, `to`, `entries`), `StatementEntry` e `EntryType` (`Deposit`, `Purchase`, `Redemption`, `Reinvestment`) em `web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/model/Statement.kt`
- [X] T195 [US7] Implementar `GroupedStatement`, `StatementItem` (`Single`, `RedemptionGroup(requestId, entries, totalRedeemed, occurredAt)`) e `StatementGrouper.group` em `web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/statement/StatementGrouper.kt`
- [X] T196 [US7] Criar a porta `StatementGateway` (`statement(filter: StatementFilter): Outcome<ApiError, Statement>`) em `web/state/src/commonMain/kotlin/com/kotlinbitcoins/web/state/port/StatementGateway.kt`, os DTOs em `web/data/src/commonMain/kotlin/com/kotlinbitcoins/web/data/dto/StatementDtos.kt` e `HttpStatementGateway` em `web/data/src/commonMain/kotlin/com/kotlinbitcoins/web/data/gateway/HttpStatementGateway.kt`
- [X] T197 [US7] Implementar `StatementViewModel` + `StatementUiState` (`filter`, `filterInput`, `filterError`, `statement: Loadable<GroupedStatement>`, último extrato válido preservado) em `web/state/src/commonMain/kotlin/com/kotlinbitcoins/web/state/statement/StatementViewModel.kt`
- [X] T198 [US7] Acrescentar a `web/ui/src/commonMain/composeResources/values/strings.xml` os textos do extrato ("Período: %1$s a %2$s", "Data inicial", "Data final", "dd/mm/aaaa", "Aplicar filtro", "Limpar filtro", tipos "Depósito"/"Compra"/"Resgate"/"Reinvestimento", "Resgate — total %1$s", "não altera o saldo", estado vazio "Nenhum lançamento no período de %1$s a %2$s.")
- [X] T199 [P] [US7] Implementar `StatementEntryCard` e `RedemptionGroupCard` (texto completo com `mergeDescendants`; BTC e cotação só quando houver; rótulo "não altera o saldo" no reinvestimento) em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/statement/StatementItems.kt`
- [X] T200 [US7] Implementar `StatementScreen` (stateless; `h1`; período exibido; formulário de filtro com dois campos `dd/mm/aaaa` e erro associado a ambos; "Aplicar"/"Limpar"; `LazyColumn`; estados carregando/vazio/erro) em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/statement/StatementScreen.kt`
- [X] T201 [US7] Ligar `HttpStatementGateway` e `StatementViewModel` em `web/app/src/wasmJsMain/kotlin/com/kotlinbitcoins/web/app/AppContainer.kt` e o destino `Statement(from, to)` com navegação por substituição ao filtrar em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/shell/AppNavHost.kt`

**Checkpoint**: US1–US7 completas.

> **Nota (2026-11-15, T193/T201)**: o `bindToBrowserNavigation` (Experimental) fazia `pushState` mesmo quando a entrada do NavController era substituída, então aplicar o filtro empilhava em vez de substituir. Foi trocado pelo roteador próprio previsto em research W-04 (`HashRouter` em `:app` + `HistorySync` em `:core/navigation`, com testes). O E2E `US7-5 applying the filter replaces the history entry` cobre a substituição.

---

## Phase 11: User Story 8 - Mercado (Priority: P3)

**Goal**: volume do dia (8 casas, data) e gráfico de 24 h com duas séries diferenciáveis sem cor, lacunas sem interpolação e alternativa textual.

**Independent Test**: histórico com um intervalo sem registro → volume, duas linhas, lacuna sem ligação e tabela/resumo textual (spec US8).

### Tests for User Story 8 ⚠️

- [X] T202 [P] [US8] Criar `web/contract-fixtures/success/market-daily-volume-200.json` (`boughtBtc: "0.00300000"`, `soldBtc: "0.00050000"`), `quotes-history-200-with-gap.json` (pontos de 10 em 10 min com um slot ausente) e `quotes-history-200-empty.json` e registrar no `manifest.json` (`GET /market/daily-volume 200` com `realScenario: "dailyVolume"`; `GET /quotes/history 200` com `realScenario: "quoteHistory"`). Implementar os cenários reais citados no mapa nome → lambda do `bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/contract/WebClientFixturesContractTest.kt` (T077)
- [X] T203 [P] [US8] Teste de séries em `web/core/src/commonTest/kotlin/com/kotlinbitcoins/web/core/market/ChartSeriesTest.kt`: quebra o segmento quando `slotAt[i+1] − slotAt[i] > 10 min` (FR-033); ponto isolado vira segmento de 1; nenhum ponto inventado; `ChartSummary` com mínimo, máximo e último exatos por série (FR-034); `TableRows` com `Missing(slotAt)` para cada slot ausente entre o primeiro e o último ponto
- [X] T204 [P] [US8] Testes de gateway em `web/data/src/commonTest/kotlin/com/kotlinbitcoins/web/data/gateway/HttpMarketGatewayTest.kt` (volume → `DailyVolume` com 8 casas e data) e `HttpQuoteGatewayHistoryTest.kt` (pontos em ordem, vazio)
- [X] T205 [P] [US8] Teste de `MarketViewModel` em `web/state/src/commonTest/kotlin/com/kotlinbitcoins/web/state/market/MarketViewModelTest.kt`: volume e histórico carregam ao abrir e falham de forma independente; histórico vazio → `Empty` sem afetar o volume (US8-5); "Atualizar" recarrega os dois (FR-035); sem atualização automática
- [X] T206 [P] [US8] Teste da geometria em `web/ui/src/commonTest/kotlin/com/kotlinbitcoins/web/ui/chart/ChartGeometryTest.kt`: mapeia instantes para X dentro de 24 h e preços para Y entre mínimo e máximo; cada segmento gera um caminho separado; a conversão `QuotePrice → Float` fica só em `ChartGeometry`
- [X] T207 [P] [US8] E2E em `web/e2e/src/test/kotlin/com/kotlinbitcoins/web/e2e/market/MarketE2ETest.kt`: US8-1 (totais com 8 casas e data), US8-2/US8-3 (com `page.route` servindo `quotes-history-200-with-gap.json`: legenda, resumo e tabela com a linha "sem registro"; screenshot do gráfico comparado, **só no Chromium**, com a referência em `web/e2e/src/test/resources/snapshots/market/price-history-gap.png` (tolerância de 1% de pixels; a referência é gerada na primeira execução com `-PupdateSnapshots=true` e revisada antes do commit), para a lacuna e os estilos de linha), US8-4 ("Ver dados em tabela" por teclado e resumo no `contentDescription`), US8-5 e axe

### Implementation for User Story 8

- [X] T208 [P] [US8] Criar `DailyVolume.kt` (`date: CalendarDate`, `boughtBtc`, `soldBtc`) e `QuoteHistory.kt` (`points: List<HistoryPoint(slotAt, buyPrice, sellPrice)>`) em `web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/model/`
- [X] T209 [US8] Implementar `ChartSeries.segments`, `ChartData`, `ChartSummary` e `TableRows` em `web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/market/ChartSeries.kt` (data-model §6)
- [X] T210 [US8] Criar a porta `MarketGateway` (`dailyVolume()`) em `web/state/src/commonMain/kotlin/com/kotlinbitcoins/web/state/port/MarketGateway.kt` e acrescentar `history()` em `web/state/src/commonMain/kotlin/com/kotlinbitcoins/web/state/port/QuoteGateway.kt`. Criar os DTOs em `web/data/src/commonMain/kotlin/com/kotlinbitcoins/web/data/dto/MarketDtos.kt` e implementar `HttpMarketGateway` em `web/data/src/commonMain/kotlin/com/kotlinbitcoins/web/data/gateway/HttpMarketGateway.kt` e `history()` em `web/data/src/commonMain/kotlin/com/kotlinbitcoins/web/data/gateway/HttpQuoteGateway.kt`
- [X] T211 [US8] Implementar `MarketViewModel` + `MarketUiState` (`volume: Loadable<DailyVolume>`, `history: Loadable<ChartData>`, `isRefreshing`, `onRefresh`) em `web/state/src/commonMain/kotlin/com/kotlinbitcoins/web/state/market/MarketViewModel.kt`
- [X] T212 [US8] Implementar `ChartGeometry.kt` e `PriceHistoryChart.kt` em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/chart/`: `Canvas`; compra em linha contínua + círculo; venda tracejada (`PathEffect.dashPathEffect`) + quadrado; um `Path` por segmento; eixo X com marcas a cada 4 h `HH:mm` (Brasília); eixo Y com 4 marcas por `QuotePrice.format()`; legenda em texto com amostras; `semantics { contentDescription = resumo }`; sem animação
- [X] T213 [US8] Acrescentar a `web/ui/src/commonMain/composeResources/values/strings.xml` os textos do mercado ("Volume de hoje (%1$s)", "BTC comprados", "BTC vendidos", "Preço de compra", "Preço de venda", "Últimas 24 horas (horário de Brasília)", "Ver dados em tabela", "Ocultar tabela", "sem registro", modelo do resumo textual, estado vazio "Sem dados de cotação nas últimas 24 horas.", "Atualizar")
- [X] T214 [US8] Implementar `MarketScreen` (stateless; `h1`; bloco de volume; gráfico; disclosure "Ver dados em tabela" com a tabela de `TableRows`; botão "Atualizar"; estados independentes) em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/market/MarketScreen.kt`
- [X] T215 [US8] Ligar `HttpMarketGateway` e `MarketViewModel` em `web/app/src/wasmJsMain/kotlin/com/kotlinbitcoins/web/app/AppContainer.kt` e o destino `Market` em `web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/shell/AppNavHost.kt`

**Checkpoint**: todas as user stories (US1–US8) completas e testáveis de forma independente.

---

## Phase 12: Polish & Cross-Cutting Concerns

**Purpose**: critérios de sucesso transversais (SC-002, SC-007, SC-008, SC-010), segurança, CI de E2E e limpeza.

- [ ] T216 [P] Criar `web/contract-fixtures/success/limits-200.json` para `GET /me/balance` (`"99999999999.99"`) e `GET /me/investments` (`btcAmount: "0.00000001"`, cotação com 8 casas), registrar no `manifest.json` e criar `web/data/src/commonTest/kotlin/com/kotlinbitcoins/web/data/mapper/PrecisionLimitsTest.kt` (fixture → modelo → `format()` com exatamente os mesmos dígitos, SC-002). Ativar a verificação 5 de `contracts/fixtures-manifest.md` em `bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/contract/WebClientFixturesContractTest.kt` (toda operação do manifest tem ao menos uma fixture de sucesso)
- [ ] T217 [P] E2E em `web/e2e/src/test/kotlin/com/kotlinbitcoins/web/e2e/layout/ResponsiveLayoutE2ETest.kt`: as 9 telas (com dados) em 320, 375, 768, 1280 e 1920 px sem rolagem horizontal (`NoHorizontalScroll`), com a navegação certa por faixa (menu/rail/drawer) e o destino atual indicado (FR-009, FR-044, SC-008)
- [ ] T218 [P] E2E em `web/e2e/src/test/kotlin/com/kotlinbitcoins/web/e2e/layout/Zoom200E2ETest.kt`: as 9 telas a 640 × 400 com `deviceScaleFactor` 2 (zoom 200%) sem perda de conteúdo nem rolagem horizontal (SC-008)
- [ ] T219 [P] E2E em `web/e2e/src/test/kotlin/com/kotlinbitcoins/web/e2e/a11y/AxeAllScreensE2ETest.kt`: axe em cada tela × estado (carregando, vazio, erro, carregado, revisão, resultado) com `emulateMedia(colorScheme = LIGHT/DARK)` e `reducedMotion = REDUCE`. Com movimento reduzido, nenhuma animação de transição (FR-045)
- [ ] T220 [P] E2E em `web/e2e/src/test/kotlin/com/kotlinbitcoins/web/e2e/a11y/KeyboardOnlyE2ETest.kt`: cadastro, login, sair, depósito, compra, resgate, investimentos, extrato (filtro) e mercado (tabela) concluídos só com teclado; o foco nunca se perde; foco no primeiro erro/resultado; `document.title` único por tela; nenhum nome acessível no espelho DOM em inglês: falha se algum nome acessível contiver uma palavra da lista `Close`, `Open`, `Navigation`, `Dismiss`, `Loading`, `Show`, `Hide`, `Back`, `Clear`, `Selected`, desde que o texto não venha de `strings.xml` (os textos do app, como "Menu", são carregados do `strings.xml` e ficam fora da verificação). Termos que existem nas duas línguas, como `Menu` e `Error`, não entram na lista e são verificados no spike (T022) (FR-048, SC-007, parte automatizável)
- [ ] T221 [P] E2E em `web/e2e/src/test/kotlin/com/kotlinbitcoins/web/e2e/security/TokenExposureE2ETest.kt` (FR-008): durante um fluxo completo, o token não aparece em nenhuma URL, console, `localStorage`, cookie, `Referer` ou fragmento; só existe em `sessionStorage` e no cabeçalho `Authorization`
- [ ] T222 [P] E2E em `web/e2e/src/test/kotlin/com/kotlinbitcoins/web/e2e/performance/LoadTimeE2ETest.kt` (Chromium, CDP `Network.emulateNetworkConditions` com o perfil "4G típico" de research.md W-15: 9.000 kbps de descida, 1.500 kbps de subida, 60 ms de RTT). (a) Navegação com o app carregado: em 20 aberturas de tela, conteúdo ou carregamento em ≤ 1 s e dados em ≤ 3 s (p95). (b) Primeira carga sem cache: em 5 cargas frias, `#boot` visível em ≤ 1 s e painel utilizável em ≤ 6 s (SC-010). Medir a soma em brotli de `*.wasm`, `*.js` e da fonte servidos na primeira carga e **falhar** acima de 3,5 MB. Registrar os tamanhos (br/gz) em `specs/002-frontend-web/research.md` W-15
- [ ] T223 Adicionar o job `web-e2e` em `.github/workflows/ci.yml` (`needs: [build, web]`; `docker compose --profile stub up -d --build --wait` com `.env` gerado de `.env.example` com valores de teste; `./gradlew -p web :e2e:e2eTest` após `java -cp … com.microsoft.playwright.CLI install --with-deps`; publicar `web/e2e/build/reports/` como artefato; `docker compose down -v` em `always()`)
- [ ] T224 Confirmar e registrar em `specs/002-frontend-web/research.md` os pontos deixados para a primeira build: nomes com hash da distribuição e regra de cache (W-15), resultado do validador OpenAPI 3.1 (W-12) e diretivas de CSP efetivamente necessárias (W-15)
- [ ] T225 Criar `specs/002-frontend-web/checklists/a11y-release.md` com a matriz de quickstart.md §6 e executar o checklist manual completo (NVDA, VoiceOver macOS/iOS, só teclado, zoom 200%, tema escuro, movimento reduzido) sobre o app final (SC-007)
- [ ] T226 Remover `web/spike-a11y/` e a entrada `"spike-a11y"` de `web/settings.gradle.kts`, mantendo o resultado registrado em research.md W-00
- [ ] T227 [P] Acrescentar a `README.md` (raiz) a seção "Cliente web" (como rodar com `docker compose --profile stub up`, endereço `http://localhost:8081`, `cd web && ./gradlew check`, `:e2e:e2eTest`, links para `specs/002-frontend-web/`)
- [ ] T228 Executar `specs/002-frontend-web/quickstart.md` de ponta a ponta (§1 a §7) e corrigir divergências. Confirmar `./gradlew check` verde na raiz e em `web/` e o CI verde nos jobs `build`, `web` e `web-e2e`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sem dependências.
- **Gate W-00 (Phase 2)**: depende do Setup. **Bloqueia** toda tarefa de `:ui`, `:app`, entrega, E2E e user stories.
- **Foundational (Phase 3)**: depende do Setup. As seções `:core`, `:state`, `:data`, os adaptadores e o teste das fixtures na API (T024–T077) MAY rodar durante a Phase 2. As seções tema/componentes/shell, borda, entrega e E2E (T078–T102) exigem T023 = GO.
- **User Stories (Phases 4–11)**: dependem da Foundational completa.
- **Polish (Phase 12)**: depende das stories desejadas.

### User Story Dependencies

- **US1 (P1)**: só Foundational. Todas as outras precisam de login para o E2E (o login dos E2E das demais stories usa `ApiClient` + sessão injetada pela UI de login de US1).
- **US2 (P1)**: Foundational + US1 (para E2E). A **saudação com nome** depende de T122–T126 (emenda `GET /me` na 001). Sem eles, o painel mostra "Olá!" e todos os outros cenários de US2 já podem ser validados.
- **US3 (P1)**: Foundational + US1. `AccountGateway` criado em US2 (T135). Se US3 for feita antes de US2, criar a porta aqui.
- **US4 (P1)**: Foundational + US1. Usa `QuoteGateway`/`InvestmentGateway` de US2 (T135–T136) e `UncertainOutcomeBlock` de US3 (T149).
- **US5 (P2)**: Foundational + US1. Reusa `InvestmentGateway.position()` de US2.
- **US6 (P2)**: Foundational + US1. Reusa `InvestmentGateway` (US2), `EstimateCard` (US4) e `UncertainOutcomeBlock` (US3).
- **US7 (P2)**: Foundational + US1. Independe das demais (os E2E preparam os dados via `ApiClient`).
- **US8 (P3)**: Foundational + US1. Estende `QuoteGateway` (US2).

### Within Each User Story

- Fixtures e testes (Red) antes da implementação. Os testes MUST falhar primeiro.
- `:core` (modelos/estimativas) → porta em `:state` → DTO/gateway em `:data` → ViewModel → strings → tela → ligação no `AppContainer`/`AppNavHost`.
- `strings.xml`, `AppContainer.kt` e `AppNavHost.kt` são arquivos compartilhados: as tarefas que os editam **não** são [P] entre si, nem entre stories.

### Parallel Opportunities

- Phase 1: T005–T013 em paralelo depois de T001–T004.
- Phase 2: T015–T019 em paralelo. A Phase 2 inteira em paralelo com T024–T077 da Phase 3.
- Phase 3: todos os testes de `:core` (T024–T033) juntos; value objects T036–T039 juntos; testes de `:state` (T047–T051) juntos; adaptadores T070–T075 juntos; componentes T085–T090 juntos.
- Depois da Foundational, com o padrão "Foundational + US1", US5, US7 e US8 podem seguir em paralelo com US3/US4 (equipes diferentes), respeitando os arquivos compartilhados.

---

## Parallel Example: Phase 3 (Foundational, núcleo)

```bash
# Red: núcleo de precisão e formatação, todos de uma vez
Task: "Teste de Money em web/core/src/commonTest/kotlin/com/kotlinbitcoins/web/core/money/MoneyTest.kt"
Task: "Teste de BtcAmount em web/core/src/commonTest/kotlin/com/kotlinbitcoins/web/core/money/BtcAmountTest.kt"
Task: "Teste de QuotePrice em web/core/src/commonTest/kotlin/com/kotlinbitcoins/web/core/money/QuotePriceTest.kt"
Task: "Teste de AmountInputParser em web/core/src/commonTest/kotlin/com/kotlinbitcoins/web/core/format/AmountInputParserTest.kt"
Task: "Teste de AppRoute em web/core/src/commonTest/kotlin/com/kotlinbitcoins/web/core/navigation/AppRouteTest.kt"
Task: "Teste de ReturnDestination em web/core/src/commonTest/kotlin/com/kotlinbitcoins/web/core/navigation/ReturnDestinationTest.kt"

# Green: value objects em paralelo (após DecimalSupport, T035)
Task: "Implementar Money em web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/money/Money.kt"
Task: "Implementar BtcAmount em web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/money/BtcAmount.kt"
Task: "Implementar QuotePrice em web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/money/QuotePrice.kt"
Task: "Implementar VariationPercent em web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/money/VariationPercent.kt"
```

## Parallel Example: User Story 4

```bash
# Red: todos os testes da US4 juntos
Task: "Fixtures de compra em web/contract-fixtures/success/me-purchases-201.json + manifest.json"
Task: "Teste de BuyEstimate em web/core/src/commonTest/kotlin/com/kotlinbitcoins/web/core/estimate/BuyEstimateTest.kt"
Task: "Teste da compra no gateway em web/data/src/commonTest/kotlin/com/kotlinbitcoins/web/data/gateway/HttpInvestmentGatewayPurchaseTest.kt"
Task: "Teste de PurchaseViewModel em web/state/src/commonTest/kotlin/com/kotlinbitcoins/web/state/purchase/PurchaseViewModelTest.kt"
Task: "E2E em web/e2e/src/test/kotlin/com/kotlinbitcoins/web/e2e/operations/PurchaseE2ETest.kt"
Task: "E2E em web/e2e/src/test/kotlin/com/kotlinbitcoins/web/e2e/operations/PurchaseSubmissionSafetyE2ETest.kt"

# Green: arquivos independentes em paralelo
Task: "Implementar BuyEstimate em web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/estimate/BuyEstimate.kt"
Task: "Criar PurchaseResult em web/core/src/commonMain/kotlin/com/kotlinbitcoins/web/core/model/PurchaseResult.kt"
Task: "Implementar EstimateCard em web/ui/src/commonMain/kotlin/com/kotlinbitcoins/web/ui/components/EstimateCard.kt"
```

---

## Implementation Strategy

### MVP First (US1 → US4)

A spec marca US1–US4 como P1 ("junto com cadastro, painel e depósito forma o MVP").

1. Phase 1: Setup.
2. Phase 2: **Gate W-00** (com `:core`/`:state`/`:data` da Phase 3 em paralelo). **NO-GO → parar e revisar o plan.**
3. Phase 3: Foundational (restante).
4. US1 → **validar**: cadastro, login, sessão, expiração, multiaba.
5. US2 → **validar**: painel, atualização automática, cotação indisponível. Aplicar a emenda `GET /me` (T122–T126) para a saudação.
6. US3 → **validar**: depósito e envio único.
7. US4 → **PARAR e VALIDAR o MVP** com quickstart §3 e SC-001.

### Incremental Delivery

1. MVP (US1–US4).
2. US5 (investimentos) → demo.
3. US6 (resgate) → demo do ciclo completo.
4. US7 (extrato) → demo.
5. US8 (mercado) → demo.
6. Polish: responsividade, zoom, axe completo, teclado, segurança do token, desempenho, CI de E2E e checklist manual de acessibilidade.

### Parallel Team Strategy

1. Todos: Setup. Dev A: Gate W-00 (spike + matriz manual). Dev B: `:core`/`:state`/`:data` da Foundational.
2. Todos: restante da Foundational (após GO).
3. Dev A: US1 → US2 → US4. Dev B: US3 → US5 → US7. Dev C (ou B depois): US6 → US8.

---

## Notes

- [P] = arquivos diferentes, sem dependência de tarefa incompleta.
- [USn] liga a tarefa à user story para rastreabilidade.
- Verificar que os testes falham antes de implementar (Princípio IV). Toda correção de defeito vem com um teste que o reproduz.
- SC-011 (teste de usabilidade com participantes) não é automatizável e fica fora desta lista. Deve ser conduzido após a Phase 12 com o app implantado.
- Decisões tomadas aqui que não estavam explícitas nos artefatos:
  1. `QuoteAvailabilityTracker` (`:state`) centraliza a regra de anúncio só em transição (FR-047), reutilizada por quatro ViewModels.
  2. `UncertainOutcomeBlock`, `EstimateCard` e `VariationBadge` são componentes compartilhados criados na primeira story que os usa (US3, US4 e US5).
  3. O contexto do `docker build` do cliente é `web/` (e não a raiz), e o comando do quickstart §7 é corrigido em T099.
  4. Os E2E das stories 2–8 preparam dados pela API (`ApiClient`) e simulam falhas por `page.route` com as mesmas fixtures do consumidor, sem depender de derrubar serviços, exceto nos testes marcados `@Tag("slow")`.
  5. A emenda `GET /me` (T122–T126) toca código da 001 (`application`, `api`, `bootstrap`) e está na US2 porque só ela a exige. Ela pode ser feita por quem mantém a API, em paralelo com as Phases 1–4.
