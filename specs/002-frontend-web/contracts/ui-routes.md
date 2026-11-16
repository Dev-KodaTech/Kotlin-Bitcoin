# Contrato de UI: rotas, URL e títulos

**Feature**: `002-frontend-web` | Fonte de implementação: `web/core/.../navigation/AppRoute.kt` (W-04, data-model §7)

Este contrato define a interface do cliente exposta à pessoa usuária: endereços (fragmentos), parâmetros, proteção e título de cada página. Ele é a referência dos testes de `AppRoute.parse`/`toFragment` (`:core`) e dos E2E de navegação (`:e2e`).

## Rotas

| Rota (`AppRoute`) | Fragmento | Parâmetros | Protegida | `document.title` | `h1` | Destino na navegação (FR-009) |
|-------------------|-----------|-----------|-----------|------------------|------|-------------------------------|
| `Login(returnTo?)` | `#/entrar` | `voltar` = fragmento protegido codificado (opcional) | não | "Entrar · Investimento em Bitcoins" | "Entrar" | — |
| `Register` | `#/cadastro` | — | não | "Criar conta · Investimento em Bitcoins" | "Criar conta" | — |
| `Dashboard` | `#/painel` | — | sim | "Painel · Investimento em Bitcoins" | "Olá, {nome}" / "Olá!" | Painel |
| `Deposit` | `#/depositar` | — | sim | "Depositar · …" | "Depositar" | Depositar |
| `Purchase` | `#/comprar` | — | sim | "Comprar bitcoin · …" | "Comprar bitcoin" | Comprar |
| `Investments` | `#/investimentos` | — | sim | "Meus investimentos · …" | "Meus investimentos" | Meus investimentos |
| `Redemption` | `#/resgatar` | — | sim | "Resgatar · …" | "Resgatar" | Resgatar |
| `Statement(filter)` | `#/extrato` ou `#/extrato?de=AAAA-MM-DD&ate=AAAA-MM-DD` | `de`, `ate` (ambos ou nenhum) | sim | "Extrato · …" | "Extrato" | Extrato |
| `Market` | `#/mercado` | — | sim | "Mercado · …" | "Mercado" | Mercado |

As etapas internas (revisão, resultado, erro) **não** têm rota própria (W-04) e **não** alteram o fragmento. Nessas etapas o título recebe um sufixo ("Revisar compra · …", "Compra realizada · …") para atender 2.4.2 e anunciar a mudança de contexto.

## Regras de resolução

1. Fragmento vazio, `#`, `#/` → `Dashboard` (passa pela guarda).
2. Fragmento desconhecido → `Dashboard`.
3. `#/extrato` com só `de` ou só `ate`, ou com data inválida → `Statement(Default)` com a mensagem de intervalo inválido junto aos campos, preenchidos com o que veio. Não há erro silencioso.
4. Rota protegida sem sessão → `Login(returnTo = rota solicitada)`.
5. `Login`/`Register` com sessão ativa → `Dashboard`.
6. `voltar` só é aceito se `AppRoute.parse(decode(voltar))` for uma rota **protegida**. Exemplos recusados (→ `Dashboard`): `https://evil.example`, `//evil.example`, `javascript:alert(1)`, `/entrar`, `../`, valor duplamente codificado que não seja rota, string vazia.
7. Parâmetros extras desconhecidos são ignorados. A rota é normalizada no fragmento (por `replace`, sem nova entrada de histórico).

## Histórico do navegador

- Navegar entre destinos cria entradas de histórico. Voltar/avançar restaura rota e filtro (via `HashRouter` do `:app`; research W-04). Só uma entrada nova no topo cria entrada de histórico; toda substituição do back stack usa `history.replaceState`.
- Aplicar ou limpar filtro do extrato **substitui** a entrada atual (o filtro aplicado pode ser compartilhado/recarregado sem encher o histórico).
- Login → `returnTo`/`Dashboard` substitui a entrada do login (voltar não retorna ao formulário de login).
- "Sair" limpa o back stack do app. Uma entrada antiga do navegador que aponte para rota protegida passa pela guarda (regra 4).

## Mensagens associadas à navegação

| Situação | Onde | Texto |
|----------|------|-------|
| Sessão expirada | banner no login (região de alerta) | "Sua sessão expirou. Entre novamente para continuar." |
| Conta criada mas login automático falhou | banner no login | "Conta criada. Entre com seu e-mail e senha." |
| Retorno após login | — | foco no `h1` da página de destino |
