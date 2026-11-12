# Research: Cliente Web do Investimento em Bitcoins

**Feature**: `002-frontend-web` | **Date**: 2026-11-11 | **Plan**: [plan.md](./plan.md)

Formato de cada item: **Decision** / **Rationale** / **Alternatives considered**. Os IDs (`W-xx`) são citados no plan, no data-model e nos contratos.

---

## W-00 Gate de acessibilidade — spike go/no-go (OBRIGATÓRIO, bloqueia a implementação)

**Contexto**: o alvo web do Compose Multiplatform está em **Beta**. O suporte a acessibilidade chegou como "inicial" na 1.9 e foi ampliado na 1.9.3 (navegação por teclado, rótulos e leitura por leitor de tela, ligado por padrão). Não encontramos documentação que descreva, para a 1.12, quais papéis ARIA o espelho DOM de acessibilidade expõe (tabela, cabeçalhos, `aria-live`, `aria-invalid`/`aria-describedby`). WCAG 2.1 AA é MUST na Constitution. Por isso a viabilidade da stack só pode ser decidida com um protótipo.

**Decision**: a primeira tarefa da implementação é um **spike descartável** em `web/spike-a11y/`, com a mesma stack e versões do W-01. Ele tem três telas:

1. **Login**: e-mail, senha e botão "Entrar". O erro genérico "E-mail ou senha incorretos." aparece em região de alerta.
2. **Formulário com erro por campo**: cadastro com nome, e-mail e senha. Ao enviar inválido, cada campo mostra sua mensagem e o foco vai ao primeiro campo inválido.
3. **Tabela**: 30 linhas no formato do extrato (tipo, valor, BTC, cotação, data/hora), com um grupo de resgate.

E também: um anúncio `aria-live` ("Cotação indisponível no momento" / "Cotação restabelecida") disparado por botão, e um contador de 10 s que atualiza um valor sem anunciá-lo.

**Matriz de teste**: NVDA (última versão) + Firefox e Chrome no Windows; VoiceOver + Safari e Chrome no macOS; VoiceOver + Safari no iOS; apenas teclado (Chrome, Firefox, Safari); zoom do navegador em 200% em janela de 1280 px; `axe-core` via Playwright (W-17).

**Critérios de aprovação (GO exige TODOS)**:

| # | Critério | Referência |
|---|----------|------------|
| A1 | Todo controle é alcançável e acionável só com teclado (Tab/Shift+Tab, Enter, Espaço), em ordem lógica, sem armadilha de foco. | WCAG 2.1.1, 2.1.2, 2.4.3 |
| A2 | O foco é sempre visível e tem contraste ≥ 3:1 contra o fundo, nos temas claro e escuro. | 2.4.7, 1.4.11 |
| A3 | NVDA e VoiceOver anunciam cada campo com rótulo, papel (campo de edição/botão) e estado (inválido, obrigatório). | 1.3.1, 4.1.2 |
| A4 | A mensagem de erro de cada campo é lida ao focar o campo (associação programática ou equivalente lido junto ao rótulo). | 3.3.1, 3.3.3 |
| A5 | Após um envio inválido, o foco vai ao primeiro campo inválido e o leitor de tela o anuncia. | FR-046 |
| A6 | A região `aria-live` é anunciada uma vez por mudança de estado. A atualização periódica NÃO é anunciada. | 4.1.3, FR-047 |
| A7 | A tabela é navegável por leitor de tela com cabeçalhos associados às células (navegação por tabela do NVDA/VoiceOver), **ou** cada linha é lida como item de lista com texto completo e autoexplicativo (fallback aceito, ver W-14). | 1.3.1, FR-034 |
| A8 | Títulos de página (`document.title`) mudam por tela e os cabeçalhos (h1/h2) são navegáveis pelo atalho de cabeçalhos do leitor de tela. | 2.4.2, 2.4.6 |
| A9 | Com zoom de 200% (e com viewport de 320 px de largura), não há perda de conteúdo nem rolagem horizontal da página, e o texto escala. | 1.4.4, 1.4.10 |
| A10 | `axe-core` não aponta nenhuma violação A/AA no espelho DOM das três telas. | SC-007 |
| A11 | Os campos de texto aceitam digitação, colar e corretor/IME em teclado virtual (iOS Safari e Android Chrome), e o teclado numérico/decimal abre no campo de valor. | SC-001 (celular) |
| A12 | O idioma pt-BR é lido corretamente (atributo `lang` aplicado ao espelho). | 3.1.1 |

**Registro**: o resultado (GO/NO-GO por critério, versões de navegador/leitor e evidências como gravações ou trechos do espelho DOM) MUST ser anexado a esta seção como "W-00 Resultado" antes de qualquer tarefa de User Story.

**Se NO-GO**: a alternativa registrada é **Compose HTML** (`org.jetbrains.compose.html:html-core`), que renderiza DOM real com elementos HTML semânticos e dá acessibilidade nativa do navegador. Restrição conhecida: o Compose HTML suporta só o alvo **Kotlin/JS** (não wasmJs). O plano já se prepara para essa troca:

- `:core`, `:state` e `:data` não usam API específica de wasm em `commonMain`. Toda interop com o navegador fica isolada em `wasmJsMain` de `:data` (W-09) e de `:app`.
- Com NO-GO, trocam-se: o alvo (`wasmJs` → `js(IR)`), o módulo `:ui` (Compose UI/Material 3 → Compose HTML + CSS próprio com tokens de tema), os adaptadores de navegador (`external` de wasm → `external` de JS) e a navegação (W-04 passa a usar o roteador próprio sobre `hashchange`). Ficam iguais: value objects, parsing/formatação, estimativas, mapeamento de erros, ViewModels e testes de `:core`/`:state`/`:data`.
- O plan MUST ser revisado (seção Constitution Check, Technical Context e W-01/W-04/W-15/W-16) antes de retomar.

Um NO-GO parcial (por exemplo, só A7) MAY ser aceito com a mitigação descrita no critério, desde que registrada aqui e no Complexity Tracking.

**Rationale**: a acessibilidade é MUST e é o maior risco técnico da stack. Descobrir uma inviabilidade depois de construir oito telas custaria a reescrita da camada de UI. O spike custa cerca de 1 a 2 dias e o isolamento das camadas limita o raio de uma troca.

**Alternatives considered**:
- *Começar direto em Compose HTML*: elimina o risco de a11y, mas contraria a escolha explícita de stack (Compose UI + Material 3) e troca wasmJs por JS. Fica como plano B.
- *Confiar só no axe*: o axe audita apenas o espelho DOM, não o que o canvas desenha (contraste, foco visível), nem a experiência real com leitor de tela. Insuficiente para SC-007.

### W-00 Resultado (parcial, 2026-11-12) — automatizado concluído; matriz manual (T022) e decisão (T023) pendentes

**Status**: T014–T021 concluídas. O `SpikeA11yE2ETest` passa **21/21** em Chromium, Firefox e WebKit. A matriz manual com leitores de tela (NVDA, VoiceOver macOS/iOS), o teclado virtual (iOS/Android) e o zoom real do navegador **ainda não foram executados**. Sem eles, A3–A6, A11 e A12 continuam sem veredito final e o gate **não está decidido**.

**Ambiente**: Kotlin 2.4.21, CMP 1.12.2, Material 3 1.9.0, navigation-compose 2.10.0, bignum 0.3.10, kotlinx-datetime 0.8.0 + `@js-joda/timezone` 3.2.1. Playwright for Java 1.64.0 (Chrome for Testing 156.0.8078.4, Firefox e WebKit da versão 1.64) e axe-core Playwright 4.14.0, em macOS (Darwin 24.6). O spike roda em `web/spike-a11y/` por `./gradlew :spike-a11y:wasmJsBrowserDevelopmentRun` (porta 8082), com a CSP de W-15 mais o hash descrito abaixo. As evidências (snapshot de acessibilidade `*.aria.yml`, resultado do axe `*.axe.txt`, capturas em 320 px e com zoom de 200%, notas de teclado e de live region) ficam em `web/e2e/build/reports/spike-a11y/<navegador>/`. A URL `?mitigacoes=0` mostra o espelho sem as mitigações, para comparação.

**Como o espelho de acessibilidade da CMP 1.12.2 se comporta (observado nos três motores)**:
- O espelho fica num shadow root aberto, em `#cmp_a11y_root` (opacidade 0, `pointer-events: none`), atrás de um `<canvas tabindex="0" role="generic">`. Os nós são `div` com `role` (`button`, `textbox` com `contenteditable`, `heading`, `list`, `grid`) e texto, posicionados sobre o desenho do canvas.
- O foco do DOM fica no `<canvas>`, e o Compose move o foco internamente, sem `aria-activedescendant`. Num campo de texto, o foco do DOM vai para um `<input class="compose-backing-field">` oculto, com o `inputmode` certo (`email`, `decimal`, `text`) e `enterkeyhint`.
- A raiz do espelho tem `aria-live="polite"`.
- A CMP injeta um `<style>` no shadow root. Com `style-src 'self'`, ele é bloqueado: o `<input>` de apoio aparece no canto da tela e a página rola.

**Lacunas e mitigações** (M1–M4 em `web/spike-a11y/src/wasmJsMain/kotlin/com/kotlinbitcoins/web/spike/ComposeA11yShim.kt`):

