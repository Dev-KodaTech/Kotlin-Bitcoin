# Bug Verification: Swagger UI chama `/api/v1/api/v1/...` e recebe 401 em rota pública

- **Slug**: swagger-double-api-prefix
- **Tested**: 2026-11-10
- **Assessment**: ./assessment.md
- **Fix**: ./fix.md
- **Result**: verified

## Summary

O sintoma não se reproduz mais. O documento OpenAPI servido pela stack local declara `servers: /api/v1` com paths relativos, então a URL que o Swagger UI monta para cadastro é `/api/v1/accounts`, e um cadastro real por ela retorna `201`. Os testes de regressão novos passam, e a suíte completa (544 testes), o ktlint e o detekt estão verdes. Não houve regressões.

## Checks Performed

| Check | Command / Action | Result | Notes |
|-------|------------------|--------|-------|
| Reproduction: documento servido | `curl localhost:8080/v3/api-docs` (via nginx) | pass | `servers[0].url=/api/v1`; os 11 paths são relativos (`/accounts`, `/auth/login`, `/me/...`, `/quotes/...`, `/market/daily-volume`) |
| Reproduction: URL montada como o Swagger UI | `POST {server}{path}` = `POST /api/v1/accounts` e `/api/v1/auth/login` com `{}` | pass | `400` em ambos: rota pública alcançada, sem `401` |
| Reproduction: cadastro ponta a ponta | `POST /api/v1/accounts` com payload válido (e-mail descartável `bugtest-…@example.com`) | pass | `HTTP 201` com a conta criada |
| New tests | `./gradlew :bootstrap:test --tests '*OpenApiConformanceTest'` | pass | 6/6; os 2 testes novos falharam no red check do fix (`expected: 400 but was: 401`) |
| Regression suite | `./gradlew test` (todos os módulos) | pass | 544 testes, 0 falhas, 0 erros, 0 ignorados |
| Lint / static analysis | `./gradlew ktlintCheck detekt` | pass | exit 0 |

## Output Excerpts

```
servers: /api/v1
paths:   /me/redemptions /me/purchases /me/deposits /auth/login /accounts /quotes/history ...
POST /api/v1/accounts   -> 400   (corpo vazio)
POST /api/v1/auth/login -> 400   (corpo vazio)
POST /api/v1/accounts   -> HTTP 201  {"id":"c083d75b-…","name":"Bug Test","balance":"0.00",...}

./gradlew test ktlintCheck detekt -> EXIT=0
tests=544 skipped=0 failures=0 errors=0
```

## Residual Risks

- O clique no botão "Execute" do Swagger UI não foi automatizado em navegador. A verificação reproduz a mesma composição `server + path` a partir do documento servido e o teste `server url plus the documented path…` trava esse comportamento.
- Um navegador com o documento antigo em cache ainda pode montar a URL duplicada até recarregar sem cache.
- Ficou no banco local uma conta de teste (`bugtest-1794357380@example.com`, id `c083d75b-6ba1-4959-a917-e94da6235ebf`), criada pela verificação.
- Rotas inexistentes continuam respondendo `401` para clientes não autenticados (escolha de design, fora do escopo deste bug).

## Recommendation

Fechar o bug: verificado ponta a ponta na stack local, com testes de regressão que falham se o prefixo duplicado voltar. Falta commitar a alteração do teste (`OpenApiConformanceTest.kt`) junto com `.specify/bugs/swagger-double-api-prefix/`.
