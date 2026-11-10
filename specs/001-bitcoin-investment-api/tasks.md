---

description: "Lista de tarefas da feature 001-bitcoin-investment-api"
---

# Tasks: API de Investimento em Bitcoins

**Input**: documentos de design em `/specs/001-bitcoin-investment-api/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/openapi.yaml](./contracts/openapi.yaml), [quickstart.md](./quickstart.md)

**Tests**: **obrigatórios**. A Constitution (Princípio IV) exige TDD (Red → Green → Refactor), unitários de domínio e casos de uso sem infraestrutura e testes de contrato para todos os endpoints. Em cada fase, as tarefas de teste vêm antes da implementação e **MUST falhar** antes de a implementação começar.

**Organization**: tarefas agrupadas por user story, para que cada uma seja implementada e testada de forma independente.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivos diferentes, sem dependência de tarefa incompleta)
- **[Story]**: user story da tarefa (US1 … US10)
- Toda tarefa traz o caminho exato do arquivo

## Path Conventions

Projeto Gradle multi-módulo (plan.md → Project Structure). Pacote raiz `com.kotlinbitcoins`.

| Módulo | Código | Testes |
|--------|--------|--------|
| `domain` | `domain/src/main/kotlin/com/kotlinbitcoins/domain/` | `domain/src/test/kotlin/com/kotlinbitcoins/domain/` |
| `application` | `application/src/main/kotlin/com/kotlinbitcoins/application/` | `application/src/test/kotlin/com/kotlinbitcoins/application/` |
| `infrastructure` | `infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/` | `infrastructure/src/test/kotlin/com/kotlinbitcoins/infrastructure/` |
| `api` | `api/src/main/kotlin/com/kotlinbitcoins/api/` | `api/src/test/kotlin/com/kotlinbitcoins/api/` |
| `bootstrap` | `bootstrap/src/main/kotlin/com/kotlinbitcoins/bootstrap/` | `bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/` |

## Regras transversais (valem para todas as tarefas)

- `domain` e `application` MUST NOT importar Spring, JPA, Jackson ou qualquer biblioteca além da stdlib Kotlin (R-02). O compilador garante isso pelas dependências dos módulos.
- Falhas previsíveis são `Outcome.Failure` com erro tipado (`sealed interface` por operação). Exceções só dentro de adaptadores, convertidas antes de cruzar para `application` (R-03).
- Toda porta que toca infraestrutura devolve `Outcome<InfrastructureFailure, T>`; adaptadores capturam suas exceções e convertem. Única exceção: `QuoteCache` é best-effort (falha do Redis = cache vazio, só log; R-06). Toda `sealed interface` de erro de operação inclui `ServiceUnavailable` (→ `SERVICE_UNAVAILABLE` 503). Nenhuma exceção é usada para controle de fluxo, nem para acionar retry (R-05, R-08).
- Sem `Double`/`Float` em código de negócio. R$ = `Money` (escala 2), BTC = `BtcAmount` (satoshis `Long`), cotação = `QuotePrice` (escala da fonte, ≤ 8). Toda divisão usa `RoundingMode` explícito (R-04).
- Tempo vem sempre da porta `Clock`; nada de `Instant.now()` fora de `SystemClock`. O fuso de negócio é `America/Sao_Paulo` (FR-034a).
- Respostas de erro: RFC 9457 (`application/problem+json`) com `code` estável e `detail` em português, sem detalhes internos (FR-035).
- Código em inglês; classes PascalCase, métodos camelCase, booleanos `is`/`has`. ktlint e detekt rodam no `./gradlew check`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: esqueleto multi-módulo, build, qualidade de código e ambiente local.

- [X] T001 Criar o Gradle wrapper (Gradle 8.x) e `settings.gradle.kts` com `rootProject.name = "kotlin-bitcoins"` e `include("domain", "application", "infrastructure", "api", "bootstrap")`; criar em cada módulo os diretórios `src/main/kotlin/com/kotlinbitcoins/<módulo>/` e `src/test/kotlin/com/kotlinbitcoins/<módulo>/`
- [X] T002 Criar o version catalog em `gradle/libs.versions.toml` com Kotlin 2.x, Spring Boot 3.x (+ plugin `io.spring.dependency-management`), `kotlin("plugin.spring")`, `kotlin("plugin.jpa")`, Flyway (`flyway-core`, `flyway-database-postgresql`), driver PostgreSQL, ShedLock (`shedlock-spring`, `shedlock-provider-jdbc-template`), `springdoc-openapi-starter-webmvc-ui`, `logstash-logback-encoder`, `jackson-module-kotlin`, JUnit 5, AssertJ, MockK, `springmockk`, Testcontainers (`junit-jupiter`, `postgresql`, `rabbitmq`), WireMock, plugins ktlint e detekt
- [X] T003 Configurar `build.gradle.kts` da raiz: toolchain JDK 21 e plugin `kotlin("jvm")` em todos os subprojetos, ktlint e detekt (config em `config/detekt/detekt.yml`) aplicados a todos, `tasks.test { useJUnitPlatform() }`, e `check` dependendo de `ktlintCheck` e `detekt`
- [X] T004 Configurar `domain/build.gradle.kts`: apenas stdlib Kotlin em `main`; em `test`, JUnit 5 e AssertJ. Nenhuma outra dependência (R-02)
- [X] T005 Configurar `application/build.gradle.kts`: `api(project(":domain"))` em `main` (os value objects do domínio fazem parte do contrato dos casos de uso, R-02); em `test`, JUnit 5, AssertJ e MockK. Nenhuma dependência de framework
- [X] T006 Configurar `infrastructure/build.gradle.kts`: `implementation(project(":application"))`, plugins `kotlin("plugin.spring")` e `kotlin("plugin.jpa")`, starters `data-jpa`, `data-redis`, `amqp`, `mail`, `oauth2-resource-server` (para `NimbusJwtEncoder`), `spring-security-crypto`, Flyway, driver PostgreSQL, ShedLock, `jackson-module-kotlin`; em `test`, `spring-boot-starter-test`, Testcontainers, WireMock e MockK
- [X] T007 Configurar `api/build.gradle.kts`: `implementation(project(":application"))` (sem acesso a `infrastructure`), plugin `kotlin("plugin.spring")`, starters `web`, `validation`, `security`, `oauth2-resource-server`, `springdoc-openapi-starter-webmvc-ui`, `jackson-module-kotlin`; em `test`, `spring-boot-starter-test`, `spring-security-test` e `springmockk`
- [X] T008 Configurar `bootstrap/build.gradle.kts`: plugin `org.springframework.boot`, dependências de `:domain`, `:application`, `:infrastructure` e `:api`, starter `actuator`, `logstash-logback-encoder`; `bootJar` habilitado só aqui (nos demais módulos `jar` comum); em `test`, `spring-boot-starter-test`, `spring-security-test`, Testcontainers, WireMock e `springmockk`
- [X] T009 [P] Criar `.editorconfig` com as regras do ktlint (indentação 4, `max_line_length = 120`, `ktlint_code_style = ktlint_official`)
- [X] T010 [P] Criar `config/detekt/detekt.yml` com o baseline padrão do detekt, `maxIssues: 0` e regras de complexidade ativas
- [X] T011 [P] Criar `.gitignore` (`build/`, `.gradle/`, `.idea/`, `.env`) e `.env.example` com `JWT_SECRET` (≥ 32 bytes), `QUOTE_PROVIDER_URL=https://www.mercadobitcoin.net/api/BTC/ticker/`, `BREVO_API_KEY`, `DB_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`, `SMTP_HOST`, `SMTP_PORT`, `SPRING_PROFILES_ACTIVE` (sem valores reais)
- [X] T012 [P] Criar `Dockerfile` multi-stage: estágio `gradle:jdk21` executa `./gradlew :bootstrap:bootJar --no-daemon`; estágio final `eclipse-temurin:21-jre` com usuário não-root, `EXPOSE 8080` e `HEALTHCHECK` em `/actuator/health/readiness` (R-13)
- [X] T013 [P] Criar `docker-compose.yml` com `nginx` (porta 8080), `app` (`deploy.replicas: 2`, `env_file: .env`), `postgres:16`, `redis:7`, `rabbitmq:3-management` (15672), `mailpit` (8025), e `quote-stub` (WireMock, porta 8089, `profiles: [stub]`, volume `docker/quote-stub/mappings`), todos com healthcheck e `depends_on: condition: service_healthy`
- [X] T014 [P] Criar `docker/nginx/nginx.conf` com upstream round-robin para `app:8080`, repasse dos headers `Authorization` e `X-Correlation-Id` e `proxy_next_upstream error timeout`
- [X] T015 [P] Criar `docker/quote-stub/mappings/ticker.json` (mapping WireMock para `GET /api/BTC/ticker/`) respondendo o formato do Mercado Bitcoin v1 com `ticker.buy = "400000.00"`, `ticker.sell = "400000.00"` e `ticker.date` (quickstart §3)
- [X] T016 [P] Criar `.github/workflows/ci.yml`: JDK 21 (temurin), cache do Gradle, `./gradlew check` (Testcontainers usa o Docker do runner) e `docker build -t kotlinbitcoins:ci .`; falha bloqueia o merge (R-12)

**Checkpoint**: `./gradlew check` executa com sucesso nos 5 módulos vazios.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: value objects, erro como valor, conta, esquema do banco, transação, segurança JWT e tratamento de erros HTTP. Tudo o que as user stories compartilham.

**⚠️ CRITICAL**: nenhuma user story pode começar antes desta fase terminar.

### Testes de domínio (escrever primeiro, devem falhar)

- [X] T017 [P] Teste de `Outcome` em domain/src/test/kotlin/com/kotlinbitcoins/domain/shared/OutcomeTest.kt: `Success`/`Failure`, `map` e `flatMap` não executam em `Failure`, `fold` cobre os dois ramos
- [X] T018 [P] Teste de `Money` em domain/src/test/kotlin/com/kotlinbitcoins/domain/shared/MoneyTest.kt: `Money.of(BigDecimal("100.5"))` → `100.50` com escala 2; valor com mais de 2 casas significativas (`10.123`) → `Failure(InvalidAmount)`; valor negativo → `Failure(InvalidAmount)`; `Money.floorOf(BigDecimal("439.999"))` → `439.99` (arredondamento explícito `FLOOR`); `plus`, `minus`, `compareTo`, `Money.ZERO`; `minus` que resultaria negativo viola invariante (`require`)
- [X] T019 [P] Teste de `PositiveMoney` em domain/src/test/kotlin/com/kotlinbitcoins/domain/shared/PositiveMoneyTest.kt: `0`, `-5`, `10.123` → `Failure(InvalidAmount)`; `0.01` e `100.00` → `Success` (FR-008, FR-013, FR-018)
- [X] T020 [P] Teste de `BtcAmount` em domain/src/test/kotlin/com/kotlinbitcoins/domain/shared/BtcAmountTest.kt: satoshis `Long` ≥ 0; negativo rejeitado; `plus`/`minus`; `ZERO`; `toBtcDecimal()` de `100_000` sats → `BigDecimal("0.00100000")` (escala 8)
- [X] T021 [P] Teste de `QuotePrice` em domain/src/test/kotlin/com/kotlinbitcoins/domain/shared/QuotePriceTest.kt: aceita `"400000.00"` e `"400000.12345678"` preservando exatamente a escala recebida (FR-011); `0`, negativo e escala 9 → `Failure(InvalidQuotePrice)`
- [X] T022 [P] Teste de `Email` em domain/src/test/kotlin/com/kotlinbitcoins/domain/account/EmailTest.kt: `" ANA@Example.com "` → `"ana@example.com"` (FR-002); sem `@`, sem domínio, vazio ou com mais de 320 caracteres → `Failure(InvalidEmail)`
- [X] T023 [P] Teste de `Account` em domain/src/test/kotlin/com/kotlinbitcoins/domain/account/AccountTest.kt: `Account.open(id, name, email, passwordHash, createdAt)` cria saldo `0.00` (FR-001); nome vazio após `trim` ou com mais de 200 caracteres → `Failure(InvalidName)`

### Implementação de domínio

- [X] T024 [P] Implementar `Outcome<out E, out T>` (sealed `Success(value)` / `Failure(error)`, `map`, `flatMap`, `fold`) em domain/src/main/kotlin/com/kotlinbitcoins/domain/shared/Outcome.kt
- [X] T025 Implementar `Money` (`BigDecimal` escala 2, não negativo, `of`, `floorOf`, `ZERO`, `plus`, `minus`, `compareTo`) e o erro `data object InvalidAmount` em domain/src/main/kotlin/com/kotlinbitcoins/domain/shared/Money.kt
- [X] T026 Implementar `PositiveMoney` (`@JvmInline value class` sobre `Money`, `of(BigDecimal): Outcome<InvalidAmount, PositiveMoney>`) em domain/src/main/kotlin/com/kotlinbitcoins/domain/shared/PositiveMoney.kt
- [X] T027 [P] Implementar `BtcAmount` (`@JvmInline value class` com `sats: Long`, `SATS_PER_BTC = 100_000_000L`, `ZERO`, `plus`, `minus`, `toBtcDecimal()`) em domain/src/main/kotlin/com/kotlinbitcoins/domain/shared/BtcAmount.kt
- [X] T028 [P] Implementar `QuotePrice` (`BigDecimal` > 0, escala ≤ 8, nunca arredondado; `of(BigDecimal): Outcome<InvalidQuotePrice, QuotePrice>`) em domain/src/main/kotlin/com/kotlinbitcoins/domain/shared/QuotePrice.kt
- [X] T029 [P] Implementar `AccountId`, `InvestmentId`, `TransactionId`, `RedemptionRequestId` (`@JvmInline value class` sobre `UUID`, com `random()`) em domain/src/main/kotlin/com/kotlinbitcoins/domain/shared/Ids.kt
- [X] T030 [P] Implementar `Email` (normaliza com `trim` + `lowercase`, valida formato, máx. 320) e `InvalidEmail` em domain/src/main/kotlin/com/kotlinbitcoins/domain/account/Email.kt
- [X] T031 Implementar a entidade `Account` (`id`, `name` não vazio ≤ 200, `email`, `passwordHash`, `balance: Money` ≥ 0, `createdAt`; fábrica `open`) e `InvalidName` em domain/src/main/kotlin/com/kotlinbitcoins/domain/account/Account.kt

