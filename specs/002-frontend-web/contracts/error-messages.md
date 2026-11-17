# Contrato de UI: erros → mensagens (FR-040, FR-041)

**Feature**: `002-frontend-web` | Implementação: `web/ui/.../shared/ErrorMessages.kt` + `composeResources/values/strings.xml` | Tipos: [data-model §3](../data-model.md#3-erros-tipados-coreerror)

Os `code` vêm do contrato autoritativo da 001 (`Problem.code` em [openapi.yaml](../../001-bitcoin-investment-api/contracts/openapi.yaml)). Esta tabela **não** os redefine. Ela só mapeia cada um para o comportamento do cliente. O `detail` do servidor nunca é exibido (SC-003).

## Mapeamento

| `code` (status) | `ApiError` | Mensagem (pt-BR) | Onde aparece | Ação oferecida |
|-----------------|-----------|------------------|--------------|----------------|
| `VALIDATION_ERROR` (400) | `Validation(fieldErrors)` | por campo: `errors[].message`; resumo: "Revise os campos destacados." | junto a cada campo + resumo no topo do formulário | foco no primeiro campo inválido |
| `INVALID_AMOUNT` (400) | `InvalidAmount` | "Informe um valor maior que zero, com no máximo 2 casas decimais." Na **compra**, acrescenta: "O valor também pode ser pequeno demais para comprar 1 satoshi (0,00000001 BTC)." | junto ao campo de valor | volta à edição |
| `EMAIL_ALREADY_IN_USE` (409) | `EmailAlreadyInUse` | "Este e-mail já está cadastrado." | junto ao campo e-mail | link "Entrar" (`#/entrar`). Mantém nome e e-mail e limpa a senha |
| `INVALID_CREDENTIALS` (401) | `InvalidCredentials` | "E-mail ou senha incorretos." | região de alerta do login | — |
| `UNAUTHORIZED` (401) | `Unauthorized` | (fluxo) "Sua sessão expirou. Entre novamente para continuar." | banner no login | volta à página após login (FR-004) |
| `INSUFFICIENT_BALANCE` (422) | `InsufficientBalance` | "Saldo insuficiente para esta compra." + "Saldo disponível: R$ X" | bloco de erro da compra | link "Depositar" |
| `REDEMPTION_EXCEEDS_AVAILABLE` (422) | `RedemptionExceedsAvailable` | "O valor solicitado é maior que o disponível para resgate." + "Disponível agora: R$ X" | bloco de erro do resgate | "Resgatar tudo" reabilitado |
| `INVALID_DATE_RANGE` (422) | `InvalidDateRange` | "Intervalo de datas inválido. A data inicial deve ser igual ou anterior à final." | junto aos campos de data | extrato anterior mantido |
| `QUOTE_UNAVAILABLE` (503) | `QuoteUnavailable` | "Cotação indisponível no momento. Tente novamente em instantes." (em blocos de exibição: "Cotação indisponível no momento") | bloco afetado / erro da operação | "Tentar de novo" |
| `SERVICE_UNAVAILABLE` (503) | `ServiceUnavailable` | "Serviço temporariamente indisponível." | bloco/tela afetada | "Tentar de novo" |
| `NOT_FOUND` (404) | `Unexpected` | "Algo deu errado. Tente novamente." | bloco/tela afetada | "Tentar de novo" |
| `INTERNAL_ERROR` (500) | `Unexpected` | idem | idem | idem |
| code desconhecido / corpo ou status fora do contrato | `Unexpected` | idem | idem | idem |
| sem conexão (leitura, login, cadastro) | `NoConnection` | "Não foi possível conectar. Verifique sua internet e tente novamente." | bloco/tela afetada | "Tentar de novo" |
| queda após envio de depósito/compra/resgate | `OperationError.Uncertain` | "Não foi possível confirmar se a operação foi concluída. Confira seu saldo e o extrato antes de tentar de novo." | resultado da operação (região de alerta) | links "Ver saldo" (Painel) e "Ver extrato". **Sem** "Tentar de novo" |

## Validações locais (antes do envio, mesmas mensagens)

| Campo | Regra (W-11) | Mensagem |
|-------|--------------|----------|
| Nome | vazio após `trim` / > 200 caracteres | "Informe seu nome." / "O nome pode ter no máximo 200 caracteres." |
| E-mail | formato inválido / > 320 | "Informe um e-mail válido." |
| Senha | < 8 caracteres / > 72 bytes UTF-8 | "A senha deve ter pelo menos 8 caracteres." / "A senha é longa demais. Use menos caracteres ou caracteres mais simples." |
| Valor (R$) | vazio, zero, negativo, não numérico, > 2 casas, formato fora do padrão | mensagem de `INVALID_AMOUNT` (sem o complemento de satoshi, que só vem após recusa da API) |
| Datas do extrato | só uma preenchida, data inválida, inicial > final | mensagem de `INVALID_DATE_RANGE` |

## Regras de apresentação (FR-046, FR-047)

- Erro de campo: texto abaixo do campo, ícone de erro e estado inválido nas semantics (`error(msg)`), lido ao focar o campo (critério A4 do W-00).
- Erros de formulário/operação: região de alerta (`DomAnnouncer.assertive`) e foco no título do bloco de erro.
- Erros de bloco em telas com auto-refresh: anunciados só na transição (por exemplo, disponível → indisponível), nunca a cada ciclo.
- Teste `ErrorMessagesTest` (`:ui`): todo subtipo de `ApiError`/`OperationError` tem mensagem não vazia, e nenhuma contém `code`, `HTTP`, `exception`, `null`, `stack`, `{` ou o nome do enum.
