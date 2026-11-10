# Revisão de conformidade com a Constitution — 001-bitcoin-investment-api

**Data**: 2026-11-10 | **Escopo**: código dos 5 módulos após as Fases 1–13 | **Evidência**: `./gradlew check` (542 testes, 0 falhas, ktlint e detekt limpos), `docker compose --profile stub up` e o roteiro do quickstart.

## Princípios

| # | Princípio | Resultado | Evidência |
|---|-----------|-----------|-----------|
| I | Arquitetura em camadas | ✅ Conforme | `domain` só com a stdlib Kotlin; `application` só depende de `domain` (`application/build.gradle.kts`); `api` não enxerga `infrastructure` (nenhum import de `com.kotlinbitcoins.infrastructure` em `api/src/main`); a composição fica só em `bootstrap`. O compilador garante a regra. |
| II | Lógica de negócio isolada | ✅ Conforme | Controllers só parseiam entrada e mapeiam erros (`MoneyInputParser`, `ProblemDetailsMapper`); regras em `domain` e casos de uso; nenhum `@Transactional` em `application` (porta `TransactionRunner`). |
| III | Erro como valor | ✅ Conforme | Nenhum `throw` nem `catch` em `domain/src/main` e `application/src/main` (só `require` de invariantes); todo erro de operação é `sealed interface` com `ServiceUnavailable`; adaptadores convertem exceções em `Outcome.Failure`. |
| IV | TDD | ✅ Conforme | Cada fase escreveu os testes antes; unitários de domínio/casos de uso sem infraestrutura; contrato para todos os endpoints (`*ContractTest`), conformidade com o OpenAPI (`OpenApiConformanceTest`) e os 10 `code` (`ErrorCodesContractTest`). O defeito achado na Fase 13 (ver abaixo) veio com testes que o reproduzem. |
| V | Simplicidade | ✅ Conforme | Sem funcionalidade fora da spec. As anotações `@ApiResponse` e `ProblemBody` existem só para que o OpenAPI gerado reflita o contrato (R-10). |
| VI | Agnosticismo tecnológico | ✅ Conforme | A Constitution não impõe tecnologia; as escolhas estão no plan e `domain`/`application` as acessam por portas. |

## Padrões de engenharia

| Padrão | Resultado | Evidência |
|--------|-----------|-----------|
| Precisão numérica | ✅ | Nenhum `Double`/`Float` em código de produção (grep em todos os módulos); `Money` escala 2, `BtcAmount` em satoshis, `QuotePrice` ≤ 8 casas, `RoundingMode` explícito. |
| Tempo via porta `Clock` | ✅ | Nenhum `Instant.now()`/`LocalDate.now()` em código de produção fora de `SystemClock`. |
| Semântica HTTP / formato de erro | ✅ | Os 10 `code` do contrato respondem com o status e `application/problem+json` esperados, `detail` em português e sem stack trace (`ErrorCodesContractTest`). |
| Segurança e dados sensíveis | ✅ | `SensitiveDataLoggingTest` (com `log-request-details` ligado) e `outbox_events.payload` sem senha/token; segredos só por ambiente (`.env` fora do git). |
| Padronização | ✅ | ktlint e detekt no `check` (`maxIssues: 0`). |

## Divergências encontradas na Fase 13

| # | Achado | Gravidade | Tratamento |
|---|--------|-----------|------------|
| D1 | `GET /me/investments` e `GET /me/statement` respondiam `200` (lista vazia) para um token válido de conta inexistente; as demais rotas já respondiam `401 UNAUTHORIZED`. Contraria T219/FR-033. | Média | **Corrigido**: `AccountNotFound` em `GetInvestmentPosition` e `GetStatement` → `UNAUTHORIZED`; testes unitários e `AccountIsolationTest`. |
| D2 | O OpenAPI gerado pelo springdoc só declarava `200` e não marcava `amount`/`name`/`email`/`password` como `required` (DTOs de entrada anuláveis para devolver `INVALID_AMOUNT`/`VALIDATION_ERROR` em vez de erro de desserialização). | Média | **Corrigido**: `@ApiResponse` nos 11 endpoints, `@Schema(requiredMode = REQUIRED)` nos DTOs de entrada, `OpenApiConfiguration`; coberto por `OpenApiConformanceTest`. |
| D3 | `docker/quote-stub/mappings/ticker.json` gerava `HTTP 500` no WireMock (`{{now format='unix'}}}}` não é um template válido). | Média | **Corrigido** (`{{now format='unix'}} }}`), verificado no compose. |
| D4 | O texto de research.md R-08 diz "na 5ª falha, publica em `notifications.email.dlq`", mas o código (e T092/T107, com intervalos de 1, 2, 4, 8 e 16 s) faz a mensagem passar por 5 retries e só então ir à DLQ, na 6ª falha. | Baixa | **Sem alteração de código**: o comportamento coincide com os testes e com os intervalos especificados; o texto de R-08 é que está impreciso. |
| D5 | O quickstart diz que `docker compose logs app` mostra JSON com `correlationId`, mas a aplicação não registra uma linha por requisição, então o `correlationId` só aparece em logs emitidos durante a requisição (erros). | Baixa | **Registrado** no quickstart.md; sem tarefa nova (log de acesso não está na spec). |

## Resultado

Nenhuma violação de MUST da Constitution. As divergências D1–D3 foram corrigidas nesta fase; D4 e D5 são imprecisões de documentação registradas.
