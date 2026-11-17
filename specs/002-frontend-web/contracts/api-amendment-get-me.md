# Emenda na 001: `GET /me` (FR-005)

**Status**: **APLICADA** em 2026-11-15 (T122–T126).

A operação agora faz parte do contrato-fonte: [`GET /me` em `specs/001-bitcoin-investment-api/contracts/openapi.yaml`](../../001-bitcoin-investment-api/contracts/openapi.yaml) (schema `ProfileResponse {name, email}`, respostas 200 e 401), com o requisito **FR-036** e o cenário 3 da US3 em [`specs/001-bitcoin-investment-api/spec.md`](../../001-bitcoin-investment-api/spec.md).

A fixture `web/contract-fixtures/success/me-200.json` não tem mais `pendingAmendment` e é validada pelo `WebClientFixturesContractTest` como as demais.