### Portas da aplicação

- [X] T032 [P] Criar a porta `Clock` (`fun now(): Instant`) em application/src/main/kotlin/com/kotlinbitcoins/application/port/Clock.kt
- [X] T033 [P] Criar `InfrastructureFailure` (data class com `reason: String`, sem stack trace) e a interface marcadora `InfrastructureUnavailable` (implementada pela variante `ServiceUnavailable` de cada erro de operação, já que subtipos de `sealed interface` precisam estar no mesmo pacote) em application/src/main/kotlin/com/kotlinbitcoins/application/port/InfrastructureFailure.kt e a porta `TransactionRunner` (`fun <E, T> run(onInfrastructureFailure: (InfrastructureFailure) -> E, block: () -> Outcome<E, T>): Outcome<E, T>`; contrato: `Failure` faz rollback sem exceção; falha de commit vira `Failure(onInfrastructureFailure(...))`) em application/src/main/kotlin/com/kotlinbitcoins/application/port/TransactionRunner.kt
- [X] T034 [P] Criar a porta `AccountRepository` (`save(Account): Outcome<SaveAccountError, Account>` com `SaveAccountError = EmailTaken | Infrastructure(InfrastructureFailure)`; `findById(AccountId)`, `findByIdForUpdate(AccountId)` e `findByEmail(Email)`: `Outcome<InfrastructureFailure, Account?>`; `existsByEmail(Email): Outcome<InfrastructureFailure, Boolean>`) em application/src/main/kotlin/com/kotlinbitcoins/application/port/AccountRepository.kt

### Testes de infraestrutura (escrever primeiro, devem falhar)

- [X] T035 Criar o suporte de integração em infrastructure/src/test/kotlin/com/kotlinbitcoins/infrastructure/support/PostgresIntegrationTest.kt e infrastructure/src/test/kotlin/com/kotlinbitcoins/infrastructure/support/InfrastructureTestApplication.kt: contêiner `postgres:16` singleton (Testcontainers), `@DynamicPropertySource`, Flyway habilitado, `spring.jpa.hibernate.ddl-auto=validate`, `@SpringBootApplication` de teste com `@EnableJpaRepositories`/`@EntityScan` em `com.kotlinbitcoins.infrastructure.persistence`
- [X] T036 [P] Teste de migrações em infrastructure/src/test/kotlin/com/kotlinbitcoins/infrastructure/persistence/FlywayMigrationsIT.kt: V1–V6 aplicam em banco vazio e o Hibernate valida; `UPDATE accounts SET balance = -0.01` falha (`CHECK (balance >= 0)`); `transactions` do tipo `DEPOSIT` com `btc_amount_sats` não nulo falha; `investments.btc_amount_sats = 0` falha; sequência `investments_fifo_seq` existe; `quote_history.slot_at` é PK
- [X] T037 [P] Teste do `JpaTransactionRunner` em infrastructure/src/test/kotlin/com/kotlinbitcoins/infrastructure/persistence/JpaTransactionRunnerIT.kt: bloco que grava uma conta e devolve `Failure` → nada persistido e nenhuma exceção lançada; `Success` → commit; falha no commit (ex.: violação de `CHECK`) → rollback e `Failure(onInfrastructureFailure(...))`, sem exceção fora do runner
- [X] T038 [P] Teste do adaptador de contas em infrastructure/src/test/kotlin/com/kotlinbitcoins/infrastructure/persistence/AccountRepositoryAdapterIT.kt: `save`/`findById`/`findByEmail`/`existsByEmail`; e-mail duplicado em `save` → `Failure(EmailTaken)` (violação de `UNIQUE` convertida, sem exceção); banco indisponível (contêiner pausado) → `Failure(InfrastructureFailure)` em todas as operações; saldo `NUMERIC(19,2)` faz ida e volta sem perder escala; `findByIdForUpdate` em duas threads: a segunda espera o commit da primeira (lock pessimista, R-05)

### Implementação de infraestrutura

- [X] T039 [P] Criar infrastructure/src/main/resources/db/migration/V1__create_accounts.sql: `id UUID PK`, `name VARCHAR(200) NOT NULL`, `email VARCHAR(320) NOT NULL UNIQUE`, `password_hash VARCHAR(100) NOT NULL`, `balance NUMERIC(19,2) NOT NULL CHECK (balance >= 0)`, `created_at TIMESTAMPTZ NOT NULL`
- [X] T040 [P] Criar infrastructure/src/main/resources/db/migration/V2__create_investments.sql: `CREATE SEQUENCE investments_fifo_seq`; tabela com `id UUID PK`, `account_id UUID NOT NULL FK → accounts`, `purchased_at TIMESTAMPTZ NOT NULL`, `fifo_sequence BIGINT NOT NULL`, `invested_amount NUMERIC(19,2) NOT NULL CHECK (>= 0)`, `purchase_quote NUMERIC(19,8) NOT NULL CHECK (> 0)`, `btc_amount_sats BIGINT NOT NULL CHECK (> 0)`, `status VARCHAR(16) NOT NULL CHECK IN ('ACTIVE','CLOSED')`, `origin VARCHAR(16) NOT NULL CHECK IN ('PURCHASE','REINVESTMENT')`, `origin_investment_id UUID NULL FK → investments`, `closed_at TIMESTAMPTZ NULL`; índice `ix_investments_active_fifo (account_id, purchased_at, fifo_sequence) WHERE status = 'ACTIVE'`
- [X] T041 [P] Criar infrastructure/src/main/resources/db/migration/V3__create_transactions.sql: `id UUID PK`, `account_id UUID NOT NULL FK → accounts`, `type VARCHAR(16) NOT NULL CHECK IN ('DEPOSIT','PURCHASE','REDEMPTION','REINVESTMENT')`, `amount NUMERIC(19,2) NOT NULL CHECK (amount > 0 OR type IN ('REDEMPTION','REINVESTMENT'))`, `btc_amount_sats BIGINT NULL`, `quote NUMERIC(19,8) NULL`, `investment_id UUID NULL FK → investments`, `request_id UUID NULL`, `occurred_at TIMESTAMPTZ NOT NULL`; `CHECK ((type = 'DEPOSIT') = (btc_amount_sats IS NULL AND quote IS NULL))`; índices `ix_transactions_statement (account_id, occurred_at DESC)` e `ix_transactions_volume (occurred_at, type) INCLUDE (btc_amount_sats) WHERE type IN ('PURCHASE','REDEMPTION')`
- [X] T042 [P] Criar infrastructure/src/main/resources/db/migration/V4__create_quote_history.sql: `slot_at TIMESTAMPTZ PK`, `buy_price NUMERIC(19,8) NOT NULL`, `sell_price NUMERIC(19,8) NOT NULL`, `captured_at TIMESTAMPTZ NOT NULL`
- [X] T043 [P] Criar infrastructure/src/main/resources/db/migration/V5__create_outbox_events.sql: `id UUID PK`, `type VARCHAR(32) NOT NULL`, `payload JSONB NOT NULL`, `created_at TIMESTAMPTZ NOT NULL`, `published_at TIMESTAMPTZ NULL`, `sent_at TIMESTAMPTZ NULL`; índice `ix_outbox_unpublished (created_at) WHERE published_at IS NULL`
- [X] T044 [P] Criar infrastructure/src/main/resources/db/migration/V6__create_shedlock.sql com o esquema padrão do ShedLock (`name VARCHAR(64) PK`, `lock_until TIMESTAMPTZ NOT NULL`, `locked_at TIMESTAMPTZ NOT NULL`, `locked_by VARCHAR(255) NOT NULL`)
- [X] T045 [P] Implementar `SystemClock` (única implementação de `Clock`, usa `java.time.Clock.systemUTC()`) em infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/time/SystemClock.kt
- [X] T046 Implementar `JpaTransactionRunner` com `TransactionTemplate`, chamando `status.setRollbackOnly()` quando o bloco devolve `Failure` e capturando `DataAccessException`/`TransactionException` para devolver `Failure(onInfrastructureFailure(InfrastructureFailure(...)))` em infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/persistence/JpaTransactionRunner.kt
- [X] T047 Implementar `AccountJpaEntity`, `SpringDataAccountRepository` (com `@Lock(PESSIMISTIC_WRITE)` em `findLockedById`) e `AccountMapper` em infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/persistence/account/AccountJpaEntity.kt, infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/persistence/account/SpringDataAccountRepository.kt e infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/persistence/account/AccountMapper.kt
- [X] T048 Implementar `AccountRepositoryAdapter` (porta `AccountRepository`; `findByIdForUpdate` → `SELECT ... FOR UPDATE`; captura `DataIntegrityViolationException` na constraint de e-mail → `Failure(EmailTaken)` e as demais `DataAccessException` → `Failure(InfrastructureFailure)`; nenhuma exceção sai do adaptador) em infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/persistence/account/AccountRepositoryAdapter.kt

### Testes da camada HTTP (escrever primeiro, devem falhar)

- [X] T049 [P] Teste do parsing estrito de valor em api/src/test/kotlin/com/kotlinbitcoins/api/format/MoneyInputParserTest.kt: aceita `"100"`, `"100.5"`, `"100.50"`; rejeita `null`, `""`, `"0"`, `"0.00"`, `"-5"`, `"abc"`, `"10.123"`, `"1e3"`, `"+10"`, `" 10"` → `InvalidAmount` (regex `^\d+(\.\d{1,2})?$`, R-10)
- [X] T050 [P] Teste de formatação em api/src/test/kotlin/com/kotlinbitcoins/api/format/ApiFormatsTest.kt: `Money` → `"100.50"`; `BtcAmount(100_000)` → `"0.00100000"`; `QuotePrice` mantém a escala (`"400000.00"`); `Instant.parse("2026-11-09T11:10:00Z")` → `"2026-11-09T08:10:00-03:00"` (FR-034a)
- [X] T051 [P] Teste do handler de erros em api/src/test/kotlin/com/kotlinbitcoins/api/error/ApiExceptionHandlerTest.kt (`@WebMvcTest` com controller de teste): JSON malformado → `400 VALIDATION_ERROR`; falha de Bean Validation → `400 VALIDATION_ERROR` com `errors[{field, message}]`; exceção inesperada → `500` sem stack trace, nome de classe ou mensagem interna; todo erro com `Content-Type: application/problem+json`, `code`, `detail` em português e `correlationId`
- [X] T052 [P] Teste do `CorrelationIdFilter` em api/src/test/kotlin/com/kotlinbitcoins/api/web/CorrelationIdFilterTest.kt: reaproveita `X-Correlation-Id` recebido, gera UUID quando ausente, devolve no header da resposta, coloca no MDC durante a requisição e limpa depois

### Implementação da camada HTTP

- [X] T053 Implementar `ProblemCode` (enum com os 10 códigos de R-03 e seus status: `SERVICE_UNAVAILABLE` 503, `VALIDATION_ERROR` 400, `INVALID_AMOUNT` 400, `INVALID_CREDENTIALS` 401, `UNAUTHORIZED` 401, `EMAIL_ALREADY_IN_USE` 409, `INSUFFICIENT_BALANCE` 422, `REDEMPTION_EXCEEDS_AVAILABLE` 422, `INVALID_DATE_RANGE` 422, `QUOTE_UNAVAILABLE` 503) e `ProblemDetailsMapper` (qualquer erro que implemente `InfrastructureUnavailable` → `SERVICE_UNAVAILABLE` 503, "Serviço temporariamente indisponível. Tente novamente em instantes."; monta `ProblemDetail` com `type=about:blank`, `title`, `status`, `code`, `detail` pt-BR, `correlationId` do MDC) em api/src/main/kotlin/com/kotlinbitcoins/api/error/ProblemCode.kt e api/src/main/kotlin/com/kotlinbitcoins/api/error/ProblemDetailsMapper.kt
- [X] T054 Implementar `ApiExceptionHandler` (`@RestControllerAdvice` só para erros de framework: `HttpMessageNotReadableException`, `MethodArgumentNotValidException` → `VALIDATION_ERROR`; qualquer outra exceção → 500 genérico com log do erro) em api/src/main/kotlin/com/kotlinbitcoins/api/error/ApiExceptionHandler.kt
- [X] T055 [P] Implementar `MoneyInputParser` (`parse(String?): Outcome<InvalidAmount, PositiveMoney>`, regex estrita, sem notação científica) em api/src/main/kotlin/com/kotlinbitcoins/api/format/MoneyInputParser.kt
- [X] T056 [P] Implementar `ApiFormats` (formatadores de `Money`, `BtcAmount`, `QuotePrice` e `Instant` → ISO-8601 com offset de `America/Sao_Paulo`) em api/src/main/kotlin/com/kotlinbitcoins/api/format/ApiFormats.kt
- [X] T057 [P] Implementar `CorrelationIdFilter` (`OncePerRequestFilter`, header `X-Correlation-Id`, chave MDC `correlationId`) em api/src/main/kotlin/com/kotlinbitcoins/api/web/CorrelationIdFilter.kt
- [X] T058 [P] Implementar `@CurrentAccount` e `CurrentAccountArgumentResolver` (extrai `AccountId` do claim `sub` do `Jwt` autenticado) em api/src/main/kotlin/com/kotlinbitcoins/api/web/CurrentAccount.kt

### Bootstrap

