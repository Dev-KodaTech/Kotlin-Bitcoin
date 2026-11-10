# Kotlin-Bitcoin

API REST para investir em bitcoins com saldo fictício em reais: o cliente cria uma conta, deposita, compra e resgata BTC pela cotação do Mercado Bitcoin, acompanha a posição e consulta extrato, volume diário e histórico de cotação.

[![CI](https://github.com/Dev-KodaTech/Kotlin-Bitcoin/actions/workflows/ci.yml/badge.svg)](https://github.com/Dev-KodaTech/Kotlin-Bitcoin/actions/workflows/ci.yml)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![JDK](https://img.shields.io/badge/JDK-21-orange)

[Funcionalidades](#funcionalidades) • [Início rápido](#início-rápido) • [Endpoints](#endpoints) • [Arquitetura](#arquitetura) • [Desenvolvimento](#desenvolvimento)

> [!NOTE]
> Nenhuma movimentação financeira real acontece. Depósitos e resgates só alteram o saldo interno da conta.

## Funcionalidades

- Cadastro e login com JWT (HS256); todas as demais rotas exigem token.
- Depósito em reais, com e-mail de confirmação.
- Compra de BTC pela cotação de venda e resgate pela cotação de compra.
- Liquidação FIFO no resgate: o investimento mais antigo é liquidado primeiro e, se a liquidação for parcial, o BTC residual é reinvestido preservando a data original.
- Posição dos investimentos ativos, com valor atual e variação percentual.
- Extrato por período, volume diário de BTC comprado e vendido (todos os clientes) e histórico das últimas 24 h em pontos de 10 min.
- Precisão exata: R$ em centavos, BTC em satoshis, arredondamento explícito em cada conversão.
- Erros no formato Problem Details, com `code` estável e `correlationId`.

## Início rápido

### Pré-requisitos

- JDK 21
- Docker 24+ com Docker Compose v2
- `curl` e `jq` (para o roteiro de exemplo)

### Subir o ambiente

1. Copie as variáveis de ambiente e preencha:

   ```bash
   cp .env.example .env
   ```

   | Variável | Descrição |
   |----------|-----------|
   | `JWT_SECRET` | Segredo HS256 com pelo menos 32 bytes (`openssl rand -base64 48`) |
   | `DB_USER`, `DB_PASSWORD` | Credenciais do PostgreSQL |
   | `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | Credenciais do RabbitMQ |
   | `MAIL_FROM` | Remetente dos e-mails |
   | `QUOTE_PROVIDER_URL` | Fonte de cotação (padrão: Mercado Bitcoin) |
   | `BREVO_API_KEY` | Envio de e-mails no perfil `prod`; em `dev` os e-mails vão para o Mailpit |

2. Suba tudo com o stub de cotação (WireMock):

   ```bash
   docker compose --profile stub up -d --build
   ```

   Com o stub, defina `QUOTE_PROVIDER_URL=http://quote-stub:8080/api/BTC/ticker/` no `.env`. Ele começa com compra e venda a R$ 400.000,00.

> [!TIP]
> Sem o perfil `stub`, a aplicação consulta o Mercado Bitcoin de verdade. Se a fonte estiver fora do ar, cotação, compra, posição e resgate respondem `503 QUOTE_UNAVAILABLE`.

| Serviço | URL |
|---------|-----|
| API (via Nginx, 2 réplicas) | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Mailpit (e-mails de dev) | http://localhost:8025 |
| RabbitMQ management | http://localhost:15672 |

### Exemplo de uso

```bash
API=http://localhost:8080/api/v1

# Cadastro e login
curl -s -X POST $API/accounts -H 'Content-Type: application/json' \
  -d '{"name":"Ana","email":"ana@example.com","password":"senha-segura"}'

TOKEN=$(curl -s -X POST $API/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"ana@example.com","password":"senha-segura"}' | jq -r .accessToken)

# Depósito, compra e posição
curl -s -X POST $API/me/deposits -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"amount":"1000.00"}'

curl -s -X POST $API/me/purchases -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"amount":"400.00"}'

curl -s $API/me/investments -H "Authorization: Bearer $TOKEN" | jq
```

O roteiro completo de validação ponta a ponta (20 passos) está em [quickstart.md](specs/001-bitcoin-investment-api/quickstart.md).

Para encerrar e limpar os volumes:

```bash
docker compose --profile stub down -v
```

## Endpoints

Prefixo `/api/v1`. O contrato completo está em [openapi.yaml](specs/001-bitcoin-investment-api/contracts/openapi.yaml).

| Método | Rota | Descrição | Auth |
|--------|------|-----------|:----:|
| `POST` | `/accounts` | Cadastrar conta | - |
| `POST` | `/auth/login` | Autenticar e obter token | - |
| `GET` | `/me/balance` | Saldo disponível em R$ | sim |
| `POST` | `/me/deposits` | Depositar reais | sim |
| `GET` | `/quotes/current` | Cotação atual do BTC | sim |
| `GET` | `/quotes/history` | Últimas 24 h em pontos de 10 min | sim |
| `POST` | `/me/purchases` | Comprar BTC com um valor em R$ | sim |
| `GET` | `/me/investments` | Posição dos investimentos ativos | sim |
| `POST` | `/me/redemptions` | Resgatar um valor em R$ | sim |
| `GET` | `/me/statement` | Extrato (`from` e `to` opcionais) | sim |
| `GET` | `/market/daily-volume` | Volume do dia, todos os clientes | sim |

> [!NOTE]
> Instantes são devolvidos em ISO-8601 com offset explícito e o "dia corrente" segue o horário de Brasília (`America/Sao_Paulo`).

## Arquitetura

A regra de negócio fica em Kotlin puro, sem dependência de framework. A infraestrutura implementa as portas definidas pela camada de aplicação.

```text
domain          Entidades, valores (R$, BTC, cotação) e regras: conversões, FIFO, reinvestimento
application     Casos de uso, portas e erros como valor
infrastructure  Adapters: JPA/Flyway, Redis, RabbitMQ, e-mail, Mercado Bitcoin, jobs agendados
api             Controllers, DTOs e tratamento de erros
bootstrap       Composition root: Spring Boot, segurança, configuração e testes de ponta a ponta
```

Decisões principais:

- **Concorrência**: uma transação por operação, com lock pessimista na conta.
- **Cotação**: cache compartilhado no Redis. Uma cotação com até 10 s é reutilizada; se a fonte falhar, vale a última cotação válida com até 60 s.
- **E-mails**: Transactional Outbox e RabbitMQ, com retry e DLQ (`notifications.email.dlq`). Falha no e-mail não afeta o depósito.
- **Histórico de cotação**: job agendado a cada 10 min, coordenado entre réplicas com ShedLock.
- **Escala horizontal**: aplicação stateless, 2 réplicas atrás do Nginx no Compose.

Mais detalhes em [plan.md](specs/001-bitcoin-investment-api/plan.md), [data-model.md](specs/001-bitcoin-investment-api/data-model.md) e [spec.md](specs/001-bitcoin-investment-api/spec.md).

**Stack:** Kotlin 2.2, Spring Boot 3.5, PostgreSQL 16, Redis 7, RabbitMQ 3, Flyway, springdoc-openapi, Brevo (e-mail em produção).

## Desenvolvimento

```bash
./gradlew check                       # testes unitários, integração, contrato, ktlint e detekt
./gradlew :bootstrap:bootJar          # gera bootstrap/build/libs/kotlin-bitcoins.jar
docker build -t kotlinbitcoins:local .
```

> [!IMPORTANT]
> Os testes de integração usam Testcontainers (PostgreSQL e RabbitMQ), então o Docker precisa estar rodando.

Os testes cobrem contrato de cada `code` de erro, isolamento entre clientes, concorrência em compras e resgates, invariantes do ledger e mascaramento de dados sensíveis nos logs. O CI ([ci.yml](.github/workflows/ci.yml)) executa `check` e o build da imagem a cada push e pull request.

## Documentação

- [Especificação](specs/001-bitcoin-investment-api/spec.md)
- [Plano de implementação](specs/001-bitcoin-investment-api/plan.md)
- [Modelo de dados](specs/001-bitcoin-investment-api/data-model.md)
- [Decisões de pesquisa](specs/001-bitcoin-investment-api/research.md)
- [Quickstart](specs/001-bitcoin-investment-api/quickstart.md)
