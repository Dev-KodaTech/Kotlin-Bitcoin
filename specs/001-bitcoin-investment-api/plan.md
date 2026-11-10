# Implementation Plan: API de Investimento em Bitcoins

**Branch**: `001-bitcoin-investment-api` | **Date**: 2026-11-09 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-bitcoin-investment-api/spec.md`

## Summary

API REST para um cliente criar conta, depositar reais fictícios, comprar e resgatar bitcoins pela cotação do Mercado Bitcoin, acompanhar a posição, consultar extrato, volume diário e histórico de cotação. Toda regra de negócio fica em Kotlin puro (`domain` + `application`): conversões R$ ↔ BTC com arredondamento explícito (FR-017, FR-019a), FIFO de liquidação com reinvestimento do residual (FR-019 a FR-022) e erros como valor. A infraestrutura (Spring Boot 3, PostgreSQL, Redis, RabbitMQ) implementa portas definidas pela aplicação. Atomicidade e concorrência vêm de transação por operação, com lock pessimista na conta. E-mails saem por Transactional Outbox, e a cotação usa cache compartilhado com janela de 60 s.

Detalhes e alternativas: [research.md](./research.md). Modelo: [data-model.md](./data-model.md). Contrato: [contracts/openapi.yaml](./contracts/openapi.yaml). Validação: [quickstart.md](./quickstart.md).

## Technical Context

**Language/Version**: Kotlin 2.x sobre JDK 21 (LTS)

**Primary Dependencies**: Spring Boot 3 (web, validation, security/OAuth2 resource server com JWT, actuator, amqp, data-redis, data-jpa), Hibernate 6, Flyway, ShedLock (provider JDBC), springdoc-openapi, logstash-logback-encoder. Build: Gradle Kotlin DSL + version catalog; ktlint e detekt.

**Storage**: PostgreSQL 16 (dados de negócio, outbox, histórico, locks do ShedLock); Redis 7 (cache da cotação atual); RabbitMQ 3 (fila de e-mails + DLQ).

**Testing**: JUnit 5, AssertJ, MockK; Testcontainers (PostgreSQL, Redis, RabbitMQ); WireMock (Mercado Bitcoin, Brevo); MockMvc para contrato.

**Target Platform**: contêiner Linux (JRE 21, usuário não-root); docker-compose com 2 réplicas atrás de Nginx.

**Project Type**: web-service (API REST, sem frontend).

**Performance Goals**: SC-006, p95 ≤ 2 s nas consultas (saldo, cotação, posição, extrato, volume, histórico); SC-007, 95% dos e-mails em ≤ 5 min.

**Constraints**: precisão exata (R$ em centavos `NUMERIC(19,2)`, BTC em satoshis `BIGINT`, cotação `NUMERIC(19,8)`); cotação com no máximo 60 s; atomicidade e segurança sob concorrência por conta; instantes em UTC no banco e America/Sao_Paulo na saída; nenhum segredo no código.

**Scale/Scope**: 10 user stories, 11 endpoints, 6 tabelas, 2 jobs agendados + 1 relay. Volume esperado de aplicação de estudo/demonstração; o desenho stateless permite mais réplicas sem mudança de código.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution v1.0.0 (ratificada em 2026-10-27).

### Princípios

| Princípio | Como o plano atende | Status |
|-----------|---------------------|--------|
| **I. Arquitetura em Camadas** | 5 módulos Gradle com dependências unidirecionais (`api`/`infrastructure` → `application` → `domain`); só `bootstrap` conhece todos e faz a composição via `@Bean`. A regra de dependência é verificada pelo compilador (R-02). | ✅ |
| **II. Lógica de Negócio Isolada** | Conversões, FIFO, reinvestimento, limites de data e volume ficam em `domain`/`application`. `api` só valida formato e mapeia erros; `infrastructure` só persiste e integra. Casos de uso testados com MockK, sem Spring. | ✅ |
| **III. Erro como Valor** | `Outcome<E, T>` próprio + `sealed interface` de erro por operação (R-03). Exceções só dentro dos adaptadores, convertidas em erro tipado: toda porta devolve `Outcome<InfrastructureFailure, T>` e nenhuma exceção atravessa camadas. `TransactionRunner` faz rollback em `Failure` sem lançar exceção e converte falhas de commit. Retry de e-mail decidido por valor, com ack manual (R-08). Mapeamento estável para RFC 9457. | ✅ |
| **IV. TDD** | Ciclo Red → Green → Refactor por tarefa. Unitários de domínio e casos de uso; integração com Testcontainers e WireMock; contrato de todos os `code` (SC-010); concorrência (SC-005) e isolamento (SC-004). CI bloqueia merge com teste falhando (R-12). | ✅ |
| **V. Simplicidade** | Cada componente está ligado a um requisito na tabela "Justificativa de componentes" abaixo. Os que não decorrem diretamente da spec estão em Complexity Tracking. | ⚠️ ver Complexity Tracking |
| **VI. Agnosticismo Tecnológico** | Tecnologias escolhidas e justificadas só aqui. `domain`/`application` não têm dependência de framework; Mercado Bitcoin, Brevo, Redis e RabbitMQ ficam atrás de portas. | ✅ |

### Engineering Standards

| Padrão | Atendimento |
|--------|-------------|
| SOLID / Clean Architecture | um caso de uso por classe; portas enxutas por necessidade (data-model §5); dependência sempre de abstrações |
| Separação de responsabilidades | validação de formato (`api`), invariantes (value objects), regra (`domain`/`application`), persistência (`infrastructure`), apresentação (DTOs em `api`) |
| Semântica HTTP | `POST` para cadastro, login e ações financeiras (`201`); `GET` sem efeito colateral; status 400/401/409/422/503 com Problem Details padronizado (contrato) |
| Segurança e dados sensíveis | BCrypt 12; JWT HS256 com segredo em variável de ambiente; rotas públicas só cadastro e login; logs JSON com mascaramento; respostas sem detalhes internos; `.env.example` |
| Integridade de valores financeiros | `Money` (escala 2), `BtcAmount` (satoshi `Long`), `QuotePrice` (escala da fonte); `RoundingMode` explícito em toda divisão; sem `double` |
| Padronização | código em inglês; classes PascalCase (substantivos), métodos camelCase (verbos), booleanos `is`/`has`; pacote raiz `com.kotlinbitcoins`; ktlint + detekt no `./gradlew check`; estrutura de diretórios abaixo |

### Justificativa de componentes (ligação com a spec)

| Componente | Requisito atendido | Por quê |
|------------|--------------------|---------|
| **Cache Redis da cotação** | FR-012 (janela de 60 s), FR-012a, SC-006 | Guarda a última cotação válida com seu instante, para servir o fallback de 60 s quando a fonte falha. Com TTL curto de reuso (10 s), as consultas não pagam uma chamada externa cada (SC-006). Por ser compartilhado, as duas réplicas usam a mesma cotação e a mesma janela. |
| **Transactional Outbox** | FR-030, FR-031 | O evento é gravado na mesma transação da operação: nenhum e-mail sem operação confirmada, e nenhuma operação desfeita por falha de e-mail. |
| **RabbitMQ (fila + retry + DLQ)** | FR-031, SC-007 | Desacopla o envio do caminho da requisição, faz retry com backoff e isola falhas definitivas na DLQ. |
| **Lock pessimista por conta** | FR-010, FR-034, SC-005 | Serializa depósito, compra e resgate da mesma conta: saldo nunca negativo, BTC nunca liquidado duas vezes. |
| **Múltiplas réplicas + Nginx** | SC-006 | Distribui carga e mantém o serviço disponível durante a troca de uma réplica. Exige aplicação stateless. |
| **ShedLock** | FR-027, FR-029 com múltiplas réplicas | Garante uma única execução da captura (uma única chamada à fonte por slot) e do expurgo, mesmo com N réplicas. |
| **Multi-módulo Gradle** | Princípio I | Transforma a regra de dependência em restrição de compilação. |
| **Flyway + `ddl-auto=validate`** | FR-032 (tipos exatos) | Esquema versionado e revisável; o Hibernate só valida, nunca altera o banco. |

**Resultado do gate (pré-Phase 0)**: ✅ PASS. Nenhuma violação de princípio MUST. Os itens de Simplicidade (V) sem ligação direta com um requisito MUST da spec estão justificados em Complexity Tracking.

**Re-check pós-Phase 1**: ✅ PASS. O design (data-model, contrato, quickstart) não introduziu dependência de framework em `domain`/`application`. Todos os erros de FR-035 têm `code` e status no contrato. Rotas sem identificador de outro cliente cumprem FR-033 por construção. Duas decisões de desenho surgiram na Phase 1 e foram registradas: o relay do outbox usa `FOR UPDATE SKIP LOCKED` em vez de ShedLock (menos um ponto de coordenação), e a deduplicação de e-mail usa `outbox_events.sent_at` em vez de Redis (sem infraestrutura extra).

## Project Structure

### Documentation (this feature)

```text
specs/001-bitcoin-investment-api/
├── spec.md
├── plan.md              # este arquivo
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   └── openapi.yaml     # Phase 1
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks — ainda não criado)
```

### Source Code (repository root)

```text
settings.gradle.kts                 # include(domain, application, infrastructure, api, bootstrap)
build.gradle.kts                    # ktlint, detekt, toolchain JDK 21 em todos os módulos
gradle/libs.versions.toml           # version catalog
config/detekt/detekt.yml
.editorconfig                       # regras do ktlint
.env.example
Dockerfile                          # multi-stage: gradle:jdk21 → eclipse-temurin:21-jre, não-root
docker-compose.yml                  # nginx, app ×2, postgres, redis, rabbitmq, mailpit, quote-stub (profile "stub")
docker/
├── nginx/nginx.conf
└── quote-stub/mappings/ticker.json
.github/workflows/ci.yml            # ./gradlew check + docker build

