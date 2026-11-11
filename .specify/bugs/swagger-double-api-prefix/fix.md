# Bug Fix: Swagger UI chama `/api/v1/api/v1/...` e recebe 401 em rota pública

- **Slug**: swagger-double-api-prefix
- **Fixed**: 2026-11-10
- **Assessment**: ./assessment.md
- **Status**: applied

## Summary

A correção de produção já estava no commit `47865fe` (`relativePathsCustomizer` em `OpenApiConfiguration.kt`, que torna os paths relativos ao server `/api/v1`). Este fix fecha a lacuna apontada na avaliação: dois testes de regressão em `OpenApiConformanceTest` que falham se o documento OpenAPI voltar a produzir URLs com o prefixo duplicado.

## Changes

| File | Change | Notes |
|------|--------|-------|
| `bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/contract/OpenApiConformanceTest.kt` | added tests | 2 testes novos + helpers `generatedServerUrl()` / `generatedPaths()`; imports `MediaType` e `post` |

Nenhum arquivo de produção foi alterado.

## Diff Highlights

```kotlin
@Test
fun `paths are relative to the server so Swagger UI does not call the prefix twice`() {
    val server = generatedServerUrl()
    val paths = generatedPaths()

    assertThat(paths).isNotEmpty().noneMatch { it.startsWith(server) }
    assertThat(paths).contains("/accounts", "/auth/login")
}

@Test
fun `server url plus the documented path reaches the public registration route`() {
    val server = generatedServerUrl()
    val accountsPath = generatedPaths().single { it.endsWith("/accounts") }
    val status = mockMvc.post(server + accountsPath) { contentType = APPLICATION_JSON; content = "{}" }
        .andReturn().response.status

    assertThat(status).isEqualTo(400)   // rota pública alcançada (validação), nunca 401
}
```

## Tests Added or Updated

- `OpenApiConformanceTest::paths are relative to the server so Swagger UI does not call the prefix twice`: nenhum path gerado pode começar com o server URL.
- `OpenApiConformanceTest::server url plus the documented path reaches the public registration route`: monta a URL como o Swagger UI faz (`server + path`) e exige que `POST` com corpo vazio chegue à rota pública (`400`), não ao entry point de segurança (`401`).

## Local Verification

- `./gradlew :bootstrap:test --tests '*OpenApiConformanceTest'`: **6 testes, 0 falhas**.
- **Red check:** com a remoção de prefixo desativada temporariamente no `relativePathsCustomizer`, os 2 testes novos **falharam**. O segundo falhou com `expected: 400 but was: 401`, o mesmo sintoma do relato; o primeiro listou `/api/v1/me/redemptions`, `/api/v1/me/purchases`, …. O arquivo de produção foi restaurado com `git checkout` (sem alterações pendentes nele).
- `./gradlew :bootstrap:ktlintCheck :bootstrap:detekt`: sem violações. A primeira versão do teste violava `chain-method-continuation`; isso foi corrigido com os helpers.
- Manual (durante a avaliação): stack local responde `/v3/api-docs` com paths relativos; `POST /api/v1/accounts` → `400`; `POST /api/v1/api/v1/accounts` → `401`.

## Deviations from Assessment

Nenhuma. O alvo opcional "teste que monta `servers[0].url + "/accounts"`" foi implementado esperando `400` com corpo `{}`, para não depender de dados únicos de cadastro.

## Follow-ups

- Reconstruir a imagem local (`docker compose up --build`) e recarregar o Swagger UI sem cache, caso algum ambiente ainda rode uma imagem anterior a `47865fe`.
- Opcional: documentar no README que rotas inexistentes respondem `401` para clientes não autenticados (efeito de `anyRequest().authenticated()`).
- Se surgirem controllers fora de `/api/v1`, revisar o `relativePathsCustomizer` (ele assume que todas as rotas documentadas estão sob o prefixo).
