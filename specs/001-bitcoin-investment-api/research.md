# Research: API de Investimento em Bitcoins

**Feature**: 001-bitcoin-investment-api | **Date**: 2026-11-09 | **Plan**: [plan.md](./plan.md)

Todas as decisões de stack foram fornecidas pelo responsável técnico. Este documento registra, para cada uma, a justificativa, as alternativas consideradas e os detalhes de projeto que elas exigem. Não há itens `NEEDS CLARIFICATION`.

---

## R-01 Linguagem, runtime e build

- **Decision**: Kotlin 2.x sobre JDK 21 (LTS); Gradle com Kotlin DSL e version catalog (`gradle/libs.versions.toml`); build multi-módulo.
- **Rationale**: Kotlin dá sealed interfaces e `when` exaustivo, base natural para Erro como Valor (Princípio III). JDK 21 é LTS. O version catalog centraliza versões dos 5 módulos.
- **Alternatives considered**: Java 21 (sem `when` exaustivo sobre tipos selados com a mesma ergonomia, e mais boilerplate em value objects); Maven (menos ergonômico para multi-módulo com regras de dependência por módulo).

## R-02 Arquitetura multi-módulo e regra de dependência

- **Decision**: módulos `domain`, `application`, `infrastructure`, `api`, `bootstrap`.
  - `domain`: Kotlin puro, sem dependências externas além da stdlib.
  - `application`: depende só de `domain`; declara as portas.
  - `infrastructure`: depende de `application` (e transitivamente de `domain`); implementa as portas com Spring, JPA, Redis, RabbitMQ, HTTP.
  - `api`: depende de `application`; controladores, DTOs, mapeamento de erros.
  - `bootstrap`: depende de todos; classe `@SpringBootApplication`, `@Configuration` com `@Bean` para cada caso de uso e cada adaptador.
- **Rationale**: o Princípio I passa a ser verificado pelo compilador. `domain` e `application` não conseguem importar Spring ou Hibernate porque essas bibliotecas não estão no classpath desses módulos. `api` não enxerga `infrastructure`.
- **Alternatives considered**: módulo único com pacotes + ArchUnit (a regra vira teste, não restrição de compilação; mais fácil de violar); Hexagonal com módulos por feature (mais módulos sem ganho para o escopo atual — Princípio V).
- **Detalhe**: os tipos de valor do domínio (`Money`, `BtcAmount`) fazem parte do contrato público dos casos de uso. `api` os recebe via `application` (dependência `api(project(":domain"))` em `application`), sem acessar entidades ou serviços de domínio diretamente.

## R-03 Erro como valor

- **Decision**: tipo próprio `Outcome<out E, out T>` (sealed: `Success`, `Failure`) em `domain`, com `map`, `flatMap`, `fold` mínimos; uma `sealed interface` de erro por operação (`RegisterAccountError`, `LoginError`, `DepositError`, `PurchaseError`, `RedemptionError`, `StatementError`, `QuoteError`).
- **Rationale**: atende o Princípio III sem dependências externas (Arrow, `kotlin.Result` descartados por decisão do responsável técnico; `kotlin.Result` além disso só carrega `Throwable`).
- **Alternatives considered**: Arrow `Either` (dependência pesada para uso mínimo); exceções de domínio + `@ControllerAdvice` (viola o Princípio III).
- **Mapeamento HTTP** (camada `api`, RFC 9457, `application/problem+json`, campo `code` estável):

| code | HTTP | Origem |
|------|------|--------|
| `VALIDATION_ERROR` | 400 | formato de entrada inválido (campos ausentes, e-mail malformado, senha curta, JSON inválido) |
| `INVALID_AMOUNT` | 400 | valor ≤ 0, não numérico, > 2 casas, ou compra que resulta em < 1 satoshi |
| `INVALID_CREDENTIALS` | 401 | login com e-mail inexistente ou senha errada (mesma resposta) |
| `UNAUTHORIZED` | 401 | token ausente, malformado, adulterado ou expirado |
| `EMAIL_ALREADY_IN_USE` | 409 | cadastro com e-mail existente |
| `INSUFFICIENT_BALANCE` | 422 | compra acima do saldo |
| `REDEMPTION_EXCEEDS_AVAILABLE` | 422 | resgate acima do valor atual total (inclui cliente sem investimentos) |
| `INVALID_DATE_RANGE` | 422 | intervalo do extrato com início > fim ou data malformada |
| `QUOTE_UNAVAILABLE` | 503 | sem cotação válida com até 60 s |
| `SERVICE_UNAVAILABLE` | 503 | falha de infraestrutura (banco, Redis, broker) convertida em valor pelo adaptador |