| # | Lacuna (sem mitigação) | Efeito | Mitigação | Verificação |
|---|---|---|---|---|
| L1 | O slot `label` do `TextField` não vira nome do `textbox` | campo sem rótulo | **M1**: `contentDescription` = texto do rótulo visível (vira `aria-label`) | ✓ axe e snapshot |
| L2 | O `<input>` focado não tem nome, `aria-invalid` nem descrição. `semantics { error() }` e o `supportingText` não chegam ao espelho | o leitor anuncia "campo de edição" sem rótulo nem erro; axe `label` (critical) | **M2**: listener `focusin` no shadow root copia o rótulo e o erro do campo Compose focado (`aria-label`, `aria-invalid`, `aria-describedby` → nó oculto no mesmo shadow root) | ✓ E2E nos 3 motores |
| L3 | `aria-live="polite"` na raiz do espelho | qualquer mudança de texto na tela, inclusive a atualização de 10 s, pode ser anunciada (viola FR-047) | **M3**: remover o atributo e mantê-lo removido (MutationObserver). Anúncios só por `#live-polite`/`#live-assertive` | ✓ E2E (atributo ausente); leitura real pendente (T022) |
| L4 | `role="heading"` sem `aria-level`; `<canvas role="generic">`; `role="list"` sem `listitem`; `aria-label` em `div` sem papel | axe `aria-required-attr`, `aria-roles`, `aria-required-children`, `aria-prohibited-attr` (critical/serious) | **M4**: 1º título = nível 1, demais = 2; o canvas perde o papel; filhos de lista viram `listitem`, e lista que contém títulos/controles perde o papel; nós rotulados sem papel viram `group` | ✓ axe 0 violações |
| L5 | `CollectionInfo`/`CollectionItemInfo` viram `role="grid"` só na linha de cabeçalho, sem `row`/`gridcell`/`columnheader` | tabela não navegável; axe `aria-required-children` | nenhuma: usar a variante (b) | ✓ axe |
| L6 | `semantics { selected }` (navegação) e `RadioButton`/`selectable(role = RadioButton)` viram `role="button"`, sem `aria-current`/`aria-selected`/`aria-checked` | destino atual e opção marcada não são anunciados (FR-009) | **não testada**. Candidatas: incluir o estado no `contentDescription` ("Extrato, página atual") ou estender a M4 | ✗ pendente |
| L7 | `liveRegion` do Compose não gera `aria-live` próprio | a técnica A só "funcionava" por causa da L3 | usar o `DomAnnouncer` (técnica B, W-09) | ✓ |
| L8 | Ao sair do último campo de texto com Tab, o foco do DOM vai para `<body>` (parada sem foco visível); o Tab seguinte volta ao canvas no próximo controle. Do último controle, o Tab volta ao primeiro (ciclo dentro do canvas) | uma parada "fantasma" sem foco visível (2.4.7); não há armadilha | nenhuma no spike | ⚠️ avaliar na T022 |
| L9 | Hash alterado no mesmo documento (link `#/…`, edição na barra de endereço) não troca de tela: o binding só trata as entradas que ele criou | W-04 só lê o fragmento na inicialização | o `:app` ouve `hashchange` e navega por `AppRoute.parse` quando o fragmento diferir da rota atual (a validar) | ⚠️ |

M2, M3 e M4 dependem de detalhes internos da CMP (`#cmp_a11y_root`, `.compose-backing-field`, estrutura do shadow root). Qualquer atualização da CMP MUST repetir o `SpikeA11yE2ETest` (ou o E2E equivalente do app) antes de entrar.

**Critérios A1–A12 (parte automatizada)**:

| # | Resultado automatizado | Falta na T022 |
|---|---|---|
| A1 | ✓ com ressalva L8: todos os controles alcançáveis e acionáveis só com Tab/Enter nos 3 motores; sem armadilha | ordem com NVDA/VoiceOver; Safari real |
| A2 | ✓ visual: indicador de 3 dp em `primary` desenhado no canvas (capturas) | medir contraste nos dois temas (vira `ThemeContrastTest`, W-16) |
| A3 | ✓ só com M1+M2 (nome, papel e `aria-invalid` no elemento focado); ✗ sem mitigação | anúncio real; estado "obrigatório" não testado |
| A4 | ✓ com M2 (`aria-describedby` → texto do erro) | leitura real |
| A5 | ✓ após envio inválido, o foco vai ao primeiro campo inválido nos 3 motores | anúncio real |
| A6 | ✓ com M3 + `DomAnnouncer`: a região externa recebe um texto por mudança e nada no espelho tem `aria-live`; ✗ sem M3 | confirmar que o contador de 10 s não é lido |
| A7 | variante (a) ✗ (L5); variante (b) ✓ axe: cartões com `clearAndSetSemantics { contentDescription = frase completa }` | leitura real da lista |
| A8 | ✓ `document.title` muda por tela; títulos com nível pela M4 | atalho de cabeçalhos do leitor |
| A9 | ✓ 320 × 640 e 640 × 400 com `deviceScaleFactor` 2: sem rolagem horizontal e com os nós do espelho dentro do viewport | zoom real do navegador em janela de 1280 px |
| A10 | ✓ com M1–M4: **0 violações** nas três telas e estados (login, login com erro, formulário, formulário com erros, extrato em cartões), nos 3 motores. Sem mitigações: 3 violações críticas por tela (`aria-required-attr`, `aria-roles`, `label`) | — |
| A11 | ✓ parcial: `inputmode="decimal"`/`"email"` no `<input>` real | teclado virtual iOS/Android, colar, corretor |
| A12 | ✓ `lang="pt-BR"` no `<html>`, herdado pelo espelho | pronúncia real |

**Decisões que não dependem da T022**:
- **W-09**: técnica de anúncio = **DOM** (`#live-polite`/`#live-assertive`), porque o `liveRegion` do Compose não gera região própria (L7).
- **W-14**: variante = **(b) cartões** com `clearAndSetSemantics`, também no desktop. O `LazyColumn` deve conter só os itens da lista; se tiver títulos ou controles, a M4 tira o `role="list"`.
- **W-17**: seletores `getByRole`/`getByLabel` (atravessam o shadow root aberto). Clique com `force` no nó do espelho (o canvas recebe o evento nas mesmas coordenadas). Foco conferido pelo elemento ativo profundo (`shadowRoot.activeElement`). Digitação pelo teclado do Playwright. Com a M2, enquanto um campo está focado há dois `textbox` com o mesmo nome (o nó do espelho e o `<input>` real), então os seletores usam `.first()`. No WebKit, o screenshot do Playwright injeta um `<style>` que a CSP bloqueia; violações ocorridas durante a captura são descartadas pelo E2E.
- **W-15**: a CSP precisa do hash do `<style>` da CMP (ver W-15).
- **FR-048** (textos internos do Material 3): nas três telas, nenhum texto interno do Material 3 em inglês apareceu no espelho (`OutlinedTextField`, `Button`, `TextButton`, `RadioButton`, `Card`). `TopAppBar`, `ModalNavigationDrawer` e menus não fizeram parte do spike e devem ser verificados na T022 ou na primeira tela que os usar.

**Pontos para a decisão da T023**: (1) sem M1–M4, a stack **não** atende A3, A6 e A10; (2) com M1–M4, a parte automatizada atende, mas as mitigações são um shim sobre internos de uma plataforma Beta (risco de quebra a cada atualização, coberto pelo E2E); (3) L6 e L8 seguem abertas; (4) o resultado com leitor de tela real é o que confirma ou derruba as mitigações.

---

## W-01 Versões e status da plataforma

**Decision** (verificado no Maven Central em 2026-11-11; versões fixadas em `web/gradle/libs.versions.toml`):

| Componente | Versão | Observação |
|------------|--------|------------|
| Kotlin (KGP, wasmJs) | **2.4.21** | última estável (2.5.0 está em RC) |
| Compose Multiplatform (plugin + runtime/foundation/ui) | **1.12.2** | última estável (1.13 está em beta). **Alvo web = Beta** |
| Material 3 (`org.jetbrains.compose.material3:material3`) | **1.9.0** | versionado à parte desde a CMP 1.10 |
| Navigation (`org.jetbrains.androidx.navigation:navigation-compose`) | **2.10.0** | `bindToBrowserNavigation()` é **Experimental** |
| Lifecycle ViewModel (`org.jetbrains.androidx.lifecycle:lifecycle-viewmodel[-compose]`) | **2.11.0** | |
| Ktor Client (`ktor-client-core`, `ktor-client-js`, `content-negotiation`, `serialization-kotlinx-json`, `ktor-client-mock`) | **3.6.1** | engine Js (fetch) também no wasmJs |
| kotlinx-serialization-json | **1.13.0** | |
| kotlinx-coroutines (core, test) | **1.11.0** | |
| kotlinx-datetime | **0.8.0** | `Instant` vem de `kotlin.time` |
| ionspin bignum (`com.ionspin.kotlin:bignum`) | **0.3.10** | publica artefato `wasm-js`. Ver risco W-06 |
| Playwright for Java | **1.64.0** | |
| axe-core Playwright (`com.deque.html.axe-core:playwright`) | **4.14.0** | |
| swagger-request-validator-core (lado da API) | **3.0.0** | ver W-12 |
| ktlint (gradle plugin / engine) / detekt | iguais aos da API (`ktlint-gradle 14.2.0`, `ktlint 1.8.0`, `detekt 1.23.8`) | detekt roda com o próprio classpath Kotlin (mesma nota do catálogo da API) |
| Node (testes wasmJs) | gerenciado pelo plugin Kotlin | |
| Nginx | `nginxinc/nginx-unprivileged:1.27-alpine` + módulo brotli (W-15) | mesma linha 1.27 do nginx atual |

**Status Beta**: JetBrains classifica "Compose Multiplatform for web" e "Kotlin/Wasm" como **Beta**: podem ser usados, com esforço da JetBrains para minimizar migrações. A navegação no navegador (`bindToBrowserNavigation`) está marcada como **Experimental**. Consequências assumidas no plan: (a) versões fixadas exatas, sem faixas; (b) o spike W-00 é o gate; (c) atualizações de CMP/Kotlin passam por E2E completo; (d) a compatibilidade exata Kotlin ↔ CMP 1.12.2 ↔ bignum 0.3.10 é confirmada na primeira compilação do spike. Se falhar, desce-se para o Kotlin mínimo exigido pela CMP 1.12.x e registra-se aqui.

**Navegadores-alvo** (W-00 e Assumptions da spec): versões atuais de Chrome/Edge, Firefox e Safari (desktop e iOS), e Chrome Android. Kotlin/Wasm exige WasmGC e exception handling, presentes nas versões atuais dos quatro motores. O `index.html` mostra uma mensagem em pt-BR quando o navegador não suporta WasmGC (W-16).

