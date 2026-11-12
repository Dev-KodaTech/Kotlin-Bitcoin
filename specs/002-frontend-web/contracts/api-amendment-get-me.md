# Proposta de emenda na 001: `GET /me` (FR-005)

**Status**: **PENDENTE.** Na verificação de 2026-11-11, o contrato `specs/001-bitcoin-investment-api/contracts/openapi.yaml` (branches `frontend`, `main` e `bitcoin/api`) **não** contém `GET /me`.

**Natureza deste arquivo**: proposta de *delta* a ser aplicada **na origem** (spec e contrato da 001, e implementação na API), conforme a Constitution ("Contratos entre Componentes": mudanças no contrato MUST ser feitas na origem). Este arquivo **não** é uma cópia nem uma extensão do contrato. Depois que a emenda for aplicada, ele deve ser reduzido a um link para a operação no `openapi.yaml`.

## Delta proposto para `openapi.yaml`

```yaml
paths:
  /me:
    get:
      tags: [Contas]
      summary: Consultar o perfil do cliente autenticado (FR-0xx da 001; FR-005 da 002)
      responses:
        '200':
          description: Nome e e-mail da conta do token
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ProfileResponse' }
        '401': { $ref: '#/components/responses/Unauthorized' }

components:
  schemas:
    ProfileResponse:
      type: object
      required: [name, email]
      properties:
        name: { type: string }
        email: { type: string, format: email }
```

Requisitos da FR-005: só nome e e-mail; sem `id`, saldo, hash ou qualquer dado sensível; sem identificador de outro cliente (a rota opera sobre a conta do token, como as demais `/me/*`).

## Impacto na 001

- **spec.md**: novo FR (consulta de perfil) e cenário de aceitação.
- **contracts/openapi.yaml**: delta acima. O `OpenApiConformanceTest` passa a exigir o controller.
- **Implementação**: caso de uso de leitura + controller + teste de contrato (`ProfileContractTest`) + isolamento (`AccountIsolationTest`).

## Impacto na 002 enquanto pendente

- Tudo, exceto a saudação com nome, pode ser implementado e testado.
- O `ProfileGateway` usa a fixture `success/me-200.json`. O `WebClientFixturesContractTest` **falha** enquanto a operação não existir no contrato. Por isso a fixture fica marcada no manifest com `"pendingAmendment": "GET /me"` e é pulada (com aviso) até a emenda. A entrega de US2 (painel com saudação) fica **bloqueada** pela emenda (Dependencies da spec).
- Sem a rota, o painel mostra "Olá!" (comportamento de falha previsto em US2-7), então a interface não quebra.