- **FR-033**: nenhuma rota recebe identificador de recurso de outro cliente. Todas as rotas protegidas operam sobre a conta do `sub` do token (`/me`), então não há caminho que revele a existência de recursos alheios. Não existe 403/404 por propriedade.

## R-04 Precisão financeira (FR-032) e regras de arredondamento

- **Decision**:
  - `Money`: `BigDecimal` com escala fixa 2; construção recusa escala > 2 (sem arredondar silenciosamente). Coluna `NUMERIC(19,2)`.
  - `BtcAmount`: `Long` em satoshis (1 BTC = 100 000 000 sat). Coluna `BIGINT`. Formatação com 8 casas só na borda (`api`).
  - `QuotePrice`: `BigDecimal` positivo com a escala recebida da fonte (até 8 casas), sem arredondar (FR-011: preços "exatamente os fornecidos"). Coluna `NUMERIC(19,8)`.
  - Todas as divisões usam `RoundingMode` explícito; nenhum `double`/`float` em código de negócio.
- **Regras** (implementadas e testadas em `domain`):

| Operação | Fórmula | Arredondamento |
|----------|---------|----------------|
| Compra (FR-013) | `sats = valor × 10⁸ ÷ cotaçãoVenda` | `FLOOR` no satoshi; `sats < 1` → `INVALID_AMOUNT` |
| Valor bruto atual (FR-017) | `valor = sats × cotaçãoCompra ÷ 10⁸` | `FLOOR` no centavo |
| Variação % (FR-017) | `(cotaçãoCompraAtual − cotaçãoDaCompra) ÷ cotaçãoDaCompra × 100` | `HALF_UP`, 2 casas (só exibição) |
| Liquidação total (FR-019a-1) | crédito = valor bruto atual | `FLOOR` no centavo |
| Liquidação parcial (FR-019a-2) | `satsVendidos = faltante × 10⁸ ÷ cotaçãoCompra`; crédito = `faltante` | `CEILING` no satoshi |
| Arredondamento consome tudo (FR-019a-3) | `satsVendidos ≥ sats` → liquida tudo, crédito = `faltante`, sem reinvestimento | — |
| Reinvestimento (FR-020) | `satsResidual = sats − satsVendidos`; `valorInvestido = satsResidual × cotaçãoOriginal ÷ 10⁸` | `FLOOR` no centavo |
| Total disponível (FR-022) | soma dos valores brutos atuais | soma de valores já `FLOOR` |

- **Algoritmo de resgate** (`RedemptionPlanner`, função pura do domínio):
  1. Se `pedido > totalDisponível` → `RedemptionExceedsAvailable`.
  2. Percorre investimentos ativos em ordem FIFO (`purchasedAt`, `fifoSequence`); `faltante = pedido`.
  3. Para cada investimento: `v = valorBrutoAtual`. Se `faltante ≥ v` → liquidação total (crédito `v`), `faltante −= v`. Senão → liquidação parcial com as regras acima e encerra o laço.
  4. Termina quando `faltante = 0`. Garantias testadas: Σcréditos = pedido; Σsats vendidos + sats residual = sats originais; nunca há reinvestimento com 0 sat.
- **Alternatives considered**: `BigDecimal` com escala 8 para BTC (funciona, mas `Long` em satoshi torna impossível representar fração de satoshi e simplifica somas e comparações); `HALF_EVEN` em tudo (rejeitado na clarificação de 2026-11-09).

## R-05 Persistência e concorrência