**Rationale**: versões estáveis mais recentes, como pedido. A versão de Kotlin é a estável mais nova que a CMP 1.12 aceita.

**Alternatives considered**: CMP 1.13.0-beta02 (descartada por não ser estável); Kotlin 2.5.0-RC (idem).

**Resultado da Phase 1 (T002/T003, 2026-11-12)**: todas as versões acima resolveram no Maven Central. O wrapper de `web/` usa o **Gradle 8.14.3**, igual ao da raiz: é compatível com KGP 2.4.21 e CMP 1.12.2, mas a KGP avisa que ele está depreciado e que a Kotlin 2.5.0 exigirá no mínimo o 8.14.4 (atualizar os dois wrappers juntos quando subir a Kotlin). `@js-joda/timezone` fixado em **3.2.1** (peer `@js-joda/core >= 5.7.0`; chave `jsJodaTimezone` no catálogo). Com `FAIL_ON_PROJECT_REPOS`, as distribuições de Node, Yarn e Binaryen que a KGP baixa vêm de repositórios Ivy declarados em `web/settings.gradle.kts` (`exclusiveContent`), e `web/build.gradle.kts` limpa o `downloadBaseUrl` das specs wasm para a KGP não adicionar repositórios ao projeto. O alvo `wasmJs { }` ainda exige `@OptIn(ExperimentalWasmDsl::class)` nos scripts.

**Resultado da Phase 2 (T014, 2026-11-12)**: o spike compila com Kotlin 2.4.21 + CMP 1.12.2 + Material 3 1.9.0 + navigation-compose 2.10.0, e `./gradlew :spike-a11y:wasmJsBrowserDistribution` gera a distribuição. **Não** foi preciso descer versões. Notas de API: `ComposeViewport(viewportContainerId = …)` exige `@OptIn(ExperimentalComposeUiApi::class)`; `navController.bindToBrowserNavigation(getBackStackEntryRoute)` existe com `@OptIn(ExperimentalBrowserHistoryApi::class)`, e o lambda devolve o fragmento com `#` (ex.: `"#/tabela?filtro=x"`). Voltar, avançar e recarregar preservam rota e parâmetro nos 3 motores (W-04; ver L9 em W-00 Resultado). O webpack de desenvolvimento da KGP adiciona um `EvalSourceMapDevToolPlugin` que viola a CSP (`'unsafe-eval'`); o spike o remove em `webpack.config.d/devServer.js`, junto com o cliente de hot reload (websocket para `0.0.0.0`). A distribuição de produção não usa `eval`. **Tamanho (SC-010)**: a distribuição de produção do spike soma **3.679.712 bytes em brotli -q 11** (Skiko 2.622.336 + app 951.615 + JS 105.761; em gzip -9, 4.725.218), **sem fonte**. Isso já ultrapassa o limite de 3,5 MB de W-15. O Skiko responde por ~71%. Registrado como risco para a T222.

---

## W-02 Módulos Gradle e regra de dependência

**Decision**: build Gradle **separado** em `web/` (`settings.gradle.kts`, `gradle/libs.versions.toml`, wrapper e `config/detekt` próprios). Não há `includeBuild` entre `web/` e a raiz, então o build da API não muda (a única mudança do lado da API é o teste de contrato do W-12). Seis módulos:

| Módulo | Tipo | Depende de | Conteúdo | Regra garantida pelo compilador |
|--------|------|-----------|----------|---------------------------------|
| `:core` | KMP (wasmJs, código em `commonMain`) | bignum, kotlinx-datetime, kotlinx-serialization-core (só anotações `@Serializable` das rotas) | value objects (`Money`, `BtcAmount`, `QuotePrice`, `VariationPercent`), parsing pt-BR, formatação, estimativas, agrupamento do extrato, `Outcome`, modelo de erros (`ApiError`, `OperationError`), rotas (`AppRoute`, `ReturnDestination`) | não conhece Compose, Ktor nem navegador: testável em Node puro |
| `:state` | KMP | `:core`, lifecycle-viewmodel, coroutines | ViewModels, `UiState` imutáveis, máquinas de estado das operações, `AutoRefresher`, `SessionManager`, **portas** (`AuthGateway`, `AccountGateway`, `QuoteGateway`, `InvestmentGateway`, `StatementGateway`, `MarketGateway`, `SessionStorage`, `TabChannel`, `PageVisibility`, `Announcer`, `Clock`) | orquestração sem Compose e sem HTTP. Define as abstrações que `:data` implementa (Princípio I) |
| `:data` | KMP | `:core`, `:state`, Ktor, serialization | DTOs escritos à mão, mapeadores DTO → `:core`, `ProblemMapper`, implementações dos gateways. Em `wasmJsMain/browser/`: `BrowserSessionStorage`, `BroadcastTabChannel`, `DocumentVisibility`, `DomAnnouncer`, `MediaPreferences` | é a única com Ktor. As exceções do Ktor viram `Outcome` aqui (Princípio III) |
| `:ui` | KMP + Compose | `:core`, `:state`, Compose, Material 3, navigation, Compose Resources | telas stateless, componentes, tema, `NavHost`, strings pt-BR, fonte | **não depende de `:data` nem de Ktor**: uma tela não consegue chamar a API (Princípio I) |
| `:app` | KMP wasmJs executável | todos | `main()`, `AppContainer` (composição manual), `index.html`, CSS de carregamento | único ponto que conhece implementações concretas (composição na borda) |
| `:e2e` | JVM (Kotlin/JUnit 5) | Playwright, axe-core | testes ponta a ponta contra o stack em docker | fora do `check` padrão; roda na tarefa `e2eTest` |

Grafo: `ui → state → core`, `data → state → core`, `app → {ui, data, state, core}`. Não há ciclos e `core` não depende de nada do projeto.

**Granularidade, justificada**:
- Separar `:ui` de `:data` é o que torna verificável a regra "telas não chamam a API". Com um módulo só, só sobrariam convenção e revisão.
- Separar `:state` de `:ui` deixa os ViewModels testáveis sem Compose (rápido, em Node) e impede que a orquestração importe widgets.
- Separar `:core` de `:state` concentra precisão decimal, formatação e parsing (os requisitos de maior risco: FR-036 a FR-039, SC-002) num módulo sem coroutines nem lifecycle, testado de forma exaustiva.
- **Não** criamos um módulo por feature/tela (seria 8 a 10 módulos sem regra nova a verificar; Princípio V). Pacotes por feature dentro de cada módulo bastam.
- `:e2e` fica separado porque é JVM e depende do stack rodando.

**Rationale**: mesma estratégia da API (R-02 da 001: a regra de dependência vira restrição de compilação), adaptada às três camadas do cliente mais o núcleo puro.

**Alternatives considered**: 3 módulos (`core`, `app`, `e2e`), descartado porque não impede tela → HTTP. Portas em `:core`, descartado porque `:core` ficaria com coroutines/Flow e conceitos de orquestração. Módulos por feature, descartado como over-engineering (Princípio V).

---

## W-03 Estado: ViewModel + StateFlow, UI stateless

**Decision**: um `ViewModel` (androidx.lifecycle multiplatform) por tela, expondo `StateFlow<XxxUiState>` (data classes/sealed imutáveis) e funções de intenção (`onAmountChange`, `onReview`, `onConfirm`, `onRetry`, `onTogglePause`). As telas recebem `state` + lambdas e não têm lógica. ViewModels são criados por `viewModel { container.xxxViewModel() }` com a fábrica do `AppContainer`, associados à entrada de back stack. Em logout, o back stack é limpo e, com ele, os ViewModels e os dados exibidos (FR-006).

**Operações financeiras** (`DepositViewModel`, `PurchaseViewModel`, `RedemptionViewModel`) seguem a máquina de estados do data-model §4. `onConfirm` só tem efeito no estado `Reviewing` (ou `Editing` no depósito, que não tem revisão) e passa atomicamente para `Submitting` antes de chamar o gateway. Enter repetido, clique duplo ou toque duplo chegam a um estado que ignora a intenção (single-flight). Isso, mais o botão desabilitado com `semantics { disabled() }` e o rótulo "Processando…", atende FR-043/SC-004 na camada testável.

**Rationale**: padrão oficial da plataforma, testável com `kotlinx-coroutines-test` e fakes das portas.

**Alternatives considered**: MVI com biblioteca (Orbit, Decompose), descartado por ser dependência extra sem requisito (Princípio V). Estado em `remember` na UI, descartado por violar o Princípio I e ser difícil de testar.

---

## W-04 Navegação, URL, guarda de sessão e retorno pós-login

**Decision**:
- `navigation-compose` 2.10.0 com rotas **type-safe** (`@Serializable`) e `navController.bindToBrowserNavigation(getBackStackEntryRoute = ...)`, que grava a rota no **fragmento** da URL. O lambda gera fragmentos curtos e estáveis pelo `AppRoute.toFragment()` de `:core` (tabela em [contracts/ui-routes.md](./contracts/ui-routes.md)). Exemplo: `#/extrato?de=2026-10-01&ate=2026-11-09`.
- **Inicialização**: antes do `bindToBrowserNavigation()`, o `:app` lê `window.location.hash`, faz `AppRoute.parse(fragment)` (função pura, testada) e navega ao destino resolvido. É o padrão documentado para ler o fragmento no carregamento.
- **Guarda**: um `SessionGate` (no `:state`) decide a rota efetiva. Rota protegida sem sessão vira `Login(returnTo = rotaSolicitada)`. Rota pública (`Login`, `Register`) com sessão ativa vai para `Dashboard`. A guarda roda na inicialização, a cada mudança de destino e a cada evento de sessão (expiração, 401, logout de outra aba).
- **Retorno pós-login**: `returnTo` trafega como parâmetro do fragmento de login (`#/entrar?voltar=%2Fextrato%3Fde%3D...`) e assim sobrevive a recarregar a página de login. Na volta, `ReturnDestination.resolve(raw)` aceita **só** strings que `AppRoute.parse` reconhece como rota protegida do app. Qualquer outra coisa (URL absoluta, `//host`, `javascript:`, rota desconhecida, rota pública) resolve para `Dashboard` (Edge Case, FR-007). Como `returnTo` vira `AppRoute` tipado, um destino externo é impossível por construção. Os filtros do extrato fazem parte da rota e, portanto, voltam junto (FR-004, SC-009).
- **Formulários financeiros**: as rotas de depósito, compra e resgate não carregam valores. Ao voltar após login, o formulário abre vazio (FR-004, Edge Case).
- **Resultado de operação** é um estado dentro da mesma rota (`#/comprar`), não uma rota própria. Recarregar mostra o formulário vazio e voltar/avançar não reenvia nada, porque o POST só sai de `onConfirm` (FR-043).
- **Sair**: `navController.navigate(Login) { popUpTo(0) { inclusive = true } }`, mais limpeza da sessão. Um "voltar" do navegador que leve a `#/painel` passa de novo pela guarda e cai no login sem exibir dados (US1-7). Não há troca de documento, então o bfcache não se aplica.
- **Título de página** (FR-046): cada destino define `document.title` ("Painel · Investimento em Bitcoins") por um efeito na raiz da navegação, com interop no `:app`.