domain/                             # Kotlin puro (sem dependências além da stdlib)
└── src/
    ├── main/kotlin/com/kotlinbitcoins/domain/
    │   ├── shared/                 # Outcome, Money, PositiveMoney, BtcAmount, QuotePrice, ids
    │   ├── account/                # Account, Email, AccountError
    │   ├── quote/                  # Quote, QuoteSnapshot, PriceVariation
    │   ├── investment/             # Investment, InvestmentStatus, BtcConversion, RedemptionPlanner, RedemptionPlan
    │   └── ledger/                 # Transaction, TransactionType
    └── test/kotlin/...             # unitários + propriedades de conservação

application/                        # Kotlin puro; depende só de domain
└── src/
    ├── main/kotlin/com/kotlinbitcoins/application/
    │   ├── port/                   # AccountRepository, InvestmentRepository, TransactionRepository,
    │   │                           # QuoteProvider, QuoteCache, QuoteHistoryRepository, NotificationPublisher,
    │   │                           # EmailSender, PasswordHasher, TokenIssuer, TransactionRunner, Clock,
    │   │                           # InfrastructureFailure (+ marcador InfrastructureUnavailable)
    │   ├── account/                # RegisterAccount, AuthenticateAccount, GetBalance, DepositFunds
    │   ├── quote/                  # QuoteService, GetCurrentQuote, CaptureQuoteSnapshot, PurgeQuoteHistory, GetQuoteHistory
    │   ├── investment/             # PurchaseBitcoin, RedeemInvestments, GetInvestmentPosition
    │   ├── ledger/                 # GetStatement, GetDailyVolume, StatementPeriod
    │   └── notification/           # NotificationEvent, SendEmailNotification
    └── test/kotlin/...             # casos de uso com MockK