- **Decision**: PostgreSQL 16, Spring Data JPA/Hibernate 6 com `ddl-auto=validate`, migrações Flyway em `infrastructure/src/main/resources/db/migration`. Entidades JPA (`*JpaEntity`) separadas dos modelos de domínio, com mappers em `infrastructure`.
- **Concorrência (FR-034, SC-005)**: depósito, compra e resgate começam com `SELECT ... FROM accounts WHERE id = ? FOR UPDATE` (porta `AccountRepository.findByIdForUpdate`). Todas as operações que alteram saldo ou investimentos de uma conta ficam serializadas por conta; contas diferentes não competem. Restrição `CHECK (balance >= 0)` como rede de segurança (FR-010).
- **Atomicidade (FR-034)**: porta `TransactionRunner { fun <E, T> run(block: () -> Outcome<E, T>): Outcome<E, T> }`. A implementação usa `TransactionTemplate` e chama `status.setRollbackOnly()` quando o bloco devolve `Failure`, de modo que um erro como valor desfaz tudo sem lançar exceção. Toda porta devolve `Outcome<InfrastructureFailure, T>`: cada adaptador captura as próprias exceções (ex.: `DataAccessException`, `RedisConnectionFailureException`) e as converte antes de devolver, de modo que nenhuma exceção atravessa camadas (Princípio III). O `TransactionRunner` recebe `onInfrastructureFailure: (InfrastructureFailure) -> E` e converte também as falhas de commit. Cada `sealed interface` de erro de operação inclui a variante `ServiceUnavailable`, mapeada para `SERVICE_UNAVAILABLE` (503).
- **Instantes**: colunas `TIMESTAMPTZ` em UTC; porta `Clock` (`fun now(): Instant`) injetada; conversão para `America/Sao_Paulo` e ISO-8601 com offset só em `api` (FR-034a). Limites de "dia corrente" e de datas do extrato são calculados em `application` a partir de um `ZoneId` configurado (`America/Sao_Paulo`) e convertidos em `Instant` antes de chegar ao repositório.
- **Alternatives considered**: lock otimista com `@Version` (exige retry em conflito, e retry é controle de fluxo adicional sem ganho para operações curtas por conta); isolamento `SERIALIZABLE` global (mais abortos, mais difícil de testar); saldo derivado só do extrato (consulta de saldo O(n) e lock mais difícil).

## R-06 Cotação: fonte, cache e indisponibilidade

- **Decision**:
  - Fonte: Mercado Bitcoin API v1 `GET https://www.mercadobitcoin.net/api/BTC/ticker/` (URL configurável em `quote.provider.url`). Campos usados: `ticker.sell` → **cotação de venda** (compras do cliente); `ticker.buy` → **cotação de compra** (resgates e posição). Valores chegam como string e são lidos como `BigDecimal` sem passar por `double`.
  - Instante da cotação (FR-012a): o momento em que a resposta do MB é recebida (`Clock.now()`), não `ticker.date`. Isso torna a janela de 60 s mensurável pelo relógio do próprio sistema.
  - Cliente HTTP: `RestClient` (Spring 6) com timeout de conexão de 1 s e de leitura de 2 s; qualquer exceção, status ≠ 2xx ou JSON inválido vira `QuoteProviderError` no adaptador.
  - Obtenção (`QuoteService` em `application`, sobre as portas `QuoteProvider` e `QuoteCache`):
    1. Lê do cache Redis compartilhado; se `idade < 10 s`, usa.
    2. Senão, consulta o MB; em sucesso grava no cache (TTL 60 s) e usa.
    - O cache é best-effort: a porta `QuoteCache` é a única exceção à regra de `Outcome<InfrastructureFailure, T>` (R-05). Falha do Redis equivale a cache vazio (`get` → `null`, `put` ignorado) e só é registrada em log; o fluxo segue para a fonte e, sem ela, para `QuoteUnavailable`.
    3. Se o MB falhar e existir cotação no cache com `idade ≤ 60 s`, usa (com seu instante original).
    4. Senão → `QuoteUnavailable` (503).
  - Uma única cotação é obtida por operação e é a registrada (edge case "cotação muda durante a operação").
- **Rationale**: o cache de 10 s evita uma chamada externa por requisição (SC-006) e mantém as réplicas usando a mesma cotação. A janela de 60 s implementa FR-012.
- **Risco registrado**: em 2026-11-09 a API v1 respondeu **HTTP 521** (origem fora do ar atrás da Cloudflare) e a API v4 bloqueou o acesso automatizado via desafio da Cloudflare. Mitigação: o adaptador `MercadoBitcoinQuoteProvider` fica atrás da porta `QuoteProvider`. A URL é configurável, todos os testes usam WireMock, e trocar de fonte significa escrever um novo adaptador, sem tocar `domain`/`application`. Enquanto a fonte estiver fora, as rotas dependentes respondem `QUOTE_UNAVAILABLE`, como a spec exige.
- **Alternatives considered**: cache em memória por réplica (réplicas veriam cotações diferentes e cada uma chamaria a fonte); cotação persistida só no Postgres (latência maior em toda consulta; o histórico já fica no Postgres, só que com outra granularidade).