**Validações exigidas pelo pedido** (viram testes E2E em `:e2e`, ver quickstart §5):
1. Voltar/avançar entre Painel → Extrato (com filtro) → Comprar restaura rota e filtro.
2. Recarregar em `#/extrato?de=…&ate=…` mantém filtro e sessão.
3. Sessão expirada no extrato filtrado → login com aviso → volta ao extrato com o mesmo filtro.
4. `#/entrar?voltar=https%3A%2F%2Fevil.example` e `voltar=%2F%2Fevil.example` → após login, painel.

**Risco**: `bindToBrowserNavigation` é Experimental. Mitigação: o mapeamento rota ↔ fragmento está todo em `:core` (testado). Se a API mudar ou falhar no spike, um roteador próprio sobre `hashchange`/`history.replaceState` (cerca de 100 linhas em `:app`) substitui o binding sem tocar em `:ui`/`:state`.

**Resultado do spike (T020/T021)**: o binding funciona nos 3 motores (voltar, avançar, recarregar e parâmetro no fragmento), e a leitura de `window.location.hash` na inicialização resolve a rota inicial. Limitação: um hash alterado no mesmo documento (link interno `#/…` ou edição na barra) não troca de tela (L9 em W-00 Resultado). O `:app` deve tratar `hashchange`.

**Alternatives considered**: rotas por caminho (`/extrato`), descartado porque o binding da CMP usa fragmento e o fallback SPA do nginx precisaria cobrir toda rota (continua configurado por segurança, W-15). `returnTo` em `sessionStorage`, descartado por não sobreviver a uma aba nova e não ser visível/testável na URL. Navigation 3, descartado porque ainda não tem binding de navegador equivalente documentado e não é o pedido.

---

## W-05 HTTP: Ktor, DTOs, erros RFC 9457 e resultado incerto

**Decision**:
- `HttpClient(Js)` com `ContentNegotiation(json { ignoreUnknownKeys = true; explicitNulls = false })`, `HttpTimeout(requestTimeoutMillis = 30_000)` (alinhado ao `proxy_read_timeout 30s` do nginx), `defaultRequest { url("${window.location.origin}/api/v1/") }` e `expectSuccess = false`. **Sem plugin `Logging`** (FR-008). O header `Authorization: Bearer` é adicionado por um plugin próprio que lê o token pela porta `SessionTokenProvider` (definida em `:state` e implementada pelo `SessionManager`) no momento do envio. O 401 também é notificado por essa porta, para que `:data` não dependa da classe concreta.
- **DTOs à mão** em `:data` (`dto/`), espelhando os schemas do contrato com nomes iguais aos campos JSON. Todo valor monetário, BTC, cotação e percentual é `String` no DTO e vira value object no mapeador (`Money.parseApi("100.50")`). Um valor fora do padrão do contrato vira `ApiError.Unexpected` (resposta fora do contrato, FR-041), nunca uma exceção que atravesse camadas.
- **Fronteira de erro** (`ApiCall.execute`), única para todas as rotas, com `runCatching` só aqui (Princípio III):

| Situação | Resultado |
|----------|-----------|
| 2xx com corpo válido | `Outcome.Success(model)` |
| `application/problem+json` com `code` conhecido | `ApiError` específico (tabela do data-model §3); `VALIDATION_ERROR` leva `errors[]` por campo |
| `code` desconhecido, `NOT_FOUND`, `INTERNAL_ERROR`, status não previsto ou corpo inválido | `ApiError.Unexpected` |
| 401 `UNAUTHORIZED` | `ApiError.Unauthorized`. Além disso, o `SessionManager` é notificado (W-08) |
| falha de transporte (fetch rejeitado/timeout) **em GET ou login/cadastro** | `ApiError.NoConnection` |
| falha de transporte, timeout ou 502/504 **sem** Problem em **POST financeiro** (`/me/deposits`, `/me/purchases`, `/me/redemptions`) | `OperationError.Uncertain` (Edge Case: resultado incerto) |
| Problem válido em POST financeiro (incluindo `SERVICE_UNAVAILABLE`, `INTERNAL_ERROR`) | erro definitivo: a API é transacional e uma resposta de erro do contrato garante que nada foi efetivado |

- O chamador (`:state`) só recebe `Outcome<ApiError, T>` ou `Outcome<OperationError, T>`. O `when` sobre esses `sealed` é exaustivo, então um código novo no modelo quebra a compilação até ser tratado em `:state` e `:ui`.
- **Mensagens**: o mapeamento erro → texto pt-BR fica em `:ui` (`ErrorMessages.kt` + `strings.xml`). Ver [contracts/error-messages.md](./contracts/error-messages.md). O `detail` do servidor **nunca** é exibido diretamente. As mensagens são as da FR-040, para garantir SC-003 ("0 mensagens com detalhes técnicos"). Exceção: em `VALIDATION_ERROR`, o `message` de cada `errors[]` é exibido junto ao campo, porque a 001 garante que ele está em pt-BR e sem detalhes internos. Se o `field` for desconhecido, a mensagem vai para o resumo do formulário.

**Rationale**: um ponto único de conversão facilita testar 100% dos códigos (SC-003) com `MockEngine` + fixtures (W-12). Distinguir "incerto" de "falhou" só nos POSTs financeiros evita orientar a pessoa a conferir o extrato depois de um GET que falhou.

**Alternatives considered**: gerar o cliente a partir do OpenAPI (openapi-generator Kotlin multiplatform), descartado porque os geradores não suportam wasmJs de forma confiável, produzem `Double` para números e foi pedido DTO à mão. Idempotency-Key na API, fora de escopo (clarificação de 2026-11-11).

---

## W-06 Decimal exato: bignum encapsulado

**Decision**: `com.ionspin.kotlin:bignum:0.3.10` (`BigDecimal`), usado **apenas dentro** de `:core/money/`. Os value objects `Money` (escala 2), `BtcAmount` (escala 8), `QuotePrice` (escala da API, 2 a 8) e `VariationPercent` (escala 2, com sinal) expõem só operações do domínio do cliente: `parseApi`, `parseUserInput` (só `Money`), `format`, `plus` (soma de exibição, FR-029), `compareTo`, `isZero`. A divisão existe só em `Estimates`, com `DecimalMode(decimalPrecision, RoundingMode.FLOOR, scale = 8)`. `Double`/`Float` são **proibidos** para valores. A regra vale para os módulos `:core`, `:state` e `:data`: o detekt (`web/config/detekt/detekt-precision.yml`, `ForbiddenMethodCall` para `toDouble`/`toFloat`) mais a tarefa Gradle `checkNoFloatingPoint` (falha com `Double`, `Float` ou literal `\d[fF]` em `src/*Main`). O detekt sozinho não basta, porque `kotlin.Float` é importado implicitamente e a `ForbiddenImport` não o detecta. O `:ui` fica fora dessa regra, porque o Compose usa `Float` em dimensões, pesos e cores. No `:ui`, o confinamento é garantido por `web/config/detekt/detekt-ui.yml`: `ForbiddenMethodCall` de `kotlin.text.toFloat`/`toDouble`, permitido só em `ui/chart/ChartGeometry.kt` (geometria de pixel, que converte `toPlainString()` para `Float`; o `:core` não expõe conversão para ponto flutuante). O `checkNoFloatingPoint` ignora comentários e literais de string e procura só uso de tipo (`: Double`, `<Float>`, `Double(`, `as Float`) e literais `1f`/`1.0F`, evitando falsos positivos. A exceção explícita é o pacote `ui/chart`, para geometria de desenho (ver W-13 e Complexity Tracking).

**Testes de precisão (SC-002)**: casos-limite `99999999999.99`, `0.01`, `0.00000001`, `21000000.00000000`, cotações com 2, 5 e 8 casas, ida e volta `parseApi → format` sem perda, ausência de notação científica (`toPlainString`), estimativa `400.00 / 400000.00 = 0.00100000` e truncamento (não arredondamento) na 8ª casa.

**Risco**: o bignum 0.3.10 foi publicado com Kotlin anterior ao 2.4. A Kotlin/Wasm aceita klibs de versões anteriores, mas isso é confirmado na primeira compilação do spike. **Plano B** registrado: value objects sobre `Long` escalado (centavos, satoshis, cotação × 10⁸) para armazenamento e soma, mais uma divisão longa própria em `Estimates` sobre um par de `Long`/`IntArray`. Os limites de faixa cabem (R$ 99.999.999.999,99 = 9,99 × 10¹² centavos). Só a estimativa exige um intermediário maior que `Long`. Como a API dos value objects não muda, o plano B fica restrito a `:core/money`.