infrastructure/                     # adaptadores; depende de application
└── src/
    ├── main/kotlin/com/kotlinbitcoins/infrastructure/
    │   ├── persistence/            # *JpaEntity, Spring Data repos, mappers, adaptadores de porta, JpaTransactionRunner
    │   ├── quote/                  # MercadoBitcoinQuoteProvider (RestClient), RedisQuoteCache
    │   ├── messaging/              # OutboxNotificationPublisher, OutboxRelayJob, EmailNotificationListener, RabbitConfig
    │   ├── email/                  # BrevoEmailSender, SmtpEmailSender, templates pt-BR
    │   ├── security/               # BCryptPasswordHasher, JwtTokenIssuer
    │   ├── scheduling/             # CaptureQuoteSnapshotJob, PurgeQuoteHistoryJob (ShedLock)
    │   └── time/                   # SystemClock
    ├── main/resources/db/migration/  # V1__ … V6__ (data-model §4)
    └── test/kotlin/...             # Testcontainers + WireMock

api/                                # interface HTTP; depende de application
└── src/
    ├── main/kotlin/com/kotlinbitcoins/api/
    │   ├── account/ quote/ investment/ ledger/ market/   # controllers + DTOs por recurso
    │   ├── error/                  # ProblemDetailsMapper (erro tipado → RFC 9457), handlers de validação/segurança
    │   ├── format/                 # parsing estrito de MoneyInput, formatação de BTC/Money/Instant (America/Sao_Paulo)
    │   └── web/                    # CorrelationIdFilter, CurrentAccount (sub do JWT)
    └── test/kotlin/...             # testes de controller (@WebMvcTest + MockK)