## R-07 Histórico de cotação e expurgo (FR-027 a FR-029)

- **Decision**:
  - Job `CaptureQuoteSnapshotJob` com `@Scheduled(cron = "0 */10 * * * *", zone = "America/Sao_Paulo")` + `@SchedulerLock` (ShedLock, tabela `shedlock` no Postgres). O slot é `now` truncado ao múltiplo de 10 min.
  - O job consulta **diretamente** o `QuoteProvider` (não usa o fallback de 60 s) e, em sucesso, grava em `quote_history` e atualiza o cache. Em falha, o ponto é omitido (FR-028) e a falha é logada.
  - `quote_history.slot_at` com `UNIQUE`; inserção `ON CONFLICT (slot_at) DO NOTHING`, o que torna o job idempotente.
  - Job `PurgeQuoteHistoryJob` diário (03:00 America/Sao_Paulo) com ShedLock: `DELETE FROM quote_history WHERE slot_at < now() - interval '90 days'` (SC-009 tolera até 91 dias).
  - Consulta: `slot_at > now - 24h` em ordem crescente (no máximo 144 pontos).
- **Alternatives considered**: usar o fallback de 60 s no job (a clarificação diz "falha da fonte = ponto omitido"; cotação reaproveitada criaria ponto com preço de outro instante); Quartz cluster (pesado demais para 2 jobs).

## R-08 Notificações por e-mail (FR-030, FR-031)

- **Decision**: Transactional Outbox.
  1. O caso de uso chama `NotificationPublisher.publish(event)`, cujo adaptador grava em `outbox_events` **na mesma transação** da operação financeira.
  2. `OutboxRelayJob` (a cada 2 s, em todas as réplicas, `SELECT ... FOR UPDATE SKIP LOCKED LIMIT 100`) publica no RabbitMQ (exchange `notifications`, routing key `email`) com publisher confirms e marca `published_at`. O `SKIP LOCKED` já divide o trabalho entre réplicas sem duplicar, então o relay não precisa de ShedLock.
  3. `EmailNotificationListener` consome a fila `notifications.email`, monta o texto em português e chama `EmailSender`.
  4. Retry por valor, com `AcknowledgeMode.MANUAL`: se `SendEmailNotification` devolver `Failure`, o listener republica a mensagem na fila `notifications.email.retry` (sem consumidor, com DLX de volta para o exchange `notifications`, routing key `email`), com `expiration` de 1, 2, 4, 8 e 16 s e o header `x-attempt` incrementado, e dá `basicAck` na original. Na 5ª falha, publica em `notifications.email.dlq`. Nenhuma exceção é usada para acionar o retry (Princípio III). Cada falha é logada com `eventId` e correlation ID, sem dados sensíveis.
  - Entrega *at-least-once*. O consumidor deduplica por `eventId`: depois do envio, marca `outbox_events.sent_at` (`UPDATE ... WHERE id = ? AND sent_at IS NULL`) e ignora mensagens cujo evento já tem `sent_at`. A deduplicação fica no Postgres, sem infraestrutura extra.
- **EmailSender**: `BrevoEmailSender` (POST `https://api.brevo.com/v3/smtp/email`, chave via `BREVO_API_KEY`) no perfil `prod`; `SmtpEmailSender` apontando para Mailpit nos perfis `dev`/`test`.
- **Rationale**: o outbox garante que todo e-mail corresponde a uma operação confirmada e que nenhuma falha de e-mail desfaz a operação (FR-031). A fila desacopla o envio da requisição (SC-007).
- **Alternatives considered**: envio síncrono após commit (perde e-mail se o processo cair entre o commit e o envio); `@TransactionalEventListener` + `@Async` (sem durabilidade); outbox + relay enviando direto ao EmailSender sem broker (viável; ver Complexity Tracking no plan).

## R-09 Autenticação e segurança