- [X] T059 Criar `KotlinBitcoinsApplication` (`@SpringBootApplication(scanBasePackages = ["com.kotlinbitcoins.api", "com.kotlinbitcoins.bootstrap"])`, `@EnableJpaRepositories` e `@EntityScan` em `com.kotlinbitcoins.infrastructure.persistence`) em bootstrap/src/main/kotlin/com/kotlinbitcoins/bootstrap/KotlinBitcoinsApplication.kt
- [X] T060 Criar bootstrap/src/main/resources/application.yml (datasource, Redis, RabbitMQ e SMTP por variáveis de ambiente; `spring.jpa.hibernate.ddl-auto=validate`; `spring.jpa.open-in-view=false`; `app.jwt.secret=${JWT_SECRET}`; `app.jwt.ttl=1h`; `app.business-zone=America/Sao_Paulo`; actuator expondo só `health` com probes `readiness`/`liveness`; no `application-test.yml`, `spring.datasource.hikari.connection-timeout=2000` e `validation-timeout=1000`, para que os testes com o banco pausado (T038, T220) falhem em segundos) e os perfis bootstrap/src/main/resources/application-dev.yml, bootstrap/src/main/resources/application-test.yml e bootstrap/src/main/resources/application-prod.yml
- [X] T061 [P] Criar bootstrap/src/main/resources/logback-spring.xml com `LogstashEncoder` em stdout, `correlationId` do MDC e mascaramento de `password`, `token`, `accessToken` e `Authorization` (R-11, FR-007)
- [X] T062 Criar o suporte de testes de aplicação montada em bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/support/ApplicationTestBase.kt, bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/support/MutableClock.kt e bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/support/JwtTestTokens.kt: `@SpringBootTest` + `MockMvc`, contêineres singleton `postgres:16`, `redis:7` e `rabbitmq:3` com `@DynamicPropertySource`, servidor WireMock (porta dinâmica) para a fonte de cotação com `quote.provider.url` apontado para ele, limpeza das tabelas entre testes, bean `@Primary` `MutableClock` (implementa `Clock`, permite `set`/`advance`), e `JwtTestTokens` que emite tokens válidos, expirados, adulterados (assinatura trocada) e malformados com o mesmo `JWT_SECRET` de teste
- [X] T063 Teste de segurança em bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/contract/SecurityContractTest.kt: `GET /api/v1/me/balance` sem token, com `Bearer abc`, com token adulterado e com token expirado → `401` com `code=UNAUTHORIZED` em `application/problem+json`; `POST /api/v1/accounts` e `POST /api/v1/auth/login` não exigem token; `GET /actuator/health` é público (FR-006)
- [X] T064 Implementar `SecurityConfiguration` (stateless, CSRF desligado, OAuth2 Resource Server com `NimbusJwtDecoder.withSecretKey` HS256 sobre `JWT_SECRET`, rotas públicas `POST /api/v1/accounts`, `POST /api/v1/auth/login`, `/actuator/health/**`, `/v3/api-docs/**`, `/swagger-ui/**`; `AuthenticationEntryPoint` respondendo Problem Details `UNAUTHORIZED` via `ProblemDetailsMapper`; registro do `CurrentAccountArgumentResolver`) em bootstrap/src/main/kotlin/com/kotlinbitcoins/bootstrap/SecurityConfiguration.kt
- [X] T065 Criar bootstrap/src/main/kotlin/com/kotlinbitcoins/bootstrap/AdapterConfiguration.kt (beans `Clock` → `SystemClock`, `TransactionRunner` → `JpaTransactionRunner`, `AccountRepository` → `AccountRepositoryAdapter`, `ZoneId` de negócio a partir de `app.business-zone`) e bootstrap/src/main/kotlin/com/kotlinbitcoins/bootstrap/UseCaseConfiguration.kt vazio, onde cada story vai registrar seus casos de uso com `@Bean`

**Checkpoint**: `./gradlew check` verde com domínio base, migrações, transação, segurança e tratamento de erros. As user stories podem começar.

---

## Phase 3: User Story 1 - Cadastro e autenticação (Priority: P1) 🎯 MVP

**Goal**: a pessoa cria conta (nome, e-mail, senha), faz login e recebe um JWT que passa a apresentar nas rotas protegidas.

**Independent Test**: cadastrar, fazer login, usar o token numa rota protegida e confirmar que a mesma rota sem token, com token malformado, adulterado ou expirado responde `401 UNAUTHORIZED` (quickstart passos 1–5).

### Tests for User Story 1 ⚠️

> Escrever primeiro e confirmar que FALHAM.

- [X] T066 [P] [US1] Teste do caso de uso `RegisterAccount` (MockK nas portas) em application/src/test/kotlin/com/kotlinbitcoins/application/account/RegisterAccountTest.kt: sucesso cria conta com saldo `0.00`, normaliza o e-mail e grava só o hash (`PasswordHasher.hash` chamado); e-mail já existente (`existsByEmail` true, inclusive com maiúsculas/espaços) → `Failure(EmailAlreadyInUse)` sem `save`; `save` devolvendo `EmailTaken` (cadastro concorrente) → `Failure(EmailAlreadyInUse)`; nome vazio, e-mail malformado, senha com menos de 8 caracteres ou com mais de 72 bytes UTF-8 (ex.: 37 caracteres `é`) → `Failure(InvalidInput(fields))` listando todos os campos inválidos (FR-001..FR-003). Falha de infraestrutura (porta devolve `Failure(InfrastructureFailure)`) → `Failure(ServiceUnavailable)`, sem gravação parcial nem evento
- [X] T067 [P] [US1] Teste do caso de uso `AuthenticateAccount` em application/src/test/kotlin/com/kotlinbitcoins/application/account/AuthenticateAccountTest.kt: credenciais corretas → `Success(AccessToken)` emitido por `TokenIssuer`; e-mail inexistente → `Failure(InvalidCredentials)` **e** `PasswordHasher.matches` é chamado contra um hash fictício (equalização de tempo, R-09); senha errada → o mesmo `Failure(InvalidCredentials)`; login com `" ANA@example.com "` encontra a conta; senha com mais de 72 bytes → `Failure(InvalidCredentials)` sem passar essa senha ao `matches` (ainda compara contra o hash fictício) (FR-002, FR-004, FR-005). Falha de infraestrutura (porta devolve `Failure(InfrastructureFailure)`) → `Failure(ServiceUnavailable)`, sem gravação parcial nem evento
- [X] T068 [P] [US1] Teste do `BCryptPasswordHasher` em infrastructure/src/test/kotlin/com/kotlinbitcoins/infrastructure/security/BCryptPasswordHasherTest.kt: hash com prefixo `$2a$12$` (custo 12), `matches` verdadeiro para a senha e falso para outra, hash nunca igual à senha
- [X] T069 [P] [US1] Teste do `JwtTokenIssuer` em infrastructure/src/test/kotlin/com/kotlinbitcoins/infrastructure/security/JwtTokenIssuerTest.kt: token HS256 decodificável com o mesmo segredo, `sub` = `AccountId`, `exp` = `Clock.now()` + 1 h, `expiresIn = 3600`
- [X] T070 [P] [US1] Teste do `AccountController` (`@WebMvcTest` + `springmockk`) em api/src/test/kotlin/com/kotlinbitcoins/api/account/AccountControllerTest.kt: `POST /api/v1/accounts` → `201` com `id`, `name`, `email`, `balance: "0.00"`, `createdAt` e **sem** campo de senha; `InvalidInput` (inclusive senha com mais de 72 bytes) → `400 VALIDATION_ERROR` com `errors[]`; `EmailAlreadyInUse` → `409 EMAIL_ALREADY_IN_USE`
- [X] T071 [P] [US1] Teste do `AuthController` em api/src/test/kotlin/com/kotlinbitcoins/api/account/AuthControllerTest.kt: `POST /api/v1/auth/login` → `200` com `accessToken`, `tokenType: "Bearer"`, `expiresIn: 3600`; `InvalidCredentials` → `401 INVALID_CREDENTIALS`; corpo ausente → `400 VALIDATION_ERROR`
- [X] T072 [P] [US1] Teste de contrato em bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/contract/AccountsContractTest.kt cobrindo US1 cenários 1–5: cadastro `201` sem senha; repetir com `"ANA@example.com "` → `409`; campos inválidos → `400` com os campos; login correto → `200`; senha errada e e-mail inexistente → `401 INVALID_CREDENTIALS` com corpos **idênticos** (exceto `correlationId`); a senha não aparece em nenhuma resposta e `accounts.password_hash` começa com `$2a$12$`

### Implementation for User Story 1

- [X] T073 [P] [US1] Criar `RegisterAccountError` (`InvalidInput(fields: List<FieldError>)`, `EmailAlreadyInUse`, `ServiceUnavailable`) e `LoginError` (`InvalidCredentials`, `ServiceUnavailable`) como `sealed interface` em application/src/main/kotlin/com/kotlinbitcoins/application/account/RegisterAccountError.kt e application/src/main/kotlin/com/kotlinbitcoins/application/account/LoginError.kt
- [X] T074 [P] [US1] Criar a porta `PasswordHasher` (`hash(raw: String): String`, `matches(raw: String, hash: String): Boolean`) em application/src/main/kotlin/com/kotlinbitcoins/application/port/PasswordHasher.kt
- [X] T075 [P] [US1] Criar a porta `TokenIssuer` (`issue(AccountId): AccessToken`) e `AccessToken(value: String, expiresInSeconds: Long)` em application/src/main/kotlin/com/kotlinbitcoins/application/port/TokenIssuer.kt
- [X] T076 [US1] Implementar `RegisterAccount` (valida nome/e-mail/senha ≥ 8 caracteres e ≤ 72 bytes UTF-8 acumulando os campos inválidos, checa unicidade, faz hash, `Account.open`, `save`; `EmailTaken` no `save` → `EmailAlreadyInUse`; nunca devolve o hash) em application/src/main/kotlin/com/kotlinbitcoins/application/account/RegisterAccount.kt
- [X] T077 [US1] Implementar `AuthenticateAccount` (normaliza e-mail, `findByEmail`, compara contra hash fictício quando não existe ou quando a senha passa de 72 bytes, mesmo erro em todos os casos, emite token) em application/src/main/kotlin/com/kotlinbitcoins/application/account/AuthenticateAccount.kt
- [X] T078 [P] [US1] Implementar `BCryptPasswordHasher` (`BCryptPasswordEncoder(12)`) em infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/security/BCryptPasswordHasher.kt
- [X] T079 [P] [US1] Implementar `JwtTokenIssuer` (`NimbusJwtEncoder` HS256 com `JWT_SECRET`, `sub`, `iat`, `exp` a partir da porta `Clock`, TTL de `app.jwt.ttl`) em infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/security/JwtTokenIssuer.kt
- [X] T080 [P] [US1] Criar os DTOs `RegisterAccountRequest` (`@NotBlank @Size(max = 200) name`, `@NotBlank @Size(max = 320) email` (o formato é validado só pelo `Email` do domínio, para que a regra seja única), `@NotBlank @Size(min = 8) password`), `AccountResponse`, `LoginRequest` e `LoginResponse` conforme `contracts/openapi.yaml` em api/src/main/kotlin/com/kotlinbitcoins/api/account/AccountDtos.kt
- [X] T081 [US1] Implementar `AccountController` (`POST /api/v1/accounts` → `201`) em api/src/main/kotlin/com/kotlinbitcoins/api/account/AccountController.kt
- [X] T082 [US1] Implementar `AuthController` (`POST /api/v1/auth/login` → `200`) em api/src/main/kotlin/com/kotlinbitcoins/api/account/AuthController.kt
- [X] T083 [US1] Adicionar ao api/src/main/kotlin/com/kotlinbitcoins/api/error/ProblemDetailsMapper.kt o mapeamento exaustivo (`when`) de `RegisterAccountError` (`InvalidInput` → `VALIDATION_ERROR` com `errors[]`, `EmailAlreadyInUse` → `EMAIL_ALREADY_IN_USE`) e `LoginError` (`InvalidCredentials` → `INVALID_CREDENTIALS`, `detail` "E-mail ou senha inválidos.")
- [X] T084 [US1] Registrar beans de `RegisterAccount` e `AuthenticateAccount` em bootstrap/src/main/kotlin/com/kotlinbitcoins/bootstrap/UseCaseConfiguration.kt e de `BCryptPasswordHasher` e `JwtTokenIssuer` em bootstrap/src/main/kotlin/com/kotlinbitcoins/bootstrap/AdapterConfiguration.kt

**Checkpoint**: US1 completa e testável sozinha. Contas e tokens funcionam.

---

## Phase 4: User Story 2 - Depósito em reais (Priority: P1)

**Goal**: o cliente deposita um valor positivo em R$, creditado na hora, registrado no extrato e notificado por e-mail (Transactional Outbox → RabbitMQ → EmailSender).

**Independent Test**: depositar R$ 100,00 numa conta nova e verificar saldo `100.00`, linha `DEPOSIT` em `transactions` e e-mail enviado com o valor; valores inválidos são recusados sem efeito (quickstart passos 6–7).

### Tests for User Story 2 ⚠️

