# Checklist de acessibilidade da release (SC-007)

**Feature**: `002-frontend-web` | **Origem**: [quickstart.md §6](../quickstart.md#6-checklist-manual-de-acessibilidade-sc-007-antes-de-cada-release) | **Critérios**: A1–A12 de [research.md W-00](../research.md#w-00-gate-de-acessibilidade--spike-gono-go-obrigatório-bloqueia-a-implementação)

Matriz do W-00 repetida sobre o **app completo** (não sobre o spike), antes de cada release. A parte automatizável já roda no E2E (`a11y/AxeAllScreensE2ETest`, `a11y/KeyboardOnlyE2ETest`, `layout/*`). Este checklist cobre o que só uma pessoa com leitor de tela, teclado e navegador reais confirma: anúncios, ordem de leitura, foco visível desenhado no canvas, contraste percebido e teclado virtual.

## Registro da execução

| Campo | Valor |
|-------|-------|
| Versão/commit avaliado | |
| Data | |
| Responsável | |
| Navegadores e leitores (versões exatas) | |
| Evidências (gravações, notas) | |
| Veredito | ☐ aprovado ☐ reprovado |

## Fluxos a concluir em cada combinação

Em cada combinação abaixo, concluir: **cadastro**, **login**, **sair**, **depósito**, **compra** (revisão e confirmação), **resgate** (revisão, "Resgatar tudo" e confirmação), **meus investimentos**, **extrato** (filtro por datas e filtro inválido) e **mercado** (gráfico, resumo e "Ver dados em tabela").

Para cada fluxo, verificar:

- o título da página e o h1 são anunciados ao chegar, e o foco vai para o h1 (FR-046);
- todo controle é alcançável e acionável, com foco visível e sem armadilha (A1, A2);
- campos anunciam rótulo, valor e erro; após envio inválido o foco vai ao primeiro campo inválido (A3, A5);
- resultado de operação e mudanças de estado relevantes são anunciados uma única vez, e a atualização automática de 10 s não gera anúncios (FR-047, A4);
- o item atual da navegação é anunciado como "…, página atual" (FR-009);
- cartões de investimentos e do extrato são lidos como uma frase completa (A7);
- nenhum texto em inglês é anunciado (FR-048).

## Matriz

- [ ] C1 — NVDA + Firefox (Windows): todos os fluxos concluídos, critérios acima atendidos
- [ ] C2 — NVDA + Chrome (Windows): todos os fluxos concluídos, critérios acima atendidos
- [ ] C3 — VoiceOver + Safari (macOS): todos os fluxos concluídos, critérios acima atendidos
- [ ] C4 — VoiceOver + Chrome (macOS): todos os fluxos concluídos, critérios acima atendidos
- [ ] C5 — VoiceOver + Safari (iOS): todos os fluxos concluídos por gestos, critérios acima atendidos
- [ ] C6 — Só teclado em Chrome, Firefox e Safari: todos os fluxos com Tab, Shift+Tab, Enter, Espaço e Esc (Esc fecha o menu em telas estreitas); foco sempre visível
- [ ] C7 — Zoom de 200% em janela de 1280 px (Chrome, Firefox e Safari): nenhum conteúdo cortado, nenhuma rolagem horizontal, navegação em trilho (SC-008)
- [ ] C8 — Teclado virtual (iOS Safari e Android Chrome): digitação, colar e corretor nos campos; teclado decimal no campo de valor e de e-mail no campo de e-mail (A11)
- [ ] C9 — Tema escuro do sistema: todas as telas legíveis, foco visível e gráfico do mercado distinguível (linhas e marcadores)
- [ ] C10 — `prefers-reduced-motion: reduce`: nenhuma transição entre telas, nenhum indicador animado, "Carregando…" estático (FR-045)

## Pendências encontradas

| # | Combinação | Tela/fluxo | Descrição | Severidade | Tratamento |
|---|------------|------------|-----------|------------|------------|
| | | | | | |