- **Decision**:
  - Senha: BCrypt custo 12 (porta `PasswordHasher`, adaptador com `BCryptPasswordEncoder`).
  - Token: JWT HS256, `sub` = id da conta (UUID), `exp` = emissão + 1 h, sem refresh (porta `TokenIssuer`, adaptador com `NimbusJwtEncoder`). Segredo em `JWT_SECRET` (≥ 256 bits), nunca no código.
  - Validação: Spring Security OAuth2 Resource Server (`NimbusJwtDecoder.withSecretKey`). Rotas públicas: `POST /api/v1/accounts` e `POST /api/v1/auth/login` (mais `/actuator/health/**` e Swagger UI). O `AuthenticationEntryPoint` responde Problem Details `UNAUTHORIZED`.
  - FR-005 e enumeração: quando o e-mail não existe, o caso de uso ainda executa uma verificação BCrypt contra um hash fictício, para equalizar o tempo de resposta.
  - E-mail normalizado (`trim` + minúsculas) antes de unicidade e login (FR-002); `UNIQUE` em `accounts.email`.
  - Segredos: `.env.example` versionado; `.env` ignorado.
  - Rate limiting: fora de escopo (não exigido pela spec).
- **Alternatives considered**: RS256 (sem necessidade de terceiros validarem o token); sessão com cookie (API stateless e várias réplicas).

## R-10 Contrato HTTP

- **Decision**: REST sob `/api/v1`; campos JSON em inglês; `detail` dos erros em português. Valores monetários e de BTC como **string** (`"100.50"`, `"0.00100000"`). Instantes ISO-8601 com offset de America/Sao_Paulo. Contrato-fonte em [`contracts/openapi.yaml`](./contracts/openapi.yaml); springdoc gera `/v3/api-docs` e Swagger UI, e um teste compara o gerado com o contrato.
- **Validação**: na `api`, DTOs com Bean Validation (`@NotBlank`, `@Email`, `@Size(min = 8)`) e parsing estrito de valor (`^\d+(\.\d{1,2})?$`, sem notação científica) → `INVALID_AMOUNT`. Invariantes reforçadas nos value objects do domínio.
- **Alternatives considered**: números JSON (perda de precisão em clientes JavaScript); GraphQL (sem demanda).

## R-11 Logs e observabilidade

- **Decision**: Logback com encoder JSON (logstash-logback-encoder) em stdout; filtro `CorrelationIdFilter` lê ou gera `X-Correlation-Id`, coloca no MDC e devolve no header de resposta. Mascaramento: nenhum DTO de entrada com senha é logado; um conversor do encoder mascara `password`, `token`, `Authorization`. Actuator expõe só `health`, `readiness`, `liveness`.
- **Alternatives considered**: OpenTelemetry/Prometheus (fora de escopo por decisão explícita).

## R-12 Testes

- **Decision**: JUnit 5 + AssertJ + MockK.
  - `domain`: unitários das regras de arredondamento, `RedemptionPlanner` (incluindo testes por propriedade de conservação de BTC e de centavos), value objects.
  - `application`: unitários dos casos de uso com MockK nas portas.
  - `infrastructure`: integração com Testcontainers (PostgreSQL 16, Redis, RabbitMQ) e WireMock (Mercado Bitcoin, Brevo).
  - `bootstrap`: testes de contrato com MockMvc sobre a aplicação inteira (Testcontainers), cobrindo cada `code` de R-03 (SC-010), isolamento entre clientes (SC-004), concorrência com N compras/resgates paralelos (SC-005) e o fluxo E2E dos cenários da spec.
  - Clock fixo/controlável em testes; a captura e o expurgo de histórico são testados chamando o job diretamente.
- **Alternatives considered**: H2 em testes (diverge de Postgres em `FOR UPDATE SKIP LOCKED`, `ON CONFLICT`, `TIMESTAMPTZ`).

## R-13 Execução, escala e entrega

- **Decision**: aplicação stateless; Dockerfile multi-stage (`gradle:jdk21` → `eclipse-temurin:21-jre`, usuário não-root, `bootJar` do módulo `bootstrap`). `docker-compose.yml` com `app` (2 réplicas), `nginx` (round-robin), `postgres:16`, `redis:7`, `rabbitmq:3-management`, `mailpit`, todos com healthcheck. CI no GitHub Actions: `./gradlew check` (testes + ktlint + detekt) e `docker build`.
- **Padronização**: código em inglês; classes PascalCase (substantivos), métodos camelCase (verbos), booleanos com prefixo `is`/`has`. Pacote raiz `com.kotlinbitcoins`. ktlint e detekt rodam no `check` e falham o build.