- [X] T085 [P] [US2] Estender domain/src/test/kotlin/com/kotlinbitcoins/domain/account/AccountTest.kt: `deposit(PositiveMoney("100.00"))` sobre saldo `50.00` → saldo `150.00`
- [X] T086 [P] [US2] Teste de `Transaction` em domain/src/test/kotlin/com/kotlinbitcoins/domain/ledger/TransactionTest.kt: `Transaction.deposit(accountId, amount, occurredAt)` cria tipo `DEPOSIT` com `btcAmount`, `quote`, `investmentId` e `requestId` nulos
- [X] T087 [P] [US2] Teste do caso de uso `DepositFunds` em application/src/test/kotlin/com/kotlinbitcoins/application/account/DepositFundsTest.kt: tudo roda dentro de `TransactionRunner.run`; usa `findByIdForUpdate`; credita o saldo; grava uma `Transaction` `DEPOSIT` com `Clock.now()`; publica `NotificationEvent` `DEPOSIT_CONFIRMED` com `amount`, e-mail e nome do destinatário; conta inexistente → `Failure(AccountNotFound)` sem gravação. Falha de infraestrutura (porta devolve `Failure(InfrastructureFailure)`) → `Failure(ServiceUnavailable)`, sem gravação parcial nem evento
- [X] T088 [P] [US2] Teste do caso de uso `SendEmailNotification` em application/src/test/kotlin/com/kotlinbitcoins/application/notification/SendEmailNotificationTest.kt: `DEPOSIT_CONFIRMED` vira `EmailMessage` com `EmailContent.DepositConfirmed(amount)`; falha do `EmailSender` → `Failure(EmailSendError)` sem lançar exceção (FR-031)
- [X] T089 [P] [US2] Teste do adaptador de transações em infrastructure/src/test/kotlin/com/kotlinbitcoins/infrastructure/persistence/TransactionRepositoryAdapterIT.kt: `saveAll` de uma linha `DEPOSIT` persiste valor `NUMERIC(19,2)` e `occurred_at` em UTC
- [X] T090 [P] [US2] Teste do outbox em infrastructure/src/test/kotlin/com/kotlinbitcoins/infrastructure/messaging/OutboxNotificationPublisherIT.kt: o evento é gravado em `outbox_events` na mesma transação; se o `TransactionRunner` devolver `Failure` depois do `publish`, o evento some junto (rollback); o `payload` JSONB não contém senha nem token
- [X] T091 [P] [US2] Teste do relay em infrastructure/src/test/kotlin/com/kotlinbitcoins/infrastructure/messaging/OutboxRelayJobIT.kt (Testcontainers PostgreSQL + RabbitMQ): eventos com `published_at IS NULL` são publicados no exchange `notifications` com routing key `email` (publisher confirms) e marcados com `published_at`; duas execuções concorrentes do relay não publicam o mesmo evento duas vezes (`FOR UPDATE SKIP LOCKED`)
- [X] T092 [P] [US2] Teste do consumidor em infrastructure/src/test/kotlin/com/kotlinbitcoins/infrastructure/messaging/EmailNotificationListenerIT.kt (RabbitMQ): mensagem consumida da fila `notifications.email` chama `SendEmailNotification` e marca `outbox_events.sent_at`; mensagem duplicada de evento com `sent_at` preenchido não reenvia; `EmailSender` falhando 5 vezes: a mensagem passa pela fila `notifications.email.retry` com `x-attempt` de 1 a 5 e intervalos de 1, 2, 4, 8 e 16 s, termina em `notifications.email.dlq` e cada falha registra log com `eventId`; o listener nunca lança exceção
- [X] T093 [P] [US2] Testes dos remetentes em infrastructure/src/test/kotlin/com/kotlinbitcoins/infrastructure/email/BrevoEmailSenderTest.kt (WireMock: `POST /v3/smtp/email` com header `api-key`, corpo com destinatário e texto pt-BR; respostas 4xx/5xx e timeout → `Failure(EmailSendError)`) e infrastructure/src/test/kotlin/com/kotlinbitcoins/infrastructure/email/SmtpEmailSenderIT.kt (contêiner `axllent/mailpit` via `GenericContainer`; e-mail chega com assunto e corpo em português contendo "R$ 100,00")
- [X] T094 [P] [US2] Teste do `DepositController` em api/src/test/kotlin/com/kotlinbitcoins/api/account/DepositControllerTest.kt: `POST /api/v1/me/deposits` `{"amount":"100.00"}` → `201` com `transactionId`, `amount`, `balance`, `occurredAt` com offset `-03:00`; `"0"`, `"-5"`, `"abc"`, `"10.123"`, ausente → `400 INVALID_AMOUNT` sem chamar o caso de uso
- [X] T095 [P] [US2] Teste de contrato em bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/contract/DepositsContractTest.kt cobrindo US2 cenários 1–4, com o `RecordingEmailSender` (`@Primary`, criado nesta tarefa em bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/support/RecordingEmailSender.kt) que registra as mensagens: saldo `50.00` + `100.00` → `150.00`; linha `DEPOSIT` gravada; e-mail com o valor recebido em até 10 s; valores inválidos não mudam o saldo; com o `RecordingEmailSender` configurado para falhar, o depósito ainda responde `201` e o saldo é creditado (FR-031)

### Implementation for User Story 2

- [X] T096 [P] [US2] Implementar `TransactionType` (`DEPOSIT`, `PURCHASE`, `REDEMPTION`, `REINVESTMENT`) e a entidade imutável `Transaction` (campos de data-model §2; fábrica `deposit`) em domain/src/main/kotlin/com/kotlinbitcoins/domain/ledger/TransactionType.kt e domain/src/main/kotlin/com/kotlinbitcoins/domain/ledger/Transaction.kt
- [X] T097 [US2] Adicionar `deposit(PositiveMoney): Account` à entidade em domain/src/main/kotlin/com/kotlinbitcoins/domain/account/Account.kt
- [X] T098 [P] [US2] Criar a porta `TransactionRepository` com `saveAll(List<Transaction>): Outcome<InfrastructureFailure, Unit>` em application/src/main/kotlin/com/kotlinbitcoins/application/port/TransactionRepository.kt
- [X] T099 [P] [US2] Criar `NotificationEvent` (`id: UUID`, `type` `DEPOSIT_CONFIRMED`, `accountId`, `recipientEmail`, `recipientName`, `amount: Money`, `createdAt`) e a porta `NotificationPublisher` (`publish(NotificationEvent): Outcome<InfrastructureFailure, Unit>`) em application/src/main/kotlin/com/kotlinbitcoins/application/notification/NotificationEvent.kt e application/src/main/kotlin/com/kotlinbitcoins/application/port/NotificationPublisher.kt
- [X] T100 [P] [US2] Criar a porta `EmailSender` (`send(EmailMessage): Outcome<EmailSendError, Unit>`), `EmailMessage(recipientEmail, recipientName, content)` e `sealed interface EmailContent` com `DepositConfirmed(amount: Money)` em application/src/main/kotlin/com/kotlinbitcoins/application/port/EmailSender.kt
- [X] T101 [US2] Implementar `DepositError` (`AccountNotFound`, `ServiceUnavailable`) e o caso de uso `DepositFunds` (lock da conta, crédito, `Transaction.deposit`, `NotificationPublisher.publish`, tudo em `TransactionRunner.run`) em application/src/main/kotlin/com/kotlinbitcoins/application/account/DepositError.kt e application/src/main/kotlin/com/kotlinbitcoins/application/account/DepositFunds.kt
- [X] T102 [US2] Implementar `SendEmailNotification` (converte `NotificationEvent` em `EmailMessage` e chama `EmailSender`, devolvendo o `Outcome`) em application/src/main/kotlin/com/kotlinbitcoins/application/notification/SendEmailNotification.kt
- [X] T103 [P] [US2] Implementar `TransactionJpaEntity`, `SpringDataTransactionRepository`, `TransactionMapper` e `TransactionRepositoryAdapter` em infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/persistence/ledger/TransactionJpaEntity.kt, infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/persistence/ledger/SpringDataTransactionRepository.kt, infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/persistence/ledger/TransactionMapper.kt e infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/persistence/ledger/TransactionRepositoryAdapter.kt
- [X] T104 [P] [US2] Implementar `OutboxEventJpaEntity`, `SpringDataOutboxEventRepository` (native query `SELECT ... WHERE published_at IS NULL ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 100`, `markPublished`, `markSentIfNotSent` = `UPDATE ... SET sent_at = ? WHERE id = ? AND sent_at IS NULL`) e `OutboxNotificationPublisher` (serializa o payload com Jackson) em infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/messaging/OutboxEventJpaEntity.kt, infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/messaging/SpringDataOutboxEventRepository.kt e infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/messaging/OutboxNotificationPublisher.kt
- [X] T105 [US2] Implementar `RabbitConfig` (exchange `notifications`, fila `notifications.email`, fila `notifications.email.retry` sem consumidor com `x-dead-letter-exchange=notifications` e `x-dead-letter-routing-key=email`, fila `notifications.email.dlq`, publisher confirms, container do listener com `AcknowledgeMode.MANUAL` e sem `RetryInterceptor`) em infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/messaging/RabbitConfig.kt
- [X] T106 [US2] Implementar `OutboxRelayJob` (`@Scheduled(fixedDelay = 2000)` em todas as réplicas, sem ShedLock; publica com confirms e marca `published_at` na mesma transação) em infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/messaging/OutboxRelayJob.kt
- [X] T107 [US2] Implementar `EmailNotificationListener` (`@RabbitListener` em `notifications.email`; ignora evento com `sent_at` preenchido; chama `SendEmailNotification`; em sucesso, `markSentIfNotSent` + `basicAck`; em `Failure`, republica em `notifications.email.retry` com `expiration = 2^(tentativa−1) s` e `x-attempt + 1` (na 5ª falha, em `notifications.email.dlq`) e dá `basicAck`; erro de I/O no canal é capturado no adaptador e vira `basicNack(requeue = true)`; nenhuma exceção sai do listener) em infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/messaging/EmailNotificationListener.kt
- [X] T108 [P] [US2] Implementar `EmailTemplates` (assunto e corpo pt-BR por `EmailContent`, valores formatados como `R$ 1.000,00`; começa com `DepositConfirmed`) em infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/email/EmailTemplates.kt
- [X] T109 [P] [US2] Implementar `SmtpEmailSender` (`JavaMailSender`, perfis `dev`/`test`, Mailpit) em infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/email/SmtpEmailSender.kt
- [X] T110 [P] [US2] Implementar `BrevoEmailSender` (`RestClient` → `POST https://api.brevo.com/v3/smtp/email`, header `api-key: ${BREVO_API_KEY}`, perfil `prod`; exceções convertidas em `EmailSendError`) em infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/email/BrevoEmailSender.kt
- [X] T111 [P] [US2] Criar `DepositResponse` e o `DepositController` (`POST /api/v1/me/deposits` → `201`; usa `MoneyInputParser` e `@CurrentAccount`) em api/src/main/kotlin/com/kotlinbitcoins/api/account/DepositController.kt e api/src/main/kotlin/com/kotlinbitcoins/api/account/DepositDtos.kt
- [X] T112 [US2] Adicionar ao api/src/main/kotlin/com/kotlinbitcoins/api/error/ProblemDetailsMapper.kt o mapeamento de `InvalidAmount` → `INVALID_AMOUNT` ("O valor deve ser positivo e ter no máximo 2 casas decimais.") e de `DepositError.AccountNotFound` → `UNAUTHORIZED`
- [X] T113 [US2] Registrar `DepositFunds` e `SendEmailNotification` em bootstrap/src/main/kotlin/com/kotlinbitcoins/bootstrap/UseCaseConfiguration.kt e `TransactionRepositoryAdapter`, `OutboxNotificationPublisher` e o `EmailSender` por perfil (`prod` → Brevo; `dev`/`test` → SMTP) em bootstrap/src/main/kotlin/com/kotlinbitcoins/bootstrap/AdapterConfiguration.kt; habilitar `@EnableScheduling` e `@EnableRabbit`

**Checkpoint**: US1 e US2 funcionam de forma independente; o pipeline de e-mail está pronto para compra e resgate.

---

## Phase 5: User Story 3 - Consulta de saldo (Priority: P1)

**Goal**: o cliente autenticado consulta o saldo em R$ da própria conta.

**Independent Test**: conta nova → `GET /me/balance` = `"0.00"`; depositar e consultar de novo. Também fecha o teste independente da US1 (token válido funcionando numa rota protegida).

### Tests for User Story 3 ⚠️

- [X] T114 [P] [US3] Teste do caso de uso `GetBalance` em application/src/test/kotlin/com/kotlinbitcoins/application/account/GetBalanceTest.kt: devolve `account.balance`; conta inexistente → `Failure(AccountNotFound)`. Falha de infraestrutura (porta devolve `Failure(InfrastructureFailure)`) → `Failure(ServiceUnavailable)`, sem gravação parcial nem evento
- [X] T115 [P] [US3] Teste do `BalanceController` em api/src/test/kotlin/com/kotlinbitcoins/api/account/BalanceControllerTest.kt: `GET /api/v1/me/balance` → `200 {"balance":"0.00"}`
- [X] T116 [P] [US3] Teste de contrato em bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/contract/BalanceContractTest.kt: cadastrar, fazer login pelo endpoint real, `GET /me/balance` com o token → `"0.00"`; depois de depósitos de `100.00` e `50.50` → `"150.50"` (US3 cenários 1–2 no que depende só de depósitos)

### Implementation for User Story 3

- [X] T117 [US3] Implementar `GetBalanceError` (`AccountNotFound`, `ServiceUnavailable`) e o caso de uso `GetBalance` em application/src/main/kotlin/com/kotlinbitcoins/application/account/GetBalance.kt
- [X] T118 [US3] Implementar `BalanceResponse` e o `BalanceController` (`GET /api/v1/me/balance`) em api/src/main/kotlin/com/kotlinbitcoins/api/account/BalanceController.kt
- [X] T119 [US3] Mapear `GetBalanceError.AccountNotFound` → `UNAUTHORIZED` em api/src/main/kotlin/com/kotlinbitcoins/api/error/ProblemDetailsMapper.kt e registrar `GetBalance` em bootstrap/src/main/kotlin/com/kotlinbitcoins/bootstrap/UseCaseConfiguration.kt

**Checkpoint**: US1–US3 completas.

---

## Phase 6: User Story 4 - Cotação atual (Priority: P1)

**Goal**: o cliente consulta preço de compra, preço de venda e o instante real da cotação, com cache compartilhado de 10 s e fallback de até 60 s quando a fonte falha.

**Independent Test**: com WireMock simulando o Mercado Bitcoin, `GET /quotes/current` devolve `buyPrice`, `sellPrice` e `quotedAt`; com a fonte fora, devolve a última cotação se tiver ≤ 60 s e `503 QUOTE_UNAVAILABLE` depois disso (quickstart passos 8 e 19).

### Tests for User Story 4 ⚠️