bootstrap/                          # composition root; depende de todos
└── src/
    ├── main/kotlin/com/kotlinbitcoins/bootstrap/
    │   ├── KotlinBitcoinsApplication.kt
    │   ├── UseCaseConfiguration.kt     # @Bean de cada caso de uso
    │   ├── AdapterConfiguration.kt     # @Bean de cada adaptador, Clock, ZoneId de negócio
    │   ├── SecurityConfiguration.kt    # resource server JWT, rotas públicas, entry point Problem Details
    │   ├── SchedulingConfiguration.kt  # ShedLock
    │   └── OpenApiConfiguration.kt     # springdoc (bearerAuth, servidor /api/v1)
    ├── main/resources/application.yml (+ -dev, -test, -prod, logback-spring.xml)
    └── test/kotlin/...             # contrato (MockMvc + Testcontainers), E2E, concorrência, isolamento
```

**Structure Decision**: projeto único multi-módulo Gradle, sem frontend. Os módulos correspondem às camadas do Princípio I. Os testes ficam no módulo que contém o código testado; os testes que exercitam a aplicação montada (contrato, E2E, concorrência, isolamento) ficam em `bootstrap`, o único módulo que monta o contexto completo.

## Complexity Tracking

> Itens que **não** decorrem diretamente de um requisito MUST da spec ou que adicionam peças além do mínimo. Nenhum viola um princípio MUST da constituição; estão aqui para cumprir o Princípio V ("complexidade adicional MUST ser justificada no Plan").

| Item | Why Needed | Simpler Alternative Rejected Because |
|------|------------|--------------------------------------|
| **2 réplicas + Nginx** no compose | Decisão de arquitetura do responsável técnico: demonstrar que a aplicação é stateless e escala horizontalmente. A spec não exige mais de uma instância; SC-006 (p95 ≤ 2 s) provavelmente é atingível com uma. | Uma instância é mais simples, mas não exercitaria a coordenação entre réplicas (cache compartilhado, ShedLock, `SKIP LOCKED`), que é parte do objetivo do projeto. **É a raiz de três itens abaixo**: com uma réplica, ShedLock e o cache compartilhado deixariam de ser necessários. |
| **Redis** como cache da cotação | FR-012 exige guardar a última cotação válida por 60 s. Com várias réplicas, esse estado precisa ser compartilhado para que todas apliquem a mesma janela e a mesma cotação. | Cache em memória por réplica: cada réplica teria cotações e janelas diferentes, e o cliente poderia ver preços divergentes entre requisições. Tabela no Postgres: viável, mas acrescenta escrita no banco a cada atualização de cotação e não traz TTL nativo. |
| **RabbitMQ** além do outbox | FR-031 é atendido só com o outbox. A fila acrescenta: entrega atrasada durável sem polling (fila de retry com TTL por mensagem e dead-letter de volta à fila principal), DLQ inspecionável e reprocessável pelo painel de gestão, consumo paralelo entre réplicas com ack manual e desacoplamento do envio em relação ao banco. O listener só decide, por valor, se confirma, reagenda ou manda para a DLQ (R-08); o agendamento e a durabilidade do atraso ficam com o broker. Ajuda SC-007. | Outbox + relay que chama o `EmailSender` diretamente, com tentativas, próximo horário e estado de falha na própria tabela `outbox_events`: atende FR-030/FR-031 com uma peça a menos. A decisão de retry seria igualmente por valor, mas o atraso viraria polling na tabela e a DLQ uma consulta SQL. Mantido por decisão do responsável técnico, para exercitar mensageria entre réplicas; é o primeiro candidato a remoção se a simplicidade for priorizada. |
| **ShedLock** nos jobs de histórico e expurgo | Com N réplicas, sem lock, cada réplica chamaria a fonte a cada slot (N chamadas externas) e rodaria o expurgo. | A constraint única em `quote_history.slot_at` já torna a captura idempotente, e o expurgo é idempotente por natureza, então o ShedLock é otimização e não correção. Mantido para não multiplicar chamadas a uma fonte externa instável (R-06). |
| **Serviço `quote-stub`** (WireMock) no compose, profile `stub` | Permite validar o fluxo completo localmente enquanto a API do Mercado Bitcoin estiver fora do ar (521 em 2026-11-09). | Depender da fonte real tornaria o quickstart não reproduzível. Fica isolado num profile e não existe em produção. |
| **5 módulos Gradle** | Princípio I (verificação da regra de dependência pelo compilador). | Módulo único + ArchUnit: a regra vira teste, que pode ser ignorado ou desativado. Não é violação; listado só pela quantidade de módulos. |