**Alternatives considered**: `Long` escalado desde o início, descartado porque a estimativa (valor × 10⁸ ÷ cotação × 10⁸) estoura `Long` e exigiria aritmética própria, que é mais código para testar. `kotlin.js` `BigInt` via interop, descartado porque prende `:core` ao navegador.

**Resultado da Phase 1 (T006, 2026-11-12)**: verificado com arquivo-sonda que o `style.ForbiddenMethodCall` do detekt 1.23.8 **não dispara** em fontes wasmJs: a regra exige resolução de tipos, e o detekt não a executa para alvos JS/Wasm. Por isso `detekt-precision.yml` e `detekt-ui.yml` foram mantidos (documentam a regra e passam a valer se houver resolução de tipos), mas a garantia efetiva vem de verificações textuais em `web/build.gradle.kts`, todas ligadas ao `check`, que ignoram comentários e literais de string/char e varrem só `src/*Main`:
- `checkNoFloatingPoint` (`:core`, `:state`, `:data`): os padrões de tipo/literal acima, mais chamadas `.toDouble(`/`.toFloat(` (e `OrNull`) e `print`/`println` (FR-008).
- `checkFloatConversionConfined` (`:ui`): `.toDouble(`/`.toFloat(` proibidos fora de `ui/chart/ChartGeometry.kt`, mais `print`/`println`. `Float` como tipo do Compose segue livre.
- `checkNoConsoleOutput` (`:app`): `print`/`println`.
A proibição de `kotlin.text.format` (detekt.yml) também fica inerte em wasmJs. Não há verificação textual para ela, porque `.format(` é legítimo com o kotlinx-datetime (W-07); a revisão de código cobre esse caso.

**Resultado da Phase 2 (T014, 2026-11-12)**: o bignum 0.3.10 funciona em Kotlin 2.4.21/wasmJs. A sonda de runtime `400.00 / 400000.00` com `DecimalMode(30, RoundingMode.FLOOR, scale = 8)` dá `0.00100000` (`toPlainString`) em Chromium, Firefox e WebKit. O plano B (`Long` escalado) não é necessário.

---

## W-07 Datas: kotlinx-datetime com America/Sao_Paulo no wasmJs

**Decision**: `kotlinx-datetime 0.8.0` com `TimeZone.of("America/Sao_Paulo")`, constante em `:core/time/BrasiliaTime`. Instantes da API (ISO-8601 com offset) são lidos como `kotlin.time.Instant` e formatados em Brasília, **independentemente do fuso do dispositivo** (FR-037).

**Requisito de base de fusos no wasmJs**: em JS/Wasm, o kotlinx-datetime usa o `@js-joda/core`, que só conhece UTC e fusos de offset fixo. Fusos nomeados exigem o pacote npm **`@js-joda/timezone`**, adicionado como `implementation(npm("@js-joda/timezone", "<versão fixada>"))` e **referenciado** no código wasm para não ser removido do bundle:

```kotlin
@JsModule("@js-joda/timezone") external object JsJodaTimeZoneModule
private val jsJodaTz = JsJodaTimeZoneModule // referência obrigatória
```

A referência fica no `:app` (`main.kt`), que é a borda. Para reduzir o bundle, usa-se a variante de dados de faixa recente, se disponível na versão fixada (`@js-joda/timezone/dist/js-joda-timezone-10-year-range`). Um teste em `:core` (`wasmJsTest`) MUST falhar se `TimeZone.of("America/Sao_Paulo")` lançar exceção. Isso protege contra a remoção acidental do módulo.

**Formato**: `dd/MM/yyyy HH:mm` e, no instante da cotação, `dd/MM/yyyy HH:mm:ss`, sempre com o rótulo "(horário de Brasília)" uma vez por bloco (FR-037). Formatação via `LocalDateTime.Format { … }` do kotlinx-datetime, sem `Intl`.

**Datas do filtro do extrato**: `LocalDate` digitado como `dd/mm/aaaa`, convertido para ISO (`2026-10-01`) na URL e na query `from`/`to` (o contrato interpreta como calendário de Brasília). "Hoje" (para sugestões e para o período padrão exibido) é calculado com `Clock.System.now().toLocalDateTime(Brasília)`.

**Resultado da Phase 2 (T014, 2026-11-12)**: com `@js-joda/timezone` 3.2.1 referenciado por `@JsModule` no `main.kt`, `TimeZone.of("America/Sao_Paulo")` converte `2026-11-09T11:10:00Z` em `09/11/2026 08:10` nos 3 motores. Nota de API do kotlinx-datetime 0.8.0: o formato usa `day()` (não `dayOfMonth()`) e a formatação é `formato.format(valor)`.

**Rationale**: o Brasil não tem horário de verão desde 2019, mas não usar offset fixo `-03:00` evita erro se a regra mudar e é o que o pedido exige.

**Alternatives considered**: `Intl.DateTimeFormat` com `timeZone: 'America/Sao_Paulo'` via interop. Funciona sem dependência extra, mas prende formatação a `:app`/JS e foge do pedido. Fica como plano B se o tamanho do `@js-joda/timezone` prejudicar SC-010.

---

## W-08 Sessão: sessionStorage + BroadcastChannel

**Decision**:
- **Armazenamento**: `{ accessToken, expiresAtEpochMs }` em `sessionStorage` (chave `kb.session`). Sobrevive a recarregamento e termina quando a aba/navegador fecha (FR-004, US1-5, US1-9). O token **nunca** vai para URL, cookie, `localStorage` ou log (FR-008).
- **Expiração**: `expiresAt = instante local do login + expiresIn` (a resposta de login não traz instante absoluto). O `SessionManager` (`:state`) agenda um job que dispara `SessionEvent.Expired` no instante. Antes de cada requisição autenticada, ele verifica `now >= expiresAt` e, nesse caso, expira sem enviar. Um 401 `UNAUTHORIZED` de qualquer rota autenticada também expira a sessão (FR-004). Não existe renovação (premissa da 001).
- **Abas novas**: o `sessionStorage` é por aba. Uma aba aberta pelo endereço começa vazia. Protocolo no `BroadcastChannel("kb-session")`:
  1. Aba sem sessão envia `{type: "request"}` e espera até **300 ms**, mostrando o carregamento.
  2. Qualquer aba com sessão válida responde `{type: "share", accessToken, expiresAtEpochMs}`. A primeira resposta é gravada no `sessionStorage` local.
  3. Sem resposta, não há sessão e a guarda leva ao login.
  4. `{type: "logout"}` e `{type: "expired"}` são enviados por quem encerra. Quem recebe limpa o `sessionStorage`, descarta os dados e vai ao login. No caso de expiração, mostra a mensagem de sessão expirada (Edge Case "várias abas", FR-006).
  5. Login em uma aba envia `{type: "share"}`, para que abas paradas no login entrem.
- O protocolo (lógica, timeouts, decisões) fica em `SessionManager` (`:state`), testado com fakes de `SessionStorage`, `TabChannel` e `Clock`. Os adaptadores reais (`BrowserSessionStorage`, `BroadcastTabChannel`) em `:data/wasmJsMain/browser` são finos e cobertos por E2E (multiaba no Playwright).
- **Mensagens no canal**: o token trafega só entre abas da **mesma origem** (escopo do `BroadcastChannel`). Nenhuma mensagem é logada.

**Limitação conhecida (registrada)**: o recurso "continuar de onde parou"/"restaurar sessão" de Chrome, Edge e Firefox pode restaurar o `sessionStorage` ao reabrir o navegador. Do ponto de vista do navegador, a sessão nunca terminou. Nesse caso, a sessão volta até o `expiresAt`, que continua sendo respeitado. Não há mecanismo web confiável para detectar o fechamento do navegador sem cookies de sessão. O E2E de US1-9 usa um contexto novo de navegador (fechamento real). Aceito como risco baixo: o token ainda expira e a limitação depende de uma opção ativada pela pessoa.

**Rationale**: atende exatamente a clarificação de 2026-11-11 (sobrevive a recarregamento e novas abas, morre ao fechar) sem cookie (que exigiria mudar a API para emitir cookie HttpOnly).

**Alternatives considered**: `localStorage`, que sobrevive ao fechamento e viola US1-9. Só memória, que perde a sessão no recarregamento e viola US1-5. Cookie HttpOnly de sessão, que exigiria mudar o contrato da 001 (fora de escopo) e levaria a CSRF. `SharedWorker` guardando o token, que tem suporte irregular (Safari iOS) e complexidade maior.

---

## W-09 Interop com o navegador (wasmJs)

**Decision**: todas as APIs do navegador ficam atrás de portas definidas em `:state` e são implementadas em `:data/src/wasmJsMain/kotlin/.../browser/` com `kotlinx.browser`/`org.w3c.dom` e `external` declarations:

| Porta (`:state`) | Implementação | API do navegador |
|------------------|---------------|------------------|
| `SessionStorage` | `BrowserSessionStorage` | `window.sessionStorage` |
| `TabChannel` | `BroadcastTabChannel` | `BroadcastChannel` (external) |
| `PageVisibility` | `DocumentVisibility` | `document.visibilityState` + `visibilitychange` → `StateFlow<Boolean>` |
| `Announcer` | `DomAnnouncer` | dois `<div>` fora do canvas, criados no `index.html`: `#live-polite` (`aria-live="polite"`) e `#live-assertive` (`role="alert"`), visualmente ocultos |
| `UserPreferences` | `MediaPreferences` | `matchMedia('(prefers-reduced-motion: reduce)')` e `('(prefers-color-scheme: dark)')`, com listeners |
| `DocumentTitle` | `BrowserDocumentTitle` | `document.title` |

**Por que `DomAnnouncer` fora do canvas**: garante o anúncio por `aria-live` real, que é a técnica mais compatível entre NVDA, JAWS e VoiceOver, independentemente de como a CMP mapeia `liveRegion` no espelho. O spike (A6) compara as duas técnicas e registra a escolhida. O padrão é o DOM.

**Rationale**: mantém `:state` testável e isola tudo que é específico de wasm (facilita o plano B do W-00).

---