- [X] T120 [P] [US4] Teste de `Quote` em domain/src/test/kotlin/com/kotlinbitcoins/domain/quote/QuoteTest.kt: guarda `buyPrice`, `sellPrice`, `quotedAt`; `ageAt(now)` devolve a `Duration` desde `quotedAt`
- [X] T121 [P] [US4] Teste do `QuoteService` (MockK em `QuoteProvider`, `QuoteCache`, `Clock`) em application/src/test/kotlin/com/kotlinbitcoins/application/quote/QuoteServiceTest.kt: cache com idade < 10 s → usa sem chamar a fonte; cache com 10 s ou mais → chama a fonte e, em sucesso, grava no cache e devolve a nova; fonte falha e cache com idade 30 s → devolve o cache com o `quotedAt` original (FR-012a); fonte falha e cache com exatamente 60 s → devolve o cache; com 60 s + 1 ms ou sem cache → `Failure(QuoteUnavailable)`
- [X] T122 [P] [US4] Teste do caso de uso `GetCurrentQuote` em application/src/test/kotlin/com/kotlinbitcoins/application/quote/GetCurrentQuoteTest.kt: delega ao `QuoteService` e propaga `QuoteUnavailable`
- [X] T123 [P] [US4] Teste do `MercadoBitcoinQuoteProvider` (WireMock) em infrastructure/src/test/kotlin/com/kotlinbitcoins/infrastructure/quote/MercadoBitcoinQuoteProviderIT.kt: `ticker.sell` → `sellPrice` e `ticker.buy` → `buyPrice` lidos como `BigDecimal` a partir da string, sem passar por `double` e com a escala original; `quotedAt` = `Clock.now()` na recepção (ignora `ticker.date`); HTTP 521, 500, timeout de leitura (> 2 s), JSON inválido e preço zero → `Failure(QuoteProviderError)` sem exceção
- [X] T124 [P] [US4] Teste do `RedisQuoteCache` (contêiner `redis:7`) em infrastructure/src/test/kotlin/com/kotlinbitcoins/infrastructure/quote/RedisQuoteCacheIT.kt: `put`/`get` preserva preços com a escala exata e o `quotedAt`; a chave expira com TTL de 60 s; `get` sem chave → `null`
- [X] T125 [P] [US4] Teste do `QuoteController` em api/src/test/kotlin/com/kotlinbitcoins/api/quote/QuoteControllerTest.kt: `GET /api/v1/quotes/current` → `200` com `buyPrice`, `sellPrice` e `quotedAt` com offset `-03:00`; `QuoteUnavailable` → `503 QUOTE_UNAVAILABLE`
- [X] T126 [P] [US4] Teste de contrato em bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/contract/QuotesContractTest.kt (WireMock como fonte + `MutableClock`), cobrindo US4 cenários 1–3: fonte ok → `200`; parar o stub e avançar o relógio 30 s → `200` com o `quotedAt` antigo; avançar até 61 s → `503 QUOTE_UNAVAILABLE`; sem nenhuma cotação anterior → `503`. Usar o WireMock da fonte já registrado no `ApplicationTestBase` (T062)

### Implementation for User Story 4

- [X] T127 [US4] Implementar `Quote` (`buyPrice: QuotePrice`, `sellPrice: QuotePrice`, `quotedAt: Instant`, `ageAt(now)`) em domain/src/main/kotlin/com/kotlinbitcoins/domain/quote/Quote.kt
- [X] T128 [P] [US4] Criar as portas `QuoteProvider` (`fetchCurrent(): Outcome<QuoteProviderError, Quote>`) e `QuoteCache` (`get(): Quote?`, `put(Quote)`) e o erro `QuoteError.QuoteUnavailable` em application/src/main/kotlin/com/kotlinbitcoins/application/port/QuoteProvider.kt, application/src/main/kotlin/com/kotlinbitcoins/application/port/QuoteCache.kt e application/src/main/kotlin/com/kotlinbitcoins/application/quote/QuoteError.kt
- [X] T129 [US4] Implementar `QuoteService` (`currentQuote(): Outcome<QuoteUnavailable, Quote>` com reuso < 10 s, consulta à fonte, fallback ≤ 60 s; uma única chamada por operação) em application/src/main/kotlin/com/kotlinbitcoins/application/quote/QuoteService.kt
- [X] T130 [US4] Implementar o caso de uso `GetCurrentQuote` em application/src/main/kotlin/com/kotlinbitcoins/application/quote/GetCurrentQuote.kt
- [X] T131 [P] [US4] Implementar `QuoteProviderProperties` (`quote.provider.url`, `connect-timeout=1s`, `read-timeout=2s`) e `MercadoBitcoinQuoteProvider` (`RestClient`; toda exceção, status ≠ 2xx ou JSON inválido vira `QuoteProviderError`) em infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/quote/QuoteProviderProperties.kt e infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/quote/MercadoBitcoinQuoteProvider.kt
- [X] T132 [P] [US4] Implementar `RedisQuoteCache` (chave `quote:current`, valor JSON com preços como string e `quotedAt` ISO, TTL 60 s; falha do Redis → `get` devolve `null`, `put` só registra log) em infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/quote/RedisQuoteCache.kt
- [X] T133 [P] [US4] Implementar `QuoteResponse` e o `QuoteController` (`GET /api/v1/quotes/current`) em api/src/main/kotlin/com/kotlinbitcoins/api/quote/QuoteController.kt
- [X] T134 [US4] Mapear `QuoteUnavailable` → `QUOTE_UNAVAILABLE` (503, "Cotação indisponível no momento. Tente novamente em instantes.") em api/src/main/kotlin/com/kotlinbitcoins/api/error/ProblemDetailsMapper.kt
- [X] T135 [US4] Registrar `QuoteService` e `GetCurrentQuote` em bootstrap/src/main/kotlin/com/kotlinbitcoins/bootstrap/UseCaseConfiguration.kt, os adaptadores de cotação em bootstrap/src/main/kotlin/com/kotlinbitcoins/bootstrap/AdapterConfiguration.kt e as propriedades `quote.provider.*` (com `${QUOTE_PROVIDER_URL}`) em bootstrap/src/main/resources/application.yml

**Checkpoint**: US1–US4 completas. A cotação está disponível para compra, posição, resgate e histórico.

---

## Phase 7: User Story 5 - Compra de bitcoin (Priority: P1) 🎯 fecha o MVP

**Goal**: o cliente compra BTC com um valor em R$ pela cotação de venda; o saldo é debitado e um investimento é registrado, com extrato e e-mail.

**Independent Test**: depositar `1000.00`, comprar `400.00` com cotação de venda `400000.00` → saldo `600.00`, investimento de `0.00100000` BTC, linha `PURCHASE` e e-mail (quickstart passos 9–10).

### Tests for User Story 5 ⚠️

- [ ] T136 [P] [US5] Teste da conversão de compra em domain/src/test/kotlin/com/kotlinbitcoins/domain/investment/BtcConversionTest.kt: `purchase(400.00, 400000.00)` → `100_000` sats; arredondamento `FLOOR` no satoshi (ex.: `100.00` a `300000.00` → `33_333` sats); resultado < 1 sat (ex.: `0.01` a `2000000.00`) → `Failure(InvalidAmount)` (FR-013, edge case "valor muito pequeno")
- [ ] T137 [P] [US5] Estender domain/src/test/kotlin/com/kotlinbitcoins/domain/account/AccountTest.kt: `debit(100.00)` sobre `1000.00` → `900.00`; `debit(100.01)` sobre `100.00` → `Failure(InsufficientBalance)` e saldo intacto (FR-010, FR-015)
- [ ] T138 [P] [US5] Teste de `Investment` em domain/src/test/kotlin/com/kotlinbitcoins/domain/investment/InvestmentTest.kt: `Investment.purchase(accountId, purchasedAt, fifoSequence, investedAmount, purchaseQuote, btcAmount)` cria `ACTIVE`, origem `PURCHASE`, `originInvestmentId` nulo; `btcAmount` zero é rejeitado
- [ ] T139 [P] [US5] Teste do caso de uso `PurchaseBitcoin` em application/src/test/kotlin/com/kotlinbitcoins/application/investment/PurchaseBitcoinTest.kt: em `TransactionRunner.run`, faz lock da conta, obtém **uma** cotação via `QuoteService`, converte pela `sellPrice`, debita, grava `Investment` com `nextFifoSequence()` e `purchasedAt = Clock.now()`, grava `Transaction` `PURCHASE` (valor, sats, `sellPrice`, `investmentId`) e publica `PURCHASE_CONFIRMED` (valor, BTC); saldo insuficiente → `Failure(InsufficientBalance)` sem gravações nem evento; `QuoteUnavailable` → `Failure` sem gravações; < 1 sat → `Failure(InvalidAmount)`. Falha de infraestrutura (porta devolve `Failure(InfrastructureFailure)`) → `Failure(ServiceUnavailable)`, sem gravação parcial nem evento
- [ ] T140 [P] [US5] Teste do adaptador de investimentos em infrastructure/src/test/kotlin/com/kotlinbitcoins/infrastructure/persistence/InvestmentRepositoryAdapterIT.kt: `save`; `nextFifoSequence` crescente; `findActiveByAccountInFifoOrder` ordena por `purchased_at` e depois `fifo_sequence`, ignora `CLOSED` e investimentos de outra conta; `purchase_quote` mantém a escala
- [ ] T141 [P] [US5] Teste do `PurchaseController` em api/src/test/kotlin/com/kotlinbitcoins/api/investment/PurchaseControllerTest.kt: `POST /api/v1/me/purchases` → `201` com `investmentId`, `transactionId`, `amount`, `btcAmount: "0.00100000"`, `quote`, `purchasedAt`, `balance`; `INVALID_AMOUNT` 400, `INSUFFICIENT_BALANCE` 422, `QUOTE_UNAVAILABLE` 503
- [ ] T142 [P] [US5] Teste de contrato em bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/contract/PurchasesContractTest.kt cobrindo US5 cenários 1–5: compra de `400.00` a `400000.00` → saldo `600.00` e `0.00100000` BTC; `100.01` com saldo `100.00` → `422` sem alterar saldo, investimentos nem `transactions`; valores inválidos → `400`; fonte fora e sem cotação ≤ 60 s → `503` sem efeito; e-mail com valor em R$ e BTC no `RecordingEmailSender`; linha `PURCHASE` com cotação
- [ ] T143 [P] [US5] Teste de concorrência em bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/concurrency/PurchaseConcurrencyTest.kt: saldo `100.00`, 20 compras paralelas de `10.00` → exatamente 10 sucessos e 10 `INSUFFICIENT_BALANCE`, saldo final `0.00`, nunca negativo, e o número de investimentos é igual ao de sucessos (FR-010, FR-034, SC-005)

### Implementation for User Story 5

- [ ] T144 [US5] Implementar `BtcConversion.purchase(amount: PositiveMoney, sellPrice: QuotePrice): Outcome<InvalidAmount, BtcAmount>` (`FLOOR(amount × 10⁸ ÷ sellPrice)`) em domain/src/main/kotlin/com/kotlinbitcoins/domain/investment/BtcConversion.kt
- [ ] T145 [US5] Adicionar `debit(PositiveMoney): Outcome<InsufficientBalance, Account>` e `data object InsufficientBalance` em domain/src/main/kotlin/com/kotlinbitcoins/domain/account/Account.kt
- [ ] T146 [P] [US5] Implementar `InvestmentStatus` (`ACTIVE`, `CLOSED`), `InvestmentOrigin` (`PURCHASE`, `REINVESTMENT`) e a entidade `Investment` (campos de data-model §2; fábrica `purchase`) em domain/src/main/kotlin/com/kotlinbitcoins/domain/investment/InvestmentStatus.kt, domain/src/main/kotlin/com/kotlinbitcoins/domain/investment/InvestmentOrigin.kt e domain/src/main/kotlin/com/kotlinbitcoins/domain/investment/Investment.kt
- [ ] T147 [P] [US5] Adicionar a fábrica `Transaction.purchase(accountId, amount, btcAmount, sellPrice, investmentId, occurredAt)` em domain/src/main/kotlin/com/kotlinbitcoins/domain/ledger/Transaction.kt
- [ ] T148 [P] [US5] Criar a porta `InvestmentRepository` (`save(Investment)`, `findActiveByAccountInFifoOrder(AccountId)`, `nextFifoSequence()`, todos devolvendo `Outcome<InfrastructureFailure, T>`) em application/src/main/kotlin/com/kotlinbitcoins/application/port/InvestmentRepository.kt
- [ ] T149 [P] [US5] Adicionar o tipo `PURCHASE_CONFIRMED` (com `btcAmount`) em application/src/main/kotlin/com/kotlinbitcoins/application/notification/NotificationEvent.kt, `EmailContent.PurchaseConfirmed(amount, btcAmount)` e o mapeamento em application/src/main/kotlin/com/kotlinbitcoins/application/notification/SendEmailNotification.kt, e o template pt-BR ("Você investiu R$ 400,00 e comprou 0,00100000 BTC.") em infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/email/EmailTemplates.kt
- [ ] T150 [P] [US5] Implementar `InvestmentJpaEntity`, `SpringDataInvestmentRepository` (`nextval('investments_fifo_seq')` em native query), `InvestmentMapper` e `InvestmentRepositoryAdapter` em infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/persistence/investment/InvestmentJpaEntity.kt, infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/persistence/investment/SpringDataInvestmentRepository.kt, infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/persistence/investment/InvestmentMapper.kt e infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/persistence/investment/InvestmentRepositoryAdapter.kt
- [ ] T151 [US5] Implementar `PurchaseError` (`InvalidAmount`, `InsufficientBalance`, `QuoteUnavailable`, `AccountNotFound`, `ServiceUnavailable`) e o caso de uso `PurchaseBitcoin` em application/src/main/kotlin/com/kotlinbitcoins/application/investment/PurchaseError.kt e application/src/main/kotlin/com/kotlinbitcoins/application/investment/PurchaseBitcoin.kt
- [ ] T152 [P] [US5] Implementar `PurchaseResponse` e o `PurchaseController` (`POST /api/v1/me/purchases` → `201`) em api/src/main/kotlin/com/kotlinbitcoins/api/investment/PurchaseController.kt e api/src/main/kotlin/com/kotlinbitcoins/api/investment/PurchaseDtos.kt
- [ ] T153 [US5] Mapear `PurchaseError` (`InsufficientBalance` → `INSUFFICIENT_BALANCE` 422 "Saldo insuficiente para esta compra.", demais reaproveitando os mapeamentos existentes) em api/src/main/kotlin/com/kotlinbitcoins/api/error/ProblemDetailsMapper.kt
- [ ] T154 [US5] Registrar `PurchaseBitcoin` em bootstrap/src/main/kotlin/com/kotlinbitcoins/bootstrap/UseCaseConfiguration.kt e `InvestmentRepositoryAdapter` em bootstrap/src/main/kotlin/com/kotlinbitcoins/bootstrap/AdapterConfiguration.kt

**Checkpoint**: **MVP completo** (US1–US5): cadastro, login, depósito, saldo, cotação e compra.

---

## Phase 8: User Story 6 - Posição dos investimentos (Priority: P2)

**Goal**: o cliente lista os investimentos ativos com data, valor investido, cotação da compra, variação % e valor bruto atual, além do total.

**Independent Test**: comprar a `400000.00`, mudar a cotação de compra para `440000.00` → `variationPercent: "10.00"` e `currentValue: "440.00"` (quickstart passo 11).

### Tests for User Story 6 ⚠️

