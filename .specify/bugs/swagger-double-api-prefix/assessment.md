# Bug Assessment: Swagger UI chama `/api/v1/api/v1/...` e recebe 401 em rota pública

- **Slug**: swagger-double-api-prefix
- **Created**: 2026-11-10
- **Source**: pasted text ("401 Unauthorized") + captura de tela do Swagger UI
- **Verdict**: valid (já corrigido no código pelo commit `47865fe`; falta teste de regressão)
- **Severity**: medium

## Report (verbatim or summarized)

Entrada do usuário: `401 Unauthorized`. A captura de tela do Swagger UI mostra:

```
curl -X 'POST' 'http://localhost:8080/api/v1/api/v1/accounts' \
  -H 'Content-Type: application/json' \
  -d '{ "name": "kobori", "email": "<redigido>", "password": "<redigido>" }'

401 Undocumented
{ "type": "about:blank", "title": "Unauthorized", "status": 401,
  "detail": "Autenticação necessária.", "code": "UNAUTHORIZED",
  "correlationId": "dcee371c-1905-4c53-9e30-01ae4948600c" }
www-authenticate: Bearer
date: Tue, 10 Nov 2026 21:06:19 GMT   (= 18:06 em -03:00)
```

## Symptom

Ao executar "Cadastrar conta" pelo Swagger UI, a requisição vai para `/api/v1/api/v1/accounts` (prefixo duplicado) e a API responde `401 UNAUTHORIZED`. O esperado era `POST /api/v1/accounts`, rota pública, retornando `201` (ou `400`/`409` em erros de validação/duplicidade).

## Reproduction

1. Subir a stack (`docker compose up`) com uma imagem construída **antes** do commit `47865fe`.
2. Abrir `http://localhost:8080/swagger-ui.html`.
3. Executar `POST /accounts` com qualquer corpo.
4. Observar a URL `/api/v1/api/v1/accounts` e o `401`.

Verificação feita durante a avaliação (stack local em execução, containers `app` recriados às 18:13 -03, depois da captura das 18:06 -03):

- `GET /v3/api-docs` → `servers: [{url: /api/v1}]`, paths relativos (`/accounts`, `/auth/login`, `/me/...`) — **comportamento já corrigido**.
- `POST /api/v1/accounts` com `{}` → `400` (rota pública alcançada, validação atua).
- `POST /api/v1/api/v1/accounts` → `401` (reproduz exatamente o sintoma relatado quando a URL duplicada é usada).

## Suspected Code Paths

- `bootstrap/src/main/kotlin/com/kotlinbitcoins/bootstrap/OpenApiConfiguration.kt:27` — declara `servers: /api/v1`, enquanto os controllers já mapeiam caminhos absolutos com `/api/v1` (ex.: `api/src/main/kotlin/com/kotlinbitcoins/api/account/AccountController.kt:44` `@PostMapping("/api/v1/accounts")`). O Swagger UI concatena server URL + path → `/api/v1/api/v1/accounts`.
- `bootstrap/src/main/kotlin/com/kotlinbitcoins/bootstrap/OpenApiConfiguration.kt:39-46` — `relativePathsCustomizer` (adicionado em `47865fe`) remove o prefixo dos paths gerados; é a correção.
- `bootstrap/src/main/kotlin/com/kotlinbitcoins/bootstrap/SecurityConfiguration.kt:82-91` — `permitAll` só para `POST /api/v1/accounts` e `/api/v1/auth/login`; qualquer outra URL (inclusive a duplicada, que nem existe) cai em `anyRequest().authenticated()` → entry point devolve `401` antes de chegar ao `404`. Explica por que o erro aparece como 401 e não 404.
- `bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/contract/OpenApiConformanceTest.kt:88,205` — o teste verifica que `servers` contém `/api/v1`, mas `normalized()` remove o prefixo dos paths gerados, então **não detecta** a combinação server + path absoluto. Por isso o bug passou pela suíte.

## Root Cause Hypothesis

O documento OpenAPI gerado pelo springdoc declarava `servers: /api/v1` e, ao mesmo tempo, paths absolutos já prefixados com `/api/v1` (vindos dos `@*Mapping` dos controllers). O Swagger UI monta a URL como `server + path`, gerando `/api/v1/api/v1/accounts`. Essa rota não é coberta pelo `permitAll`, então o Spring Security a trata como protegida e responde `401` sem token. Confiança: **alta** — reproduzido diretamente com `curl` na URL duplicada, e o diff de `47865fe` ("Correção de autenticação") trata exatamente esse caso. O horário da captura (18:06 -03) é anterior à recriação dos containers `app` (18:13 -03), que já servem o documento corrigido.

## Proposed Remediation

**Preferred**: Manter a correção existente (`relativePathsCustomizer` em `OpenApiConfiguration.kt`) e fechar a lacuna de testes: adicionar asserção em `OpenApiConformanceTest` de que nenhum path do documento gerado começa com o server URL (`/api/v1`), e/ou que `server.url + path` de `POST /accounts` resulta em `/api/v1/accounts`. Para o ambiente local, reconstruir a imagem (`docker compose up --build`) e recarregar o Swagger UI sem cache.

**Alternatives**:
- Remover `.servers(...)` (servidor padrão = raiz) e manter os paths absolutos — mais simples, mas diverge de `specs/001-bitcoin-investment-api/contracts/openapi.yaml` (que declara `servers: /api/v1` e paths relativos) e quebraria a asserção da linha 88 do teste de conformidade.
- Mover o prefixo para os controllers via `@RequestMapping("/api/v1")` em nível de classe ou `spring.mvc.servlet.path`/`server.servlet.context-path=/api/v1` — o springdoc passaria a gerar o server corretamente; porém altera matchers de segurança, actuator, testes e nginx. Custo alto para o benefício.

**Files likely to change**:
- `bootstrap/src/test/kotlin/com/kotlinbitcoins/bootstrap/contract/OpenApiConformanceTest.kt`
- (nenhuma mudança de produção necessária, salvo se escolhida uma alternativa)

**Tests to add or update**:
- `OpenApiConformanceTest`: "paths gerados são relativos ao server" — falha se algum path começar com `/api/v1`.
- Opcional: teste que monta `servers[0].url + "/accounts"` e faz `POST` via MockMvc esperando `201`/`400` (nunca `401`).

## Risks & Considerations

- O `OpenApiCustomizer` recria `Paths` a cada geração; se no futuro houver controllers fora de `/api/v1` (ex.: rotas administrativas), elas ficariam com URL errada no Swagger (`/api/v1/admin/...`). Considerar declarar um server por grupo nesse caso.
- Rotas inexistentes respondem `401` em vez de `404` para clientes não autenticados (efeito de `anyRequest().authenticated()`). É comportamento comum e evita enumeração de rotas, mas dificulta diagnosticar erros de URL como este — vale documentar.
- Swagger UI pode manter o documento antigo em cache do navegador; recarregar com cache limpo após o rebuild.
- Sem risco de dados nem de segurança: a API em si sempre funcionou na URL correta; o impacto era apenas na ferramenta de documentação/teste manual.

## Open Questions

- [NEEDS CLARIFICATION: o erro ainda ocorre após `docker compose up --build` e recarregar o Swagger UI sem cache? A verificação local indica que não.]