## W-10 Atualização automática (~10 s)

**Decision**: `AutoRefresher` em `:state` (um por ViewModel que precisa), com `interval = 10.seconds` e as entradas `visible: StateFlow<Boolean>` (porta `PageVisibility`), `paused: MutableStateFlow<Boolean>` e `refresh: suspend () -> Unit`.
- Executa `refresh` ao iniciar. Depois espera 10 s **após o término** do ciclo anterior (sem sobreposição; um ciclo lento não acumula chamadas).
- Com `visible = false` ou `paused = true`, suspende (sem timers ativos). Ao voltar a ficar visível (e não pausado), executa **imediatamente** (Edge Case "aba em segundo plano", FR-011). Ao retomar a pausa, executa imediatamente.
- O estado "pausado" vale por tela, com `rememberSaveable`-equivalente no ViewModel. Não é persistido.
- Usado por: Painel (cotação + posição), Meus investimentos (posição), Resgate (posição → disponível), Revisão de compra (cotação), Revisão de resgate (posição → cotação de compra). Perfil e saldo carregam uma vez ao abrir. O saldo é recarregado após cada operação concluída.
- **Foco** (FR-011): a atualização só troca valores dentro de componentes já compostos com `key` estável, sem recompor a estrutura nem chamar `FocusRequester`. É verificado em E2E: o foco no botão "Comprar" continua após um ciclo.
- **Anúncios** (FR-047): o ViewModel compara o estado anterior com o novo e chama `Announcer.polite` **só** em transições `Disponível → Indisponível` ("Cotação indisponível no momento") e `Indisponível → Disponível` ("Cotação restabelecida"). Ao pausar/retomar, anuncia "Atualização automática pausada/retomada" (FR-012). Os valores atualizados ficam em nós sem `liveRegion`.
- **SC-005** (instante ≤ 25 s atrás): a API serve cotação com reuso de até ~10 s (R-06 da 001: reuso do cache se a idade for menor que 10 s) e o cliente consulta a cada 10 s, com pior caso de cerca de 20 s mais latência. O E2E verifica com o quote-stub: `now − quotedAt ≤ 25 s` durante 2 minutos.
- O controle de pausa é um `IconToggleButton`/`FilterChip` com rótulo visível "Atualização automática: ativa/pausada" e `semantics { stateDescription }`.

**Alternatives considered**: `setInterval` JS, descartado porque prende `:state` ao navegador e é difícil de testar. Polling fixo sem esperar o término, descartado por acumular chamadas com a rede lenta. Server-Sent Events, que não existem no contrato.

---

## W-11 Parsing e formatação pt-BR

**Decision** (tudo em `:core/format`, funções puras):
- **Entrada de valor (FR-039)**: depois de `trim`, aceita `^\d{1,3}(\.\d{3})+(,\d{1,2})?$` (com milhar) ou `^\d+(,\d{1,2})?$` (sem milhar). Exemplos aceitos: `1234,56`, `1.234,56`, `1234`, `1.234`. Recusados com `AmountInputError`: vazio → `Empty`; `0`, `0,00` → `NotPositive`; `-5`, `R$ -5` → `InvalidFormat`; `1,234.56`, `1e3`, `12,345` (3 casas → `TooManyDecimals`), `1.23,4`, texto → `InvalidFormat`. Limite de formato: até 17 dígitos inteiros (`NUMERIC(19,2)` da 001). Acima disso é `InvalidFormat`, mas a API continua sendo a autoridade (FR-039). A saída é `Money` e o envio usa `Money.toApiString()` (`"1234.56"`, padrão `MoneyInput`).
- **Campo**: `TextField` com `KeyboardOptions(keyboardType = KeyboardType.Decimal)`, prefixo visual "R$" e sem máscara automática que mova o cursor (melhor para leitor de tela). A validação acontece ao avançar/enviar e ao sair do campo depois do primeiro envio.
- **Saída (FR-036)**:
  - `Money.format()` → `R$ 1.234,56` (espaço **não separável** U+00A0 entre `R$` e o número).
  - `BtcAmount.format()` → `0,00100000 BTC`, sempre com 8 casas.
  - `QuotePrice.format()` → `R$ 400.000,00` com **todas** as casas recebidas (mínimo 2): `"400000.12345"` → `R$ 400.000,12345`.
  - `VariationPercent.format()` → `+10,00%`, `−5,00%` (sinal **U+2212**, como na spec) e `0,00%`, mais uma `Trend` (`Up`/`Down`/`Flat`) para ícone e texto (FR-020).
  - Sem `String.format`, `toDouble` ou `Intl`: os separadores são inseridos sobre `toPlainString()` do decimal.
- **Cadastro (FR-001)**: nome com `trim().isNotEmpty()` e no máximo 200 caracteres, contados como `String.length` (unidades UTF-16) após `trim()`, exatamente como a 001 (`Account.isValidName` e `@Size(max = 200)`), para que cliente e API aceitem os mesmos nomes. E-mail com `^[^\s@]+@[^\s@]+\.[^\s@]+$` e no máximo 320. Senha com no mínimo 8 caracteres (*code points*, como `PasswordPolicy` da 001) e `encodeToByteArray().size <= 72` (Edge Case multibyte).

---

## W-12 Testes de consumidor: fixtures conformes ao contrato

**Problema**: o cliente precisa de fixtures JSON (respostas de sucesso e Problem de todos os `code`) para os testes com `MockEngine`, mas não pode manter uma cópia do contrato (Constitution, "Contratos entre Componentes").

**Decision**:
- **Local**: `web/contract-fixtures/` é a fonte única das fixtures. Elas são **exemplos**, não definição: o contrato continua em `specs/001-bitcoin-investment-api/contracts/openapi.yaml`. O `manifest.json` liga cada fixture a uma operação do contrato (`method`, `path`, `status`, `scenario`). Formato em [contracts/fixtures-manifest.md](./contracts/fixtures-manifest.md).
- **Uso no cliente**: uma task Gradle em `:data` (`generateContractFixtures`) lê `web/contract-fixtures/**` e gera `ContractFixtures.kt` em `build/generated/` (strings), adicionado a `commonTest`. Assim os testes wasm não precisam ler arquivos em runtime (Node/navegador). Os testes de `:data` montam um `MockEngine` que responde a fixture certa por método + path + cenário.
- **Validação no build da API** (`bootstrap/src/test/kotlin/.../contract/WebClientFixturesContractTest.kt`, no `./gradlew check` da raiz):
  1. **Conformidade com o schema**: cada fixture é validada contra o schema de resposta da operação/status no `openapi.yaml`, com `swagger-request-validator-core` (`OpenApiInteractionValidator.validateResponse`). Se a versão 3.0.0 não validar corretamente construções OpenAPI 3.1 usadas no contrato (`type: [string, 'null']`), troca-se por `com.networknt:json-schema-validator` com dialeto 2020-12 aplicado ao schema extraído. A escolha é confirmada na tarefa do teste e registrada aqui.
  2. **Cobertura de códigos**: o conjunto de `code` das fixtures Problem MUST ser igual ao `enum` de `Problem.code` do contrato. Um código novo na API quebra o build da API até a fixture existir, e o `when` exaustivo do cliente quebra o build web até o código ser mapeado (SC-003).
  3. **Cobertura de operações**: toda operação usada pelo cliente (lista no manifest) existe no contrato com o status indicado.
  4. **Aderência às respostas reais**: para cada fixture de sucesso marcada com `realScenario` no manifest, o teste executa o cenário real via MockMvc (sobre o `ApplicationTestBase` existente, com Testcontainers) e compara a **forma** da resposta real com a da fixture: mesmo conjunto de chaves em todos os níveis, mesmos tipos JSON e cada string de valor casando o mesmo `pattern` do contrato. Não compara valores, porque ids e instantes variam.
- O teste lê `../web/contract-fixtures` por caminho relativo à raiz, como o `OpenApiConformanceTest` já lê o contrato em `specs/`. A única mudança do lado da API é este teste mais uma dependência `testImplementation` em `bootstrap/build.gradle.kts`.

**Rationale**: o contrato tem um único dono, as fixtures são verificadas contra ele e contra o comportamento real, e o cliente testa com dados que a API de fato produz.

**Alternatives considered**: fixtures em `specs/002-frontend-web/contracts/`, descartado porque fixture é artefato de teste e não de especificação, e ficaria longe do código que a usa. Pact (consumer-driven contracts), descartado como infraestrutura extra (broker, plugin) sem requisito (Princípio V). Gerar fixtures a partir dos `example` do OpenAPI, descartado porque os exemplos do contrato não cobrem todos os cenários e não provam aderência à resposta real.

---

## W-13 Gráfico do Mercado (Canvas próprio)

**Decision**: componente `PriceHistoryChart` em `:ui/chart`, desenhado com `Canvas`/`DrawScope`:
- **Séries**: compra em linha **contínua** com marcador **círculo**; venda em linha **tracejada** (`PathEffect.dashPathEffect`) com marcador **quadrado**. Cores do tema com contraste ≥ 3:1 contra a superfície (1.4.11). Legenda em texto ao lado, com amostra de traço + marcador (FR-032).
- **Lacunas (FR-033)**: a preparação dos dados é **pura** em `:core/market/ChartSeries.segments(points)`. Os pontos vêm em ordem cronológica e são divididos em segmentos sempre que `slotAt[i+1] − slotAt[i] > 10 min`. Cada segmento vira um `Path` separado: nenhuma linha liga segmentos e nenhum valor é interpolado. Um ponto isolado vira só marcador.
- **Eixo X**: das últimas 24 h em horário de Brasília, com marcas a cada 4 h (`HH:mm`). **Eixo Y**: mínimo e máximo das duas séries, com 4 marcas formatadas por `QuotePrice.format()`.
- **Float só para geometria**: posições em pixel exigem `Float` (API do `DrawScope`). A conversão `QuotePrice → Float` acontece **apenas** em `:ui/chart` para calcular coordenadas. Rótulos, legenda, tabela e resumo usam os valores decimais exatos. Fica registrado no Complexity Tracking como exceção controlada à regra "sem ponto flutuante" (FR-038 trata de exibir e manipular valores, não de pixel).
- **Alternativa textual (FR-034)**: o `Canvas` recebe `semantics { contentDescription = resumo }`, com o resumo gerado em `:core`: "Compra: mínimo R$ …, máximo R$ …, último R$ … às HH:mm. Venda: …". Abaixo vem um botão de disclosure "Ver dados em tabela" que expande a tabela de pontos (horário, compra, venda) e indica as lacunas como linhas "sem registro" no horário esperado. Teclado e leitor de tela acessam tudo.
- **Movimento reduzido**: sem animação de desenho. O gráfico aparece pronto (não há animação de qualquer forma; `prefers-reduced-motion` afeta só transições de navegação e indicadores, W-16).
- **Estado vazio**: "Sem dados de cotação nas últimas 24 horas", independente do bloco de volume (US8-5).