- [ ] T155 [P] [US6] Estender domain/src/test/kotlin/com/kotlinbitcoins/domain/investment/BtcConversionTest.kt: `currentValue(100_000 sats, 440000.00)` → `440.00`; `FLOOR` no centavo (ex.: `33_333` sats a `300000.00` → `99.99`); poucos satoshis podem valer `0.00` (FR-017)
- [ ] T156 [P] [US6] Teste de `PriceVariation` em domain/src/test/kotlin/com/kotlinbitcoins/domain/quote/PriceVariationTest.kt: `percent(400000.00, 440000.00)` → `10.00`; `percent(400000.00, 360000.00)` → `-10.00`; arredondamento `HALF_UP` em 2 casas (ex.: `300000.00` → `400000.00` = `33.33`)
- [ ] T157 [P] [US6] Teste do caso de uso `GetInvestmentPosition` em application/src/test/kotlin/com/kotlinbitcoins/application/investment/GetInvestmentPositionTest.kt: usa a `buyPrice` de **uma** cotação; cada item com `purchasedAt`, `investedAmount`, `purchaseQuote`, `btcAmount`, `variationPercent`, `currentValue`; `totalCurrentValue` = soma dos valores já arredondados para baixo (FR-022); sem investimentos → lista vazia; `QuoteUnavailable` → `Failure`. Falha de infraestrutura (porta devolve `Failure(InfrastructureFailure)`) → `Failure(ServiceUnavailable)`, sem gravação parcial nem evento
- [ ] T158 [P] [US6] Teste do `PositionController` em api/src/test/kotlin/com/kotlinbitcoins/api/investment/PositionControllerTest.kt: `GET /api/v1/me/investments` → `200` com `quote`, `totalCurrentValue`, `investments[]` em ordem FIFO, `variationPercent` como string com 2 casas; `503` quando sem cotação
- [ ] T159 [P] [US6] Teste de contrato em bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/contract/PositionContractTest.kt cobrindo US6 cenários 1–3 (variação `+10.00%` e `440.00`; lista vazia; investimentos `CLOSED` inseridos diretamente no banco não aparecem)

### Implementation for User Story 6

- [ ] T160 [US6] Adicionar `BtcConversion.currentValue(btc: BtcAmount, buyPrice: QuotePrice): Money` (`FLOOR(sats × buyPrice ÷ 10⁸)` no centavo) em domain/src/main/kotlin/com/kotlinbitcoins/domain/investment/BtcConversion.kt
- [ ] T161 [P] [US6] Implementar `PriceVariation.percent(purchaseQuote, currentBuy): BigDecimal` (`HALF_UP`, escala 2) em domain/src/main/kotlin/com/kotlinbitcoins/domain/quote/PriceVariation.kt
- [ ] T162 [US6] Implementar o read model `InvestmentPosition` (`quote`, `totalCurrentValue`, `items`) e o caso de uso `GetInvestmentPosition` com `GetInvestmentPositionError` (`QuoteUnavailable`, `ServiceUnavailable`) em application/src/main/kotlin/com/kotlinbitcoins/application/investment/InvestmentPosition.kt e application/src/main/kotlin/com/kotlinbitcoins/application/investment/GetInvestmentPosition.kt
- [ ] T163 [US6] Implementar `PositionResponse`/`InvestmentPositionDto` e o `PositionController` (`GET /api/v1/me/investments`) em api/src/main/kotlin/com/kotlinbitcoins/api/investment/PositionController.kt e api/src/main/kotlin/com/kotlinbitcoins/api/investment/PositionDtos.kt
- [ ] T164 [US6] Mapear `GetInvestmentPositionError` em api/src/main/kotlin/com/kotlinbitcoins/api/error/ProblemDetailsMapper.kt e registrar `GetInvestmentPosition` em bootstrap/src/main/kotlin/com/kotlinbitcoins/bootstrap/UseCaseConfiguration.kt

**Checkpoint**: US6 completa; o cliente acompanha o resultado das compras.

---

## Phase 9: User Story 7 - Venda (resgate) de bitcoin (Priority: P2)

**Goal**: o cliente resgata um valor em R$; o sistema liquida investimentos em FIFO pela cotação de compra, credita exatamente o valor pedido e reinveste o residual da liquidação parcial com a cotação e a data originais.

**Independent Test**: comprar em dois momentos, resgatar um valor que consuma o primeiro investimento inteiro e parte do segundo; conferir ordem, saldo, reinvestimento, extrato com o mesmo `requestId` e e-mail (quickstart passos 12–14).

### Tests for User Story 7 ⚠️

- [ ] T165 [P] [US7] Estender domain/src/test/kotlin/com/kotlinbitcoins/domain/investment/BtcConversionTest.kt: `satsToCover(50000.00, 200000.00)` → `25_000_000` sats; `CEILING` no satoshi (ex.: `100.00` a `300000.00` → `33_334` sats) (FR-019a-2)
- [ ] T166 [P] [US7] Teste do `RedemptionPlanner` em domain/src/test/kotlin/com/kotlinbitcoins/domain/investment/RedemptionPlannerTest.kt com os cenários da spec: (1) A=`300.00`, B=`500.00`, pedido `300.00` → A total, B intacto; (2) pedido `500.00` → A total, B parcial com residual valendo `300.00`; (3) 1 BTC comprado a `100000.00`, cotação `200000.00`, pedido `50000.00` → vende `25_000_000` sats, residual `75_000_000` sats, `investedAmount` `75000.00`; pedido igual ao total → todos totais, sem reinvestimento; pedido que termina exatamente no fim de um investimento → total, sem reinvestimento; `CEILING` ≥ sats do investimento → liquida inteiro, crédito = faltante, sem reinvestimento (FR-019a-3); pedido `800.01` com total `800.00` → `Failure(RedemptionExceedsAvailable)`; sem investimentos → o mesmo erro; residual de poucos satoshis valendo `0.00` é liquidado primeiro com crédito `0.00`
- [ ] T167 [P] [US7] Teste de propriedades de conservação em domain/src/test/kotlin/com/kotlinbitcoins/domain/investment/RedemptionPlannerPropertyTest.kt (gerador `kotlin.random.Random` com semente fixa, ≥ 10 000 casos com sats, cotações e pedidos aleatórios ≤ total): Σ créditos = pedido exato; para cada investimento, sats vendidos + sats residuais = sats originais; nunca há reinvestimento com 0 sat; no máximo 1 reinvestimento; em liquidação total, `soldSats × buyPrice ÷ 10⁸ − credit < 0.01`; em liquidação parcial, `soldSats − credit × 10⁸ ÷ buyPrice < 1`; nunca `credit > soldSats × buyPrice ÷ 10⁸` (edge case "diferença de arredondamento")
- [ ] T168 [P] [US7] Estender domain/src/test/kotlin/com/kotlinbitcoins/domain/investment/InvestmentTest.kt: `close(closedAt)` muda para `CLOSED` e preenche `closedAt`; fechar um `CLOSED` viola invariante; `Investment.reinvestmentOf(origin, residualSats)` copia `purchasedAt`, `fifoSequence` e `purchaseQuote`, usa origem `REINVESTMENT`, `originInvestmentId = origin.id` e `investedAmount = FLOOR(residualSats × purchaseQuote ÷ 10⁸)` (FR-020, FR-021)
- [ ] T169 [P] [US7] Estender domain/src/test/kotlin/com/kotlinbitcoins/domain/account/AccountTest.kt: `credit(Money("500.00"))` soma ao saldo; `credit(Money.ZERO)` mantém o saldo
- [ ] T170 [P] [US7] Teste do caso de uso `RedeemInvestments` em application/src/test/kotlin/com/kotlinbitcoins/application/investment/RedeemInvestmentsTest.kt: em `TransactionRunner.run`, lock da conta, **uma** cotação (`buyPrice`), `findActiveByAccountInFifoOrder`, `RedemptionPlanner`; fecha os liquidados; cria o reinvestimento; credita exatamente o pedido; grava uma linha `REDEMPTION` por investimento liquidado (valor creditado, sats vendidos, `buyPrice`) e, se houver, uma `REINVESTMENT` (sats residuais, cotação original, valor investido), todas com o mesmo `RedemptionRequestId` (FR-024, FR-024a); publica `REDEMPTION_CONFIRMED` com valor e Σ sats vendidos; erros (`InvalidAmount`, `RedemptionExceedsAvailable`, `QuoteUnavailable`) → nenhuma gravação nem evento. Falha de infraestrutura (porta devolve `Failure(InfrastructureFailure)`) → `Failure(ServiceUnavailable)`, sem gravação parcial nem evento
- [ ] T171 [P] [US7] Estender infrastructure/src/test/kotlin/com/kotlinbitcoins/infrastructure/persistence/InvestmentRepositoryAdapterIT.kt: `close` grava `status = 'CLOSED'` e `closed_at`; o reinvestimento salvo com `purchased_at` e `fifo_sequence` copiados volta **antes** de um investimento comprado depois do original em `findActiveByAccountInFifoOrder` (US7 cenário 4)
- [ ] T172 [P] [US7] Teste do `RedemptionController` em api/src/test/kotlin/com/kotlinbitcoins/api/investment/RedemptionControllerTest.kt: `POST /api/v1/me/redemptions` → `201` com `requestId`, `amount`, `btcSold`, `quote`, `balance`, `occurredAt`, `liquidations[{investmentId, amount, btcAmount, isPartial}]` e `reinvestment` (ou `null`); `400`, `422 REDEMPTION_EXCEEDS_AVAILABLE`, `503`
- [ ] T173 [P] [US7] Teste de contrato em bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/contract/RedemptionsContractTest.kt cobrindo US7 cenários 1–9 e o roteiro do quickstart passos 12–14 (A `0.00100000` e B `0.00050000` a `400000.00`; `buy` → `200000.00`; resgate `250.00` → A total `200.00`, B parcial `50.00`/`0.00025000`, reinvestimento `0.00025000` a `400000.00` valendo `100.00` com `purchasedAt` de B; saldo +`250.00`; depois `50.01` → `422` sem efeito); linhas de extrato com o mesmo `requestId`; e-mail com BTC vendido e valor resgatado
- [ ] T174 [P] [US7] Teste de concorrência em bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/concurrency/RedemptionConcurrencyTest.kt: um investimento de `0.00200000` BTC comprado a `400000.00`, com cotação de compra `400000.00` (vale `800.00`; cada `100.00` corresponde exatamente a `25_000` sats, sem resto de arredondamento), 10 resgates paralelos de `100.00` → exatamente 8 sucessos; nenhum sat liquidado duas vezes (Σ sats ativos + Σ sats vendidos = Σ comprados); saldo = depósitos − compras + `800.00` (FR-034, SC-005)

### Implementation for User Story 7

- [ ] T175 [US7] Adicionar `BtcConversion.satsToCover(amount: Money, buyPrice: QuotePrice): BtcAmount` (`CEILING(amount × 10⁸ ÷ buyPrice)`) em domain/src/main/kotlin/com/kotlinbitcoins/domain/investment/BtcConversion.kt
- [ ] T176 [US7] Implementar `RedemptionPlan` (`liquidations: List<Liquidation(investment, soldSats, credit, isPartial)>`, `residual: Residual?(origin, sats)`, `totalSoldSats`) e o `RedemptionPlanner` (função pura com o algoritmo de research.md R-04: checa `pedido > total` → `RedemptionExceedsAvailable`; percorre FIFO; total quando `faltante ≥ valor`; parcial com `CEILING`; parcial que consome tudo vira total) em domain/src/main/kotlin/com/kotlinbitcoins/domain/investment/RedemptionPlan.kt e domain/src/main/kotlin/com/kotlinbitcoins/domain/investment/RedemptionPlanner.kt
- [ ] T177 [US7] Adicionar `close(closedAt)` e a fábrica `reinvestmentOf(origin, residualSats)` em domain/src/main/kotlin/com/kotlinbitcoins/domain/investment/Investment.kt
- [ ] T178 [US7] Adicionar `credit(Money): Account` em domain/src/main/kotlin/com/kotlinbitcoins/domain/account/Account.kt
- [ ] T179 [P] [US7] Adicionar as fábricas `Transaction.redemption(accountId, amount, soldSats, buyPrice, investmentId, requestId, occurredAt)` e `Transaction.reinvestment(accountId, investedAmount, residualSats, originalQuote, investmentId, requestId, occurredAt)` em domain/src/main/kotlin/com/kotlinbitcoins/domain/ledger/Transaction.kt
- [ ] T180 [US7] Adicionar `close(Investment)` à porta application/src/main/kotlin/com/kotlinbitcoins/application/port/InvestmentRepository.kt e implementá-lo em infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/persistence/investment/InvestmentRepositoryAdapter.kt
- [ ] T181 [P] [US7] Adicionar o tipo `REDEMPTION_CONFIRMED` em application/src/main/kotlin/com/kotlinbitcoins/application/notification/NotificationEvent.kt, `EmailContent.RedemptionConfirmed(amount, btcSold)` e o mapeamento em application/src/main/kotlin/com/kotlinbitcoins/application/notification/SendEmailNotification.kt, e o template pt-BR em infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/email/EmailTemplates.kt
- [ ] T182 [US7] Implementar `RedemptionError` (`InvalidAmount`, `RedemptionExceedsAvailable`, `QuoteUnavailable`, `AccountNotFound`, `ServiceUnavailable`) e o caso de uso `RedeemInvestments` em application/src/main/kotlin/com/kotlinbitcoins/application/investment/RedemptionError.kt e application/src/main/kotlin/com/kotlinbitcoins/application/investment/RedeemInvestments.kt
- [ ] T183 [P] [US7] Implementar `RedemptionResponse` e o `RedemptionController` (`POST /api/v1/me/redemptions` → `201`) em api/src/main/kotlin/com/kotlinbitcoins/api/investment/RedemptionController.kt e api/src/main/kotlin/com/kotlinbitcoins/api/investment/RedemptionDtos.kt
- [ ] T184 [US7] Mapear `RedemptionExceedsAvailable` → `REDEMPTION_EXCEEDS_AVAILABLE` (422, "O valor solicitado excede o valor atual dos seus investimentos.") em api/src/main/kotlin/com/kotlinbitcoins/api/error/ProblemDetailsMapper.kt e registrar `RedeemInvestments` em bootstrap/src/main/kotlin/com/kotlinbitcoins/bootstrap/UseCaseConfiguration.kt

**Checkpoint**: ciclo de investimento completo (compra → posição → resgate).

---

## Phase 10: User Story 8 - Extrato (Priority: P2)