**Alternatives considered**: bibliotecas de gráfico KMP (Koalaplot, Vico multiplatform), descartadas porque o suporte a wasmJs e o controle de lacunas/estilo variam e a acessibilidade não é verificada. Também seria uma dependência a mais para um gráfico de duas linhas (Princípio V).

---

## W-14 Layout responsivo, tabelas e listas acessíveis

**Decision**:
- Breakpoints por `BoxWithConstraints` na raiz: **compacto** (< 600 dp), com `TopAppBar` + `ModalNavigationDrawer` (botão "Menu" acessível) e os 7 destinos; **médio** (600–839 dp), com `NavigationRail` e rótulos; **expandido** (≥ 840 dp), com `PermanentNavigationDrawer`. Os três indicam o destino atual com `selected` e `semantics { selected = true }` (FR-009). "Sair" fica no fim da navegação em todos.
- Conteúdo em coluna única com largura máxima de 720 dp centralizada. Nenhum `horizontalScroll` no nível de página (FR-044). Regra detekt/revisão: `horizontalScroll` só no `:ui/chart` (eixo), se necessário.
- **Extrato e Meus investimentos**: no compacto, cada lançamento/investimento é um **cartão** em `LazyColumn` com `semantics(mergeDescendants = true)` e texto completo lido de uma vez ("Compra de R$ 400,00, 0,00100000 BTC, cotação R$ 400.000,00, 09/11/2026 08:10"). No expandido, usa-se layout tabular com cabeçalho de colunas, cujas semantics de tabela dependem do resultado de A7 no W-00. Se não houver suporte a papel de tabela no espelho, mantém-se o cartão com texto completo também no desktop (fallback aceito).
- **Grupo de resgate (FR-029)**: cartão-contêiner com título "Resgate — total R$ X", sub-itens indentados e o rótulo "Reinvestimento — não altera o saldo" na linha de reinvestimento.
- **Centenas de linhas**: `LazyColumn` (virtualizada). A navegação por teclado entre linhas é por Tab nos itens focáveis. O spike (A1) confirma que a lista virtualizada não quebra a ordem do foco.

---

## W-15 Entrega: nginx do cliente, mesma origem

**Decision**:
- **Topologia**: novo serviço `web` no `docker-compose.yml`, publicado em `8081:8080` (porta própria, para não quebrar o `8080` atual da API). O nginx do `web` serve o bundle e faz `location /api/ { proxy_pass http://nginx:8080; }`, encaminhando ao nginx existente, que já balanceia entre as réplicas. O navegador vê **uma origem** (`http://localhost:8081`), sem CORS e sem mudança na API.
- **Imagem**: Dockerfile multi-stage em `web/Dockerfile`:
  1. `gradle:jdk21` executa `./gradlew :app:wasmJsBrowserDistribution`.
  2. `compress` pré-comprime os arquivos da distribuição (`.gz` com `gzip -9` e `.br` com `brotli -q 11`).
  3. `brotli-module` compila o `ngx_brotli` como módulo dinâmico contra **o mesmo** fonte/versão do nginx da imagem final.
  4. `nginxinc/nginx-unprivileged:1.27-alpine` (usuário não-root, porta 8080) com os módulos `ngx_http_brotli_static_module`/`filter` carregados.
- **nginx.conf** (`web/docker/nginx.conf`):
  - `types { application/wasm wasm; }` (o `mime.types` padrão já inclui wasm em versões recentes; declarado explicitamente por segurança).
  - `gzip_static on; brotli_static on;` (serve os pré-comprimidos) + `gzip on; gzip_types application/wasm application/javascript text/css application/json image/svg+xml;` como fallback.
  - **Cache**: arquivos com hash de conteúdo no nome (regex `\.[0-9a-f]{8,}\.(wasm|js|mjs)$`, inclusive os `.wasm` nomeados por hash que a distribuição Kotlin/Wasm gera) recebem `Cache-Control: public, max-age=31536000, immutable`. `index.html`, o JS de entrada sem hash e `composeResources/**` recebem `Cache-Control: no-cache` (revalidação por ETag). Se a distribuição não gerar hash para algum artefato pesado, a etapa de build adiciona fingerprint e reescreve as referências no `index.html`. Isso é confirmado na primeira build, com resultado registrado aqui.
  - **Fallback SPA**: `location / { try_files $uri $uri/ /index.html; }` (rotas usam fragmento, mas o fallback cobre links antigos/digitados).
  - **Cabeçalhos de segurança**: `Content-Security-Policy: default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self'; img-src 'self' data: blob:; font-src 'self'; connect-src 'self'; worker-src 'self' blob:; object-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `Permissions-Policy: camera=(), microphone=(), geolocation=()`. O `index.html` **não** tem script nem estilo inline (CSS de carregamento em arquivo). O spike verifica 0 violações de CSP no console. Se o Skiko/Compose exigir `blob:`/`data:` adicionais, acrescenta-se o mínimo e registra-se aqui.
  - `location /api/`: `proxy_pass http://nginx:8080;` (mantém `/api/v1/...`), repassando `Authorization` e `X-Correlation-Id`, com `proxy_read_timeout 30s`. Sem `access_log` de query strings sensíveis: o token vai em header e o access log padrão não registra headers (FR-008).
- **Healthcheck**: `wget -q -O /dev/null http://127.0.0.1:8080/index.html`, com `depends_on: nginx (service_healthy)`.
- **Orçamento de desempenho (SC-010)**: perfil de rede **"4G típico"** = 9.000 kbps de descida, 1.500 kbps de subida e 60 ms de RTT (CDP `Network.emulateNetworkConditions`). Metas: (a) navegação com o app carregado, com conteúdo ou carregamento em ≤ 1 s e dados em ≤ 3 s (p95); (b) primeira carga sem cache, com `#boot` em ≤ 1 s e app utilizável em ≤ 6 s. **Limite de tamanho do bundle**: ≤ 3,5 MB em brotli somando `*.wasm`, `*.js` e a fonte da primeira carga. A 9 Mbps, isso dá cerca de 3,1 s de download, o que deixa margem para compilar o Wasm e carregar os dados dentro de 6 s. A T222 mede e falha acima do limite. Se o limite for estourado, as alavancas em ordem são: subconjunto de fonte menor, `-Xwasm-opt`/otimização de tamanho da distribuição, remover dependências e só então rever a meta (com registro aqui).

**Resultado do spike (T015/T021, 2026-11-12)**: a CSP acima, sozinha, bloqueia o `<style>` que a CMP 1.12.2 injeta no shadow root do canvas (o `<input>` de apoio do IME fica visível e a página rola). O mínimo necessário é acrescentar o hash desse estilo a `style-src`: `style-src 'self' 'sha256-2DtT88WYuzB8DiHdoLqEYkONEwxf9gqsOdTY44QuLA4='` (igual nos 3 motores; não usar `'unsafe-inline'`). O hash muda quando a CMP mudar esse estilo, e o E2E (0 violações de CSP no console) detecta isso. Com esse acréscimo, o spike roda sem violação de CSP em Chromium, Firefox e WebKit. Nada mais precisou ser liberado (`blob:`/`data:` não foram exigidos pelo Skiko).

**Rationale**: mesma origem elimina CORS e expõe o token a menos superfícies. Imagem não-root, como a da API. Pré-compressão máxima ajuda SC-010 (o bundle Compose/Skiko pesa vários MB).

**Alternatives considered**: servir o bundle pelo nginx existente (mudaria a configuração da 001 e misturaria responsabilidades). Brotli dinâmico (custa CPU por requisição; o pré-comprimido é melhor para estáticos). Só gzip (mais simples, mas cerca de 15–20% maior no `.wasm`; mantido como fallback se o módulo brotli falhar na build, com registro no Complexity Tracking). Imagem de terceiros com brotli embutido (cadeia de suprimentos não oficial).

---

## W-16 index.html, carregamento, tema, fonte e movimento