**Goal**: o cliente lista depósitos, compras, resgates e reinvestimentos (últimos 90 dias por padrão ou intervalo customizado inclusivo), do mais recente para o mais antigo.

**Independent Test**: fazer depósito, compra e resgate; o extrato padrão traz todos; um intervalo customizado filtra; `from > to` ou data malformada → `422 INVALID_DATE_RANGE` (quickstart passos 15–16).

### Tests for User Story 8 ⚠️

- [ ] T185 [P] [US8] Teste de `StatementPeriod` em application/src/test/kotlin/com/kotlinbitcoins/application/ledger/StatementPeriodTest.kt (zona `America/Sao_Paulo`, `Clock` fixo): sem datas → `from` = 00:00:00 local de (hoje local − 90 dias), `to` = agora; `from=2026-10-01&to=2026-11-09` → `[2026-10-01T03:00:00Z, 2026-11-10T03:00:00Z)` (inclui 23:59:59.999… local); `from = to` → o dia inteiro; `from > to` → `Failure(InvalidDateRange)`; só uma das duas datas → `Failure(InvalidDateRange)` (FR-025, FR-034a)
- [ ] T186 [P] [US8] Teste do caso de uso `GetStatement` em application/src/test/kotlin/com/kotlinbitcoins/application/ledger/GetStatementTest.kt: consulta `findByAccountBetween` só da conta autenticada, com os limites do `StatementPeriod`, e devolve na ordem do repositório. Falha de infraestrutura (porta devolve `Failure(InfrastructureFailure)`) → `Failure(ServiceUnavailable)`, sem gravação parcial nem evento
- [ ] T187 [P] [US8] Estender infrastructure/src/test/kotlin/com/kotlinbitcoins/infrastructure/persistence/TransactionRepositoryAdapterIT.kt com `findByAccountBetween`: ordem `occurred_at DESC`; limite inferior inclusivo e superior exclusivo; transações de outra conta nunca aparecem
- [ ] T188 [P] [US8] Teste do `StatementController` em api/src/test/kotlin/com/kotlinbitcoins/api/ledger/StatementControllerTest.kt: `GET /api/v1/me/statement` → `200` com `from`, `to`, `entries[]`; `DEPOSIT` com `btcAmount` e `quote` `null`; `from=2026-13-01` ou `from=abc` → `422 INVALID_DATE_RANGE` (não `400`)
- [ ] T189 [P] [US8] Teste de contrato em bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/contract/StatementContractTest.kt cobrindo US8 cenários 1–4: com `MutableClock`, transações de 100 dias atrás não aparecem no padrão; intervalo customizado inclusivo nas duas pontas (transação às 23:59 local do dia `to` aparece; às 00:00 do dia seguinte não); `from=2026-11-10&to=2026-11-01` → `422`; compras e resgates trazem cotação e BTC

### Implementation for User Story 8

- [ ] T190 [US8] Implementar `StatementPeriod` (cálculo dos limites em `Instant` a partir de `LocalDate?` e do `ZoneId` de negócio) e `StatementError` (`InvalidDateRange`, `ServiceUnavailable`) em application/src/main/kotlin/com/kotlinbitcoins/application/ledger/StatementPeriod.kt e application/src/main/kotlin/com/kotlinbitcoins/application/ledger/StatementError.kt
- [ ] T191 [US8] Adicionar `findByAccountBetween(accountId, fromInclusive, toExclusive): List<Transaction>` à porta application/src/main/kotlin/com/kotlinbitcoins/application/port/TransactionRepository.kt e implementá-lo em infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/persistence/ledger/TransactionRepositoryAdapter.kt e infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/persistence/ledger/SpringDataTransactionRepository.kt
- [ ] T192 [US8] Implementar o caso de uso `GetStatement` (devolve `Statement(from, to, entries)`) em application/src/main/kotlin/com/kotlinbitcoins/application/ledger/GetStatement.kt
- [ ] T193 [US8] Implementar `StatementResponse`/`StatementEntryDto` e o `StatementController` (`GET /api/v1/me/statement?from&to`; `LocalDate.parse` estrito, data malformada → `INVALID_DATE_RANGE`) em api/src/main/kotlin/com/kotlinbitcoins/api/ledger/StatementController.kt e api/src/main/kotlin/com/kotlinbitcoins/api/ledger/StatementDtos.kt
- [ ] T194 [US8] Mapear `InvalidDateRange` → `INVALID_DATE_RANGE` (422, "Intervalo de datas inválido.") em api/src/main/kotlin/com/kotlinbitcoins/api/error/ProblemDetailsMapper.kt e registrar `GetStatement` em bootstrap/src/main/kotlin/com/kotlinbitcoins/bootstrap/UseCaseConfiguration.kt

**Checkpoint**: US1–US8 completas.

---

## Phase 11: User Story 9 - Volume diário (Priority: P3)

**Goal**: qualquer cliente autenticado consulta o total de BTC comprado e vendido no dia corrente (America/Sao_Paulo), agregando todos os clientes.

**Independent Test**: dois clientes compram e um vende no mesmo dia; o volume devolve as somas, sem identificar clientes (quickstart passo 17).

### Tests for User Story 9 ⚠️

- [ ] T195 [P] [US9] Teste do caso de uso `GetDailyVolume` em application/src/test/kotlin/com/kotlinbitcoins/application/ledger/GetDailyVolumeTest.kt: com `Clock` em `2026-11-10T01:00:00Z` (22:00 de 09/11 em Brasília), consulta `[2026-11-09T03:00:00Z, 2026-11-10T03:00:00Z)` e devolve `date = 2026-11-09`; sem operações → zero e zero. Falha de infraestrutura (porta devolve `Failure(InfrastructureFailure)`) → `Failure(ServiceUnavailable)`, sem gravação parcial nem evento
- [ ] T196 [P] [US9] Estender infrastructure/src/test/kotlin/com/kotlinbitcoins/infrastructure/persistence/TransactionRepositoryAdapterIT.kt com `sumBtcByTypeBetween`: soma `PURCHASE` e `REDEMPTION` de todas as contas; `REINVESTMENT` e `DEPOSIT` não entram; sem linhas → `0`; respeita os limites (23:59:59 local entra, 00:00 do dia seguinte não)
- [ ] T197 [P] [US9] Teste do `MarketController` em api/src/test/kotlin/com/kotlinbitcoins/api/market/MarketControllerTest.kt: `GET /api/v1/market/daily-volume` → `200 {"date":"2026-11-09","boughtBtc":"0.00300000","soldBtc":"0.00050000"}`, sem nenhum campo que identifique cliente
- [ ] T198 [P] [US9] Teste de contrato em bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/contract/DailyVolumeContractTest.kt cobrindo US9 cenários 1–5 (X compra `0.001`, Y compra `0.002`, X vende `0.0005` → `0.00300000`/`0.00050000`; operações de ontem ignoradas; compra às 22:00 local conta no dia local; reinvestimento não conta como compra)

### Implementation for User Story 9

- [ ] T199 [US9] Adicionar `sumBtcByTypeBetween(type, fromInclusive, toExclusive): BtcAmount` à porta application/src/main/kotlin/com/kotlinbitcoins/application/port/TransactionRepository.kt e implementá-lo (agregação `SUM` no banco, usando `ix_transactions_volume`) em infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/persistence/ledger/TransactionRepositoryAdapter.kt e infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/persistence/ledger/SpringDataTransactionRepository.kt
- [ ] T200 [US9] Implementar o read model `DailyVolume(date, boughtBtc, soldBtc)`, `DailyVolumeError` (`ServiceUnavailable`) e o caso de uso `GetDailyVolume` em application/src/main/kotlin/com/kotlinbitcoins/application/ledger/GetDailyVolume.kt
- [ ] T201 [US9] Implementar `DailyVolumeResponse` e o `MarketController` (`GET /api/v1/market/daily-volume`) em api/src/main/kotlin/com/kotlinbitcoins/api/market/MarketController.kt
- [ ] T202 [US9] Registrar `GetDailyVolume` em bootstrap/src/main/kotlin/com/kotlinbitcoins/bootstrap/UseCaseConfiguration.kt

**Checkpoint**: US9 completa.

---

## Phase 12: User Story 10 - Histórico de cotação (Priority: P3)

**Goal**: o sistema registra a cotação a cada 10 minutos alinhados ao relógio, expurga registros com mais de 90 dias e expõe as últimas 24 h.

**Independent Test**: executar o job de captura em slots distintos (com `MutableClock` e WireMock) e consultar o histórico: pontos alinhados, ordem crescente, lacunas omitidas; executar o expurgo e verificar que só registros com mais de 90 dias somem (quickstart passo 20).

### Tests for User Story 10 ⚠️

- [ ] T203 [P] [US10] Teste de `QuoteSnapshot` em domain/src/test/kotlin/com/kotlinbitcoins/domain/quote/QuoteSnapshotTest.kt: `slotFor(Instant.parse("2026-11-09T11:17:42Z"))` → `2026-11-09T11:10:00Z`; um instante já alinhado não muda; `QuoteSnapshot.from(quote, slotAt)` copia preços e `capturedAt = quote.quotedAt`
- [ ] T204 [P] [US10] Teste do caso de uso `CaptureQuoteSnapshot` em application/src/test/kotlin/com/kotlinbitcoins/application/quote/CaptureQuoteSnapshotTest.kt: chama `QuoteProvider.fetchCurrent()` **diretamente** (sem o fallback de 60 s do `QuoteService`); em sucesso, `saveIfAbsent` no slot do `Clock` e `QuoteCache.put`; em falha, não grava nada e devolve `Failure` (ponto omitido, FR-028). Falha de infraestrutura (porta devolve `Failure(InfrastructureFailure)`) → `Failure(ServiceUnavailable)`, sem gravação parcial nem evento
- [ ] T205 [P] [US10] Teste do caso de uso `PurgeQuoteHistory` em application/src/test/kotlin/com/kotlinbitcoins/application/quote/PurgeQuoteHistoryTest.kt: chama `deleteOlderThan(now − 90 dias)`. Falha de infraestrutura (porta devolve `Failure(InfrastructureFailure)`) → `Failure(ServiceUnavailable)`, sem gravação parcial nem evento
- [ ] T206 [P] [US10] Teste do caso de uso `GetQuoteHistory` em application/src/test/kotlin/com/kotlinbitcoins/application/quote/GetQuoteHistoryTest.kt: chama `findSince(now − 24 h)` e devolve no máximo 144 pontos em ordem crescente. Falha de infraestrutura (porta devolve `Failure(InfrastructureFailure)`) → `Failure(ServiceUnavailable)`, sem gravação parcial nem evento
- [ ] T207 [P] [US10] Teste do adaptador de histórico em infrastructure/src/test/kotlin/com/kotlinbitcoins/infrastructure/persistence/QuoteHistoryRepositoryAdapterIT.kt: `saveIfAbsent` duas vezes no mesmo slot mantém o primeiro (`ON CONFLICT (slot_at) DO NOTHING`); `findSince` exclusivo no limite inferior e em ordem crescente; `deleteOlderThan` remove só os anteriores ao limite
- [ ] T208 [P] [US10] Teste dos jobs em infrastructure/src/test/kotlin/com/kotlinbitcoins/infrastructure/scheduling/QuoteHistoryJobsIT.kt: `CaptureQuoteSnapshotJob.run()` e `PurgeQuoteHistoryJob.run()` chamados diretamente delegam aos casos de uso; duas invocações simultâneas da captura com ShedLock executam o caso de uso uma vez só; falha da fonte é registrada em log e não lança exceção
- [ ] T209 [P] [US10] Teste do `QuoteHistoryController` em api/src/test/kotlin/com/kotlinbitcoins/api/quote/QuoteHistoryControllerTest.kt: `GET /api/v1/quotes/history` → `200 {"points":[{"slotAt":"...-03:00","buyPrice":"...","sellPrice":"..."}]}`
- [ ] T210 [P] [US10] Teste de contrato em bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/contract/QuoteHistoryContractTest.kt cobrindo US10 cenários 1–3: capturas em 3 slots com o stub derrubado no do meio → 2 pontos, minutos múltiplos de 10, ordem crescente, sem interpolação; ponto de 25 h atrás não aparece; registro com 91 dias removido pelo expurgo e o de 89 dias preservado (SC-009)

### Implementation for User Story 10

- [ ] T211 [US10] Implementar `QuoteSnapshot` (`slotAt`, `buyPrice`, `sellPrice`, `capturedAt`; `slotFor(Instant)` trunca para múltiplo de 10 min) em domain/src/main/kotlin/com/kotlinbitcoins/domain/quote/QuoteSnapshot.kt
- [ ] T212 [P] [US10] Criar a porta `QuoteHistoryRepository` (`saveIfAbsent(QuoteSnapshot)`, `findSince(Instant)`, `deleteOlderThan(Instant)`, todos devolvendo `Outcome<InfrastructureFailure, T>`) em application/src/main/kotlin/com/kotlinbitcoins/application/port/QuoteHistoryRepository.kt
- [ ] T213 [US10] Implementar `QuoteHistoryError` (`SourceUnavailable`, `ServiceUnavailable`) e os casos de uso `CaptureQuoteSnapshot`, `PurgeQuoteHistory` e `GetQuoteHistory` em application/src/main/kotlin/com/kotlinbitcoins/application/quote/CaptureQuoteSnapshot.kt, application/src/main/kotlin/com/kotlinbitcoins/application/quote/PurgeQuoteHistory.kt e application/src/main/kotlin/com/kotlinbitcoins/application/quote/GetQuoteHistory.kt
- [ ] T214 [P] [US10] Implementar `QuoteHistoryJpaEntity`, `SpringDataQuoteHistoryRepository` (native `INSERT ... ON CONFLICT (slot_at) DO NOTHING`) e `QuoteHistoryRepositoryAdapter` em infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/persistence/quote/QuoteHistoryJpaEntity.kt, infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/persistence/quote/SpringDataQuoteHistoryRepository.kt e infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/persistence/quote/QuoteHistoryRepositoryAdapter.kt
- [ ] T215 [US10] Implementar `CaptureQuoteSnapshotJob` (`@Scheduled(cron = "0 */10 * * * *", zone = "America/Sao_Paulo")`, `@SchedulerLock(name = "captureQuoteSnapshot", lockAtMostFor = "PT5M")`) e `PurgeQuoteHistoryJob` (`@Scheduled(cron = "0 0 3 * * *", zone = "America/Sao_Paulo")`, `@SchedulerLock(name = "purgeQuoteHistory")`) em infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/scheduling/CaptureQuoteSnapshotJob.kt e infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/scheduling/PurgeQuoteHistoryJob.kt
- [ ] T216 [US10] Implementar `SchedulingConfiguration` (`@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")`, `JdbcTemplateLockProvider` com `usingDbTime()` sobre a tabela `shedlock`) em bootstrap/src/main/kotlin/com/kotlinbitcoins/bootstrap/SchedulingConfiguration.kt
- [ ] T217 [US10] Implementar `QuoteHistoryResponse` e o `QuoteHistoryController` (`GET /api/v1/quotes/history`) em api/src/main/kotlin/com/kotlinbitcoins/api/quote/QuoteHistoryController.kt
- [ ] T218 [US10] Registrar os três casos de uso em bootstrap/src/main/kotlin/com/kotlinbitcoins/bootstrap/UseCaseConfiguration.kt e `QuoteHistoryRepositoryAdapter` e os dois jobs em bootstrap/src/main/kotlin/com/kotlinbitcoins/bootstrap/AdapterConfiguration.kt

**Checkpoint**: todas as 10 user stories funcionam de forma independente.

---

## Phase 13: Polish & Cross-Cutting Concerns

**Purpose**: critérios de sucesso transversais (SC-002..SC-010), conformidade com o contrato e validação final.

- [ ] T219 [P] Teste de isolamento em bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/isolation/AccountIsolationTest.kt: com os tokens de Ana e Bia, `GET /me/balance`, `/me/investments` e `/me/statement` só trazem dados do dono do token; resgate de Bia nunca liquida investimentos de Ana; token com `sub` de conta inexistente → `401` (FR-033, SC-004)
- [ ] T220 [P] Teste de cobertura de erros em bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/contract/ErrorCodesContractTest.kt: provoca cada um dos 10 `code` de R-03 pela API real (para `SERVICE_UNAVAILABLE`, pausa o contêiner do PostgreSQL) e verifica status, `Content-Type: application/problem+json`, `code`, `detail` em português e ausência de stack trace ou nome de classe (FR-035, SC-010)
- [ ] T221 [P] Teste de conformidade com o contrato em bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/contract/OpenApiConformanceTest.kt: compara o `/v3/api-docs` gerado pelo springdoc com specs/001-bitcoin-investment-api/contracts/openapi.yaml (paths, métodos, status de resposta, campos `required` dos schemas); diferença falha o build (R-10)
- [ ] T222 [P] Teste de invariantes contábeis em bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/e2e/LedgerInvariantsTest.kt: sequência aleatória com semente fixa de depósitos, compras e resgates com cotações variando; ao final, saldo = Σ DEPOSIT − Σ PURCHASE + Σ REDEMPTION (SC-002) e Σ sats ativos = Σ sats PURCHASE − Σ sats REDEMPTION (SC-003)
- [ ] T223 [P] Teste ponta a ponta em bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/e2e/QuickstartFlowTest.kt automatizando os passos 1–18 de specs/001-bitcoin-investment-api/quickstart.md com WireMock e `MutableClock`
- [ ] T224 [P] Teste de dados sensíveis em bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/security/SensitiveDataLoggingTest.kt: captura os logs (appender de teste) durante cadastro, login e uma rota autenticada e verifica que senha, `accessToken` e header `Authorization` não aparecem; `outbox_events.payload` também não os contém (FR-007)
- [ ] T225 [P] Teste de latência em bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/performance/QueryLatencySmokeTest.kt: com dados semeados (100 investimentos, 1 000 transações, 144 pontos de histórico), 200 chamadas a cada consulta (saldo, cotação, posição, extrato, volume, histórico) com p95 ≤ 2 s (SC-006)
- [ ] T226 [P] Teste de vazão de e-mails em bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/performance/EmailThroughputTest.kt: 200 depósitos seguidos com o `RecordingEmailSender` atrasando 50 ms por envio; o p95 de `outbox_events.created_at` até `sent_at` deve ser ≤ 5 min e 100% dos eventos devem terminar com `sent_at` preenchido (SC-007)
- [ ] T227 [P] Teste de completude do histórico em bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/e2e/QuoteHistoryCompletenessTest.kt: avança o `MutableClock` por 24 h em passos de 10 min chamando o `CaptureQuoteSnapshotJob` a cada slot com a fonte disponível; `GET /api/v1/quotes/history` devolve ≥ 143 dos 144 pontos (SC-008)
- [ ] T228 Configurar o springdoc (título, versão, esquema `bearerAuth`, servidor `/api/v1`) em bootstrap/src/main/kotlin/com/kotlinbitcoins/bootstrap/OpenApiConfiguration.kt e confirmar em bootstrap/src/main/resources/application.yml que o actuator expõe só `health` (`readiness`, `liveness`)
- [ ] T229 Rodar `./gradlew check` e corrigir todos os apontamentos de ktlint e detekt e testes falhando nos 5 módulos
- [ ] T230 Rodar `docker build -t kotlinbitcoins:local .` e `docker compose --profile stub up -d --build`; confirmar todos os serviços `healthy` em `docker-compose.yml` e as 2 réplicas atendendo via Nginx
- [ ] T231 Executar manualmente o roteiro de specs/001-bitcoin-investment-api/quickstart.md (passos 1–20 e conferências finais, incluindo Mailpit parado → retry/DLQ e logs JSON com `correlationId`) e registrar no próprio specs/001-bitcoin-investment-api/quickstart.md qualquer divergência encontrada
- [ ] T232 Revisão de conformidade (passo 7 do Workflow da Constitution): rodar `/speckit-converge` e conferir o código contra spec, plan e os 6 princípios (regra de dependência garantida pelo compilador; nenhuma exceção atravessando camadas; nenhum `Double`/`Float` em código de negócio; os 10 `code` do contrato); registrar as divergências em specs/001-bitcoin-investment-api/checklists/constitution-review.md

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sem dependências.
- **Foundational (Phase 2)**: depende do Setup e **bloqueia todas as user stories**.
- **User Stories (Phases 3–12)**: dependem do Foundational e seguem as dependências abaixo.
- **Polish (Phase 13)**: depende de todas as user stories.

### User Story Dependencies

| Story | Depende de | Motivo |
|-------|------------|--------|
| US1 Cadastro/autenticação (P1) | Foundational | — |
| US2 Depósito (P1) | US1 | conta e token; cria `Transaction`, `TransactionRepository` e o pipeline de e-mail |
| US3 Saldo (P1) | US1 | só precisa de conta (os testes com depósito usam US2, se já pronta) |
| US4 Cotação (P1) | Foundational | independente de contas no domínio; o contrato usa token (US1) |
| US5 Compra (P1) | US2, US4 | saldo para debitar, extrato e e-mail (US2), cotação (US4); cria `Investment` |
| US6 Posição (P2) | US5 | precisa de investimentos |
| US7 Resgate (P2) | US5, US6 | investimentos e `currentValue` (FR-022 usa o mesmo total da posição) |
| US8 Extrato (P2) | US2 | lê `transactions`; os cenários completos usam US5 e US7 |
| US9 Volume diário (P3) | US5 | soma `PURCHASE`; a parte de vendas usa US7 |
| US10 Histórico (P3) | US4 | reusa `QuoteProvider` e `QuoteCache` |

Grafo:

```text
Setup → Foundational ─┬─► US1 ─┬─► US2 ─┬─► US5 ─► US6 ─► US7
                      │        │        │     └──────► US9
                      │        └─► US3  └─► US8
                      └─► US4 ─────────────► US5
                               └─► US10
```

### Within Each User Story

- Testes primeiro, e eles MUST falhar (Red) antes da implementação.
- Domínio → portas → casos de uso → adaptadores → controllers → mapeamento de erros → wiring em `bootstrap`.
- O teste de contrato da story fica verde só depois do wiring (última tarefa da fase).
- Commit ao fim de cada ciclo Red → Green → Refactor.

### Arquivos compartilhados (evitar edição simultânea)

Estes arquivos são estendidos por várias stories, então tarefas que os tocam em stories diferentes não rodam em paralelo: api/src/main/kotlin/com/kotlinbitcoins/api/error/ProblemDetailsMapper.kt, bootstrap/src/main/kotlin/com/kotlinbitcoins/bootstrap/UseCaseConfiguration.kt, bootstrap/src/main/kotlin/com/kotlinbitcoins/bootstrap/AdapterConfiguration.kt, domain/src/main/kotlin/com/kotlinbitcoins/domain/account/Account.kt, domain/src/main/kotlin/com/kotlinbitcoins/domain/ledger/Transaction.kt, domain/src/main/kotlin/com/kotlinbitcoins/domain/investment/BtcConversion.kt, application/src/main/kotlin/com/kotlinbitcoins/application/port/TransactionRepository.kt, application/src/main/kotlin/com/kotlinbitcoins/application/notification/NotificationEvent.kt, application/src/main/kotlin/com/kotlinbitcoins/application/notification/SendEmailNotification.kt e infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/email/EmailTemplates.kt.

### Parallel Opportunities

- Setup: as tarefas de arquivos de configuração (`.editorconfig`, detekt, `.env.example`, Docker, compose, Nginx, stub, CI) rodam juntas.
- Foundational: os 7 testes de domínio; depois os value objects independentes; as 6 migrações; os 4 testes da camada HTTP.
- Depois do Foundational: **US1 e US4 em paralelo**. Depois de US1: **US2 e US3 em paralelo**. Depois de US4: **US10 em paralelo** com o resto.
- Depois de US5: **US6, US8 e US9 em paralelo**. US7 começa quando US6 termina.
- Em cada story, todas as tarefas de teste marcadas [P] rodam juntas.

---

## Parallel Example: User Story 5

```bash
# Red: todos os testes da US5 de uma vez
Task: "Teste da conversão de compra em domain/src/test/kotlin/com/kotlinbitcoins/domain/investment/BtcConversionTest.kt"
Task: "Teste de Investment em domain/src/test/kotlin/com/kotlinbitcoins/domain/investment/InvestmentTest.kt"
Task: "Teste do caso de uso PurchaseBitcoin em application/src/test/kotlin/com/kotlinbitcoins/application/investment/PurchaseBitcoinTest.kt"
Task: "Teste do adaptador de investimentos em infrastructure/src/test/kotlin/com/kotlinbitcoins/infrastructure/persistence/InvestmentRepositoryAdapterIT.kt"
Task: "Teste do PurchaseController em api/src/test/kotlin/com/kotlinbitcoins/api/investment/PurchaseControllerTest.kt"
Task: "Teste de contrato em bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/contract/PurchasesContractTest.kt"
Task: "Teste de concorrência em bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/concurrency/PurchaseConcurrencyTest.kt"

# Green: implementações independentes em paralelo
Task: "Implementar Investment em domain/src/main/kotlin/com/kotlinbitcoins/domain/investment/Investment.kt"
Task: "Criar a porta InvestmentRepository em application/src/main/kotlin/com/kotlinbitcoins/application/port/InvestmentRepository.kt"
Task: "Implementar InvestmentJpaEntity ... InvestmentRepositoryAdapter em infrastructure/src/main/kotlin/com/kotlinbitcoins/infrastructure/persistence/investment/"
Task: "Implementar PurchaseController em api/src/main/kotlin/com/kotlinbitcoins/api/investment/PurchaseController.kt"
```

## Parallel Example: User Story 7

```bash
# Red: regras de resgate no domínio, em paralelo
Task: "RedemptionPlannerTest em domain/src/test/kotlin/com/kotlinbitcoins/domain/investment/RedemptionPlannerTest.kt"
Task: "RedemptionPlannerPropertyTest em domain/src/test/kotlin/com/kotlinbitcoins/domain/investment/RedemptionPlannerPropertyTest.kt"
Task: "RedeemInvestmentsTest em application/src/test/kotlin/com/kotlinbitcoins/application/investment/RedeemInvestmentsTest.kt"
Task: "RedemptionsContractTest em bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/contract/RedemptionsContractTest.kt"
Task: "RedemptionConcurrencyTest em bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/concurrency/RedemptionConcurrencyTest.kt"
```

---

## Implementation Strategy

### MVP First (US1 → US5)

A spec marca US1–US5 como P1 e diz que "junto com conta, depósito, saldo e cotação [a compra] forma o MVP".

1. Phase 1: Setup.
2. Phase 2: Foundational (bloqueia tudo).
3. US1 → **validar**: cadastro, login e 401 sem token.
4. US2 + US3 → **validar**: depósito, saldo e e-mail.
5. US4 → **validar**: cotação e fallback de 60 s.
6. US5 → **PARAR e VALIDAR o MVP** com o quickstart passos 1–10.

### Incremental Delivery

1. MVP (US1–US5).
2. US6 (posição) → demo.
3. US7 (resgate), a regra mais crítica: só depois do `RedemptionPlannerPropertyTest` verde → demo do ciclo completo.
4. US8 (extrato) → demo.
5. US9 e US10 (informação de mercado) → demo.
6. Polish: isolamento, cobertura de erros, conformidade OpenAPI e quickstart completo.

### Parallel Team Strategy

1. Todos: Setup + Foundational.
2. Dev A: US1 → US2 → US3. Dev B: US4 → US10.
3. Dev A + B: US5 (junta saldo e cotação).
4. Dev A: US6 → US7. Dev B: US8 → US9.

---

## Notes

- [P] = arquivos diferentes, sem dependência de tarefa incompleta.
- [USn] liga a tarefa à user story para rastreabilidade.
- Verificar que os testes falham antes de implementar (Princípio IV).
- Toda correção de defeito vem com um teste que o reproduz.
- Decisões tomadas aqui que não estavam explícitas nos artefatos: (1) `AdapterConfiguration.kt` separa os beans de adaptadores dos de casos de uso em `bootstrap`; (2) o texto dos e-mails é renderizado em `infrastructure/email` a partir de um `EmailContent` tipado definido em `application` (o caso de uso decide *o que* vai no e-mail, FR-030; o adaptador decide *como* aparece); (3) a deduplicação por `sent_at` fica no `EmailNotificationListener` (mecânica de entrega, não regra de negócio).