**Decision**:
- **`index.html`** (`:app/src/wasmJsMain/resources/`): `<html lang="pt-BR">`, `<title>Investimento em Bitcoins</title>`, `<link rel="stylesheet" href="loading.css">`, um bloco `<div id="boot" role="status">` com marca, spinner CSS (desligado em `prefers-reduced-motion`) e o texto "Carregando…". Também tem `<noscript>` em pt-BR, as duas regiões `aria-live` do W-09 e um `<canvas>`/contêiner da CMP. Ao primeiro frame, o `main()` remove o `#boot` e move o foco para o primeiro cabeçalho. Detecção de WasmGC sem script inline: o arquivo externo `compat.js` (compatível com a CSP), carregado antes do app, testa WasmGC e exception handling (os dois exigidos pela Kotlin/Wasm, W-01) com `WebAssembly.validate` sobre dois módulos mínimos. Se algum falhar, mostra "Seu navegador não é compatível. Atualize para a versão mais recente." no `#boot` e não carrega o `app.js`. O conteúdo inicial aparece em menos de 1 s porque é HTML/CSS estático (SC-010).
- **Pré-carga**: o `index.html` versionado faz preload só da fonte (caminho estável). Os `<link rel="preload" as="fetch" crossorigin>` dos `.wasm` com hash são injetados no build da imagem por `web/docker/inject-preload.sh`, antes da pré-compressão, porque os nomes só existem depois da distribuição.
- **Tema M3**: `ColorScheme` claro e escuro **gerados a partir da semente `#F7931A`** com o algoritmo do Material 3 (HCT/tonal palettes). A geração é feita **uma vez** com o Material Theme Builder (export Compose) e commitada como constantes em `:ui/theme/Color.kt`, com comentário de origem e semente. Não há biblioteca de geração em runtime (Princípio V). O laranja `#F7931A` sobre branco tem contraste de cerca de 2,4:1 e por isso **não** é usado para texto. O esquema usa os tons adequados (por exemplo, `primary` claro de tom ~40, marrom-alaranjado) e o laranja da semente aparece só em elementos não textuais com ≥ 3:1.
- **Contraste verificado por teste** (`:ui` `commonTest`, `ThemeContrastTest`): calcula a razão WCAG (luminância relativa sRGB) para os pares `on*`/`*` (`onPrimary/primary`, `onSurface/surface`, `onSurfaceVariant/surface`, `onError/error`, `onPrimaryContainer/primaryContainer`, …) e exige ≥ 4,5:1. Para cores de traço do gráfico, indicadores de foco e bordas de campo contra `surface`, exige ≥ 3:1, nos dois temas. (Cálculo de cor em `Float` é aceitável: não é valor financeiro.)
- **Tema do sistema**: `isSystemInDarkTheme()` da CMP (que no web consulta `prefers-color-scheme`). Mudanças em runtime são observadas via `MediaPreferences` (W-09). Sem seletor manual (Out of Scope).
- **Fonte**: **Inter** (OFL) Regular/Medium/SemiBold em `composeResources/font/`, com subconjunto Latin + Latin-1 + símbolos usados (R$, −, ↑, ↓) para reduzir o tamanho. Números com `fontFeatureSettings = "tnum"` (algarismos tabulares, para alinhar valores). Embarcar a fonte é necessário porque o canvas não usa as fontes do sistema e precisa dos glifos acentuados do pt-BR.
- **Movimento reduzido**: `LocalReducedMotion` (CompositionLocal) alimentado por `MediaPreferences`. Com `true`, transições de navegação são `EnterTransition.None`/`ExitTransition.None`, indicadores de progresso indeterminados viram texto "Carregando…" estático e não há animação de valores (FR-045).
- **Strings**: todas em `composeResources/values/strings.xml` (pt-BR como padrão e único, FR-048). Plurais via `pluralStringResource`.

---

## W-17 Testes ponta a ponta: Playwright for Java + axe-core

**Decision**: módulo `:e2e` (JVM, Kotlin, JUnit 5) com Playwright 1.64.0 (Chromium, Firefox e WebKit) e `axe-core/playwright` 4.14.0. Roda contra `docker compose --profile stub up` (API + quote-stub + `web`) na URL `http://localhost:8081`, sob a tarefa `./gradlew :e2e:e2eTest` (fora do `check`).
- **Interação com a UI em canvas**: por meio do **espelho de acessibilidade** que a CMP gera no DOM (`getByRole`, `getByLabel`) e por **teclado**. O spike (W-00) confirma quais seletores funcionam. Se o espelho não expuser papéis suficientes, os testes navegam só por teclado e verificam pelo espelho/anúncios. Coordenadas de clique não são usadas (são frágeis).
- **axe**: `AxeBuilder(page).withTags(wcag2a, wcag2aa, wcag21a, wcag21aa).analyze()` em cada tela e estado (vazio, erro, carregado), exigindo `violations.isEmpty()`. **Limitação registrada**: o axe vê só o espelho DOM. Contraste e foco visível desenhados no canvas são cobertos pelo `ThemeContrastTest` e pelo checklist manual de SC-007 (quickstart §6).
- **Cenários**: fluxos críticos (cadastro → depósito → compra → resgate → extrato); envio duplicado (SC-004): `dblclick`, Enter ×5, toque duplo emulado, recarregar durante o envio e voltar/avançar após o resultado, verificando pela API (cliente HTTP Java em `/api/v1/me/statement`) que existe **exatamente um** lançamento; 320, 375, 768, 1280 e 1920 px; zoom 200% (viewport de 640 × 400 com `deviceScaleFactor: 2`, equivalente a 1280 px a 200%) sem rolagem horizontal (verificado por `scrollWidth <= clientWidth` do documento **e** pelas caixas dos nós do espelho dentro do viewport); expiração de sessão (SC-009) com `page.clock().install()`/`fastForward()` além de `expiresIn` e 401 simulado por `page.route`; multiaba (logout em uma aba propaga); retorno pós-login com filtro; destino externo recusado; cotação indisponível (quote-stub em modo de falha) com SC-006; SC-005 (instante ≤ 25 s por 2 min).
- **Resultado incerto**: `page.route("**/api/v1/me/purchases", route -> route.abort())` **após** deixar a requisição chegar (via proxy de teste que encaminha e depois aborta a resposta). A interface mostra a orientação de conferir saldo/extrato, sem botão de reenvio.

**Alternatives considered**: Playwright TypeScript (pede Node no repositório; o pedido é Java/Gradle). Selenium (sem `clock`, `route` e multiaba tão diretos).

---

## W-18 Testes unitários (Princípio IV)

**Decision**: `kotlin("test")` + `kotlinx-coroutines-test` em `commonTest`, executados pelo `wasmJsNodeTest`/`wasmJsBrowserTest` via `./gradlew check`:
- `:core`: value objects (limites, ida e volta, sem notação científica), parsing (tabela completa do W-11 e Edge Cases), formatação, estimativas (truncamento, < 1 satoshi), `StatementGrouper` (agrupamento, soma exata, grupo cortado pelo filtro), `ChartSeries` (lacunas), `AppRoute`/`ReturnDestination` (ida e volta, rotas maliciosas) e `BrasiliaTime` (fuso diferente do dispositivo).
- `:state`: cada ViewModel com fakes das portas e `TestScope` (tempo virtual): estados carregando/vazio/erro (FR-042), máquina das operações (single-flight, SC-004), `AutoRefresher` (10 s, visibilidade, pausa, sem sobreposição), anúncios só em transição (FR-047) e `SessionManager` (expiração, 401, protocolo entre abas).
- `:data`: `MockEngine` + fixtures (W-12) para cada rota e **cada `code`** (SC-003), mais transporte falho em GET vs POST financeiro (Uncertain), corpo fora do contrato, ausência de log/URL com token (FR-008) e header `Authorization` presente só nas rotas protegidas.
- `:ui`: `ThemeContrastTest` e `ErrorMessagesTest` (todo `ApiError`/`OperationError` tem mensagem não vazia, sem palavras técnicas como "code", "exception", "null" ou HTTP). Testes de UI do Compose (`runComposeUiTest`) **opcionais** para componentes críticos (campo de valor com erro, botão em processamento), se a infraestrutura de teste wasm da CMP 1.12 for estável. Senão, essa cobertura fica com o E2E.
- **Execução**: `wasmJs { browser { testTask { … } }; nodejs() }`. Os testes de `:core`/`:state`/`:data` usam Node (rápidos, sem navegador). `:ui` usa o Chrome headless pelo executor de testes de navegador da KGP (`browser { testTask { useKarma { useChromeHeadless() } } }`, T010), porque os testes leem Compose Resources. No CI, o Chrome é instalado pelo job `web` (T100). Alternativa registrada: Node, se a CMP 1.12 suportar Compose Resources nos testes em Node; se a KGP 2.4.21 indicar outro executor para wasmJs no navegador, usar o indicado e registrar aqui.

**Resultado da Phase 1 (T010, 2026-11-12)**: a KGP 2.4.21 aceitou `useKarma { useChromeHeadless() }` sem aviso de depreciação, então o executor foi mantido. O plugin Compose 1.12.2 exige `binaries.executable()` no alvo `wasmJs` do `:ui` (tarefa `checkComposeUiTestConfigurationForWasmJs`, CMP-4906): sem o bundle do webpack, o runtime Skiko não carrega nos testes de UI. O `:data` declara `browser()` com a tarefa de teste do navegador desligada, para que seus testes rodem só em Node.

---

## W-19 Convenções e qualidade

**Decision**:
- Código em **inglês**; textos de UI em pt-BR **somente** em `strings.xml`. Pacote raiz `com.kotlinbitcoins.web.<módulo>`. Os pacotes por feature dentro dos módulos são `account`, `dashboard`, `deposit`, `purchase`, `investments`, `redemption`, `statement`, `market`, `session` e `shared`.
- Nomes: telas `XxxScreen`, ViewModels `XxxViewModel`, estados `XxxUiState`, portas `XxxGateway`/substantivo, DTOs `XxxDto`, booleanos `is`/`has`. Composables em PascalCase.
- **ktlint** (via `org.jlleitschuh.gradle.ktlint`, com `.editorconfig` próprio em `web/`, incluindo `ktlint_function_naming_ignore_when_annotated_with = Composable`) e **detekt** (com `config/detekt/detekt.yml` em `web/`, mais `detekt-precision.yml` em `:core`/`:state`/`:data` e `detekt-ui.yml` em `:ui`, conforme W-06) e a tarefa `checkNoFloatingPoint`, todos no `./gradlew check`.
- **CI** (`.github/workflows/ci.yml`): o job `build` atual continua igual (passa a incluir o `WebClientFixturesContractTest` automaticamente). O novo job `web` (JDK 21, `setup-gradle`, `working-directory: web`) roda `./gradlew check` + `:app:wasmJsBrowserDistribution` + `docker build -f web/Dockerfile .`. O novo job `web-e2e` (`needs: [build, web]`) roda `docker compose --profile stub up -d --wait`, instala os navegadores do Playwright (`com.microsoft.playwright.CLI install --with-deps`), roda `./gradlew :e2e:e2eTest` e publica os relatórios como artefato.
