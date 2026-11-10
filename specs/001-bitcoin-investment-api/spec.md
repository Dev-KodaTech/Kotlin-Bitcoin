# Feature Specification: API de Investimento em Bitcoins

**Feature Branch**: `001-bitcoin-investment-api`

**Created**: 2026-11-09

**Status**: Draft

**Input**: User description: "API de investimento em bitcoins. Permite que uma pessoa crie uma conta, deposite reais (sem movimentação financeira real), compre e venda bitcoins com o saldo da conta, acompanhe a posição dos seus investimentos, consulte cotação, histórico e extrato. Toda operação do cliente exige autenticação, exceto cadastro e login. (descrição completa com user stories P1–P3, regras transversais e fora de escopo fornecida pelo usuário)"

## Glossário

- **Cotação de venda**: preço, em R$, pelo qual a plataforma vende 1 BTC ao cliente. É usada nas **compras** do cliente.
- **Cotação de compra**: preço, em R$, pelo qual a plataforma compra 1 BTC do cliente. É usada nos **resgates (vendas)** do cliente e no cálculo do valor atual dos investimentos.
- **Investimento**: um lote de BTC adquirido pelo cliente em um instante, com valor investido em R$ e cotação registrados.
- **Investimento ativo**: investimento ainda não liquidado.
- **Resgate**: venda de BTC solicitada pelo cliente informando um valor em R$.
- **Reinvestimento**: novo investimento criado automaticamente com o residual de um investimento liquidado parcialmente em um resgate.

## Clarifications

### Session 2026-11-09

- Q: Como calcular o residual reinvestido na liquidação parcial? → A: Preserva a quantidade de BTC não vendida; valor investido = BTC residual × cotação original (nenhum BTC é criado ou destruído).
- Q: Qual a data de compra do reinvestimento? → A: A data de compra original do investimento encerrado (mantém a posição na fila de liquidação).
- Q: Quando a fonte externa de cotação não responde, o sistema deve recusar na hora ou pode usar a última cotação obtida? → A: Usa a última cotação válida se tiver no máximo 60 segundos, para consultas e operações; acima disso, erro de cotação indisponível.
- Q: De onde vêm os preços de compra e de venda? → A: A fonte externa fornece os dois prontos; a plataforma os usa sem alteração (sem margem própria).
- Q: Qual fuso horário define o "dia corrente" e os limites do extrato, e em que formato os instantes são devolvidos? → A: Horário de Brasília (America/Sao_Paulo); instantes em ISO-8601 com deslocamento explícito (ex.: 2026-11-09T08:10:00-03:00).
- Q: Quando um resgate liquida vários investimentos, como ele aparece no extrato? → A: Uma linha de resgate por investimento liquidado (valor em R$, BTC vendido, cotação do momento), todas com o mesmo identificador da solicitação, mais uma linha de reinvestimento quando houver liquidação parcial.
- Q: No resgate, como os valores são arredondados entre R$ e BTC em cada investimento liquidado? → A: Liquidação total credita BTC × cotação arredondado para baixo no centavo; liquidação parcial vende o BTC necessário arredondado para cima no satoshi e credita exatamente o valor que falta; se isso consumir todo o BTC, liquida por inteiro sem reinvestimento; o valor atual total é a soma dos valores arredondados para baixo.
- Q: Com a fonte disponível, uma cotação obtida há poucos segundos pode ser reutilizada sem nova chamada à fonte? → A: Sim. Uma cotação obtida há no máximo 10 segundos é considerada atual e pode ser reutilizada em consultas e operações; a janela de 60 segundos continua valendo só quando a fonte não responde.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cadastro e autenticação (Priority: P1)

A pessoa cria uma conta informando nome, e-mail e senha. Depois faz login com e-mail e senha e recebe um token de acesso, que passa a apresentar em todas as demais operações.

**Why this priority**: sem conta e autenticação nenhuma outra operação pode ser associada a um cliente; é a fundação de todo o produto.

**Independent Test**: cadastrar uma conta, fazer login, usar o token em uma operação protegida (ex.: consulta de saldo) e verificar que a mesma operação sem token é rejeitada.

**Acceptance Scenarios**:

1. **Given** um e-mail ainda não cadastrado, **When** a pessoa se cadastra com nome, e-mail e senha válidos, **Then** a conta é criada com saldo R$ 0,00 e nenhum dado sensível (senha) é devolvido na resposta.
2. **Given** um e-mail já cadastrado, **When** alguém tenta se cadastrar com esse e-mail, **Then** o cadastro é recusado com erro de e-mail já em uso e nenhuma conta é criada.
3. **Given** dados de cadastro inválidos (nome vazio, e-mail malformado ou senha fora da política), **When** a pessoa tenta se cadastrar, **Then** o cadastro é recusado com erro de validação indicando os campos inválidos.
4. **Given** uma conta existente, **When** o cliente faz login com e-mail e senha corretos, **Then** recebe um token de acesso válido.
5. **Given** um e-mail inexistente ou uma senha incorreta, **When** alguém tenta fazer login, **Then** recebe o mesmo erro genérico de credenciais inválidas nos dois casos, sem revelar se o e-mail existe.
6. **Given** uma operação protegida, **When** ela é chamada sem token, com token malformado, adulterado ou expirado, **Then** é recusada com erro de autenticação e nenhum efeito ocorre.

---

### User Story 2 - Depósito em reais (Priority: P1)

O cliente autenticado informa um valor positivo em reais, que é creditado imediatamente no seu saldo. Ele recebe um e-mail com o valor depositado e o depósito passa a constar no extrato com data e hora.

**Why this priority**: é a única forma de o cliente obter saldo para investir.

**Independent Test**: depositar R$ 100,00 em uma conta nova e verificar saldo de R$ 100,00, registro no extrato e e-mail enviado.

**Acceptance Scenarios**:

1. **Given** um cliente autenticado com saldo R$ 50,00, **When** deposita R$ 100,00, **Then** o saldo passa a R$ 150,00 imediatamente.
2. **Given** um depósito realizado, **When** o cliente consulta o extrato, **Then** o depósito aparece com valor, data e hora.
3. **Given** um depósito realizado, **When** a operação é concluída, **Then** o cliente recebe um e-mail informando o valor depositado.
4. **Given** um cliente autenticado, **When** informa valor zero, negativo, ausente, não numérico ou com mais de 2 casas decimais, **Then** o depósito é recusado com erro de valor inválido e o saldo não muda.

---

### User Story 3 - Consulta de saldo (Priority: P1)

O cliente autenticado consulta o saldo disponível em reais da sua conta.

**Why this priority**: o cliente precisa saber quanto pode investir; é pré-requisito prático para compra.

**Independent Test**: consultar o saldo de uma conta nova (R$ 0,00), depositar e consultar novamente.

**Acceptance Scenarios**:

1. **Given** uma conta nova, **When** o cliente consulta o saldo, **Then** recebe R$ 0,00.
2. **Given** depósitos, compras e resgates realizados, **When** o cliente consulta o saldo, **Then** o valor corresponde exatamente a depósitos − compras + resgates.

---

### User Story 4 - Cotação atual (Priority: P1)

O cliente autenticado consulta a cotação atual do bitcoin em reais, com preço de compra, preço de venda e o instante da cotação.

**Why this priority**: o cliente precisa conhecer o preço antes de comprar ou vender; a cotação também é insumo de compra, venda e posição.

**Independent Test**: consultar a cotação e verificar que retorna preço de compra, preço de venda e instante.

**Acceptance Scenarios**:

1. **Given** um cliente autenticado e a fonte de cotação disponível, **When** consulta a cotação, **Then** recebe preço de compra, preço de venda (em R$) e o instante a que a cotação se refere.
2. **Given** a fonte de cotação indisponível e uma última cotação válida obtida há 30 segundos, **When** o cliente consulta a cotação, **Then** recebe essa última cotação com o instante real em que foi obtida.
3. **Given** a fonte de cotação indisponível e a última cotação válida obtida há mais de 60 segundos (ou nenhuma), **When** o cliente consulta a cotação, **Then** recebe um erro específico de cotação indisponível.

---

### User Story 5 - Compra de bitcoin (Priority: P1)

O cliente informa um valor em reais a investir. O sistema converte o valor pela cotação de venda do momento, debita o saldo e registra um investimento com data da compra, valor investido, cotação usada e quantidade de BTC adquirida. O cliente recebe um e-mail com o valor investido e a quantidade de BTC comprada.

**Why this priority**: é a proposta central do produto; junto com conta, depósito, saldo e cotação forma o MVP.

**Independent Test**: depositar R$ 1.000,00, comprar R$ 400,00 e verificar saldo de R$ 600,00, investimento registrado com os dados corretos, entrada no extrato e e-mail.

**Acceptance Scenarios**:

1. **Given** saldo R$ 1.000,00 e cotação de venda R$ 400.000,00, **When** o cliente compra R$ 400,00, **Then** o saldo passa a R$ 600,00 e é registrado um investimento de R$ 400,00 com cotação R$ 400.000,00 e quantidade 0,00100000 BTC.
2. **Given** saldo R$ 100,00, **When** o cliente tenta comprar R$ 100,01, **Then** a compra é recusada com erro de saldo insuficiente e nenhum saldo, investimento ou extrato é alterado.
3. **Given** valor zero, negativo, ausente, não numérico ou com mais de 2 casas decimais, **When** o cliente tenta comprar, **Then** a compra é recusada com erro de valor inválido, sem efeito.
4. **Given** a fonte de cotação indisponível e nenhuma cotação válida com até 60 segundos, **When** o cliente tenta comprar, **Then** a compra é recusada com erro de cotação indisponível, sem efeito.
5. **Given** uma compra concluída, **When** a operação termina, **Then** o cliente recebe e-mail com o valor investido em R$ e a quantidade de BTC comprada, e a compra aparece no extrato com data/hora e cotação.

---

### User Story 6 - Posição dos investimentos (Priority: P2)

O cliente lista seus investimentos ativos, vendo para cada um: data de compra, valor investido em R$, cotação do BTC no momento da compra, percentual de variação do preço do bitcoin desde a compra até a cotação atual e valor bruto atual do investimento em R$.

**Why this priority**: permite acompanhar o resultado do investimento; depende de compras existirem.

**Independent Test**: realizar compras, alterar a cotação de referência e verificar variação percentual e valor bruto atual de cada investimento.

**Acceptance Scenarios**:

1. **Given** um investimento comprado à cotação R$ 400.000,00 com 0,00100000 BTC e cotação de compra atual R$ 440.000,00, **When** o cliente lista a posição, **Then** o investimento mostra variação de +10,00% e valor bruto atual de R$ 440,00.
2. **Given** um cliente sem investimentos ativos, **When** lista a posição, **Then** recebe uma lista vazia.
3. **Given** investimentos liquidados por completo, **When** o cliente lista a posição, **Then** eles não aparecem.

---

### User Story 7 - Venda (resgate) de bitcoin (Priority: P2)

O cliente informa um valor em reais que deseja resgatar. O sistema liquida investimentos do mais antigo para o mais recente, pela cotação de compra do momento, até atingir o valor desejado, e credita o valor resgatado no saldo. Um investimento liquidado apenas parcialmente é encerrado e seu residual é reinvestido automaticamente como novo investimento usando a cotação original daquele investimento. O cliente recebe e-mail com a quantidade de BTC vendida e o valor resgatado.

**Why this priority**: completa o ciclo de investimento, permitindo realizar ganhos ou perdas.

**Independent Test**: comprar em dois momentos distintos, resgatar um valor que consuma o primeiro investimento inteiro e parte do segundo, e verificar ordem de liquidação, saldo, reinvestimento, extrato e e-mail.

**Acceptance Scenarios**:

1. **Given** investimentos A (mais antigo, valor atual R$ 300,00) e B (valor atual R$ 500,00), **When** o cliente resgata R$ 300,00, **Then** A é liquidado por completo, B permanece intacto e o saldo aumenta R$ 300,00.
2. **Given** os mesmos A e B, **When** o cliente resgata R$ 500,00, **Then** A é liquidado por completo, B é encerrado e seu residual (a quantidade de BTC de B não vendida, com valor atual R$ 300,00) dá origem a um novo investimento com a cotação original e a data de compra original de B, e o saldo aumenta R$ 500,00.
3. **Given** um investimento de 1,00000000 BTC comprado a R$ 100.000,00 (valor investido R$ 100.000,00) e cotação de compra atual R$ 200.000,00, **When** o cliente resgata R$ 50.000,00, **Then** são vendidos 0,25000000 BTC, o saldo aumenta R$ 50.000,00 e é criado um reinvestimento de 0,75000000 BTC com cotação R$ 100.000,00, valor investido R$ 75.000,00 e a mesma data de compra do investimento original.
4. **Given** o reinvestimento do cenário anterior e um investimento comprado depois do original, **When** o cliente faz um novo resgate, **Then** o reinvestimento é liquidado primeiro.
5. **Given** o resgate de R$ 500,00 do cenário 2, **When** o cliente consulta o extrato, **Then** aparecem uma linha de resgate de A (R$ 300,00, BTC de A, cotação do momento), uma linha de resgate de B (R$ 200,00, BTC vendido de B, cotação do momento) e uma linha de reinvestimento (BTC residual de B, cotação original de B), todas com o mesmo identificador de solicitação.
6. **Given** valor atual total dos investimentos de R$ 800,00, **When** o cliente tenta resgatar R$ 800,01, **Then** o resgate é recusado com erro de valor de resgate acima do disponível, sem efeito algum.
7. **Given** um cliente sem investimentos, **When** tenta resgatar qualquer valor, **Then** recebe o mesmo erro de valor acima do disponível, sem efeito.
8. **Given** valor inválido (zero, negativo, ausente, não numérico ou com mais de 2 casas decimais) ou cotação indisponível, **When** o cliente tenta resgatar, **Then** recebe o erro correspondente, sem efeito.
9. **Given** um resgate concluído, **When** a operação termina, **Then** o cliente recebe e-mail com a quantidade de BTC vendida e o valor resgatado em R$.

---

### User Story 8 - Extrato (Priority: P2)

O cliente lista suas transações — depósitos, compras, resgates e reinvestimentos — com data/hora e, quando aplicável, a cotação utilizada. Por padrão são retornados os últimos 90 dias; o cliente pode informar um intervalo customizado.

**Why this priority**: dá transparência e permite ao cliente conferir sua movimentação.

**Independent Test**: realizar depósito, compra e resgate e verificar que todos constam no extrato padrão e que um intervalo customizado filtra corretamente.

**Acceptance Scenarios**:

1. **Given** transações nos últimos 90 dias e anteriores a isso, **When** o cliente consulta o extrato sem intervalo, **Then** recebe apenas as transações dos últimos 90 dias, da mais recente para a mais antiga.
2. **Given** um intervalo customizado válido, **When** o cliente consulta o extrato, **Then** recebe apenas transações cujo instante está dentro do intervalo (datas inicial e final inclusivas).
3. **Given** data inicial posterior à final ou data malformada, **When** o cliente consulta o extrato, **Then** recebe erro de intervalo inválido.
4. **Given** compras e resgates no extrato, **When** o cliente os consulta, **Then** cada um traz a cotação utilizada e a quantidade de BTC; depósitos não trazem cotação.

---

### User Story 9 - Volume diário (Priority: P3)

O cliente autenticado consulta o total de bitcoins comprados e o total vendidos no dia corrente, somando todos os clientes da plataforma.

**Why this priority**: informação de mercado complementar, não essencial ao ciclo de investimento.

**Independent Test**: dois clientes compram e vendem no mesmo dia; o volume retorna a soma das quantidades de ambos.

**Acceptance Scenarios**:

1. **Given** compras de 0,001 BTC (cliente X) e 0,002 BTC (cliente Y) e venda de 0,0005 BTC (cliente X) hoje, **When** um cliente consulta o volume diário, **Then** recebe 0,003 BTC comprados e 0,0005 BTC vendidos.
2. **Given** nenhuma operação no dia corrente, **When** consulta o volume, **Then** recebe zero para ambos.
3. **Given** operações do dia anterior, **When** consulta o volume do dia corrente, **Then** elas não são contabilizadas.
4. **Given** uma compra às 22:00 no horário de Brasília (já o dia seguinte em UTC), **When** o cliente consulta o volume nesse mesmo dia local, **Then** a compra é contabilizada no dia corrente de Brasília.
5. **Given** volume consultado, **When** a resposta é exibida, **Then** ela contém apenas totais agregados, sem identificar clientes.

---

### User Story 10 - Histórico de cotação (Priority: P3)

O cliente autenticado consulta os preços de compra e de venda do bitcoin nas últimas 24 horas, em pontos a cada 10 minutos alinhados ao relógio (08:00, 08:10, 08:20...). O sistema registra a cotação continuamente nessa frequência e expurga automaticamente registros com mais de 90 dias.

**Why this priority**: informação analítica complementar.

**Independent Test**: com o registro periódico ativo, consultar o histórico e verificar pontos alinhados a múltiplos de 10 minutos cobrindo as últimas 24 horas; verificar que registros com mais de 90 dias deixam de existir.

**Acceptance Scenarios**:

1. **Given** o registro periódico funcionando há mais de 24 horas, **When** o cliente consulta o histórico, **Then** recebe até 144 pontos, cada um com instante alinhado a múltiplo de 10 minutos, preço de compra e preço de venda, em ordem cronológica.
2. **Given** um intervalo em que a cotação não pôde ser registrada (fonte indisponível), **When** o cliente consulta o histórico, **Then** esse ponto é omitido (não é inventado nem interpolado).
3. **Given** registros com mais de 90 dias, **When** o expurgo automático executa, **Then** esses registros são removidos e os mais recentes preservados.

---

### Edge Cases

- **Concorrência sobre o mesmo saldo**: duas compras simultâneas que, somadas, excedem o saldo — apenas as que couberem no saldo são efetivadas; o saldo nunca fica negativo.
- **Concorrência sobre os mesmos investimentos**: dois resgates simultâneos não podem liquidar o mesmo BTC duas vezes nem resgatar, somados, mais que o valor atual total.
- **Atomicidade**: se qualquer parte de uma compra ou resgate falhar (débito/crédito, registro de investimento, liquidação, reinvestimento, extrato), nada é efetivado.
- **Resgate exatamente igual ao valor total**: todos os investimentos são liquidados por completo e nenhum reinvestimento é criado.
- **Resgate que coincide exatamente com o fim de um investimento**: o investimento é liquidado por completo, sem reinvestimento.
- **Arredondamento consome todo o BTC**: se, na liquidação parcial, o BTC necessário arredondado para cima no satoshi for igual ou maior que o BTC do investimento, ele é liquidado por inteiro e nenhum reinvestimento é criado.
- **Diferença de arredondamento**: em cada investimento liquidado por inteiro, o crédito é arredondado para baixo no centavo, então o cliente cede em BTC menos de R$ 0,01 além do valor creditado; no investimento liquidado em parte, cede menos de 1 satoshi além do valor exato creditado. O cliente nunca recebe valor sem BTC correspondente.
- **Compra de valor muito pequeno**: se a conversão resultar em quantidade de BTC inferior à menor unidade representável (0,00000001 BTC), a compra é recusada com erro de valor inválido.
- **Fonte de cotação fora do ar por pouco tempo**: por até 60 segundos após a última cotação válida, consultas e operações seguem usando essa cotação; depois disso, retornam erro de cotação indisponível até a fonte voltar.
- **Cotação muda durante a operação**: a operação usa uma única cotação, obtida no momento da operação, e é essa cotação que fica registrada.
- **Falha no envio de e-mail**: não desfaz a operação financeira já concluída.
- **Dados de outro cliente**: qualquer tentativa de acessar ou operar recursos de outra conta não revela a existência desses recursos.
- **E-mail com diferenças de maiúsculas/minúsculas ou espaços**: tratado como o mesmo e-mail para fins de unicidade e login.
- **Virada do dia**: o volume diário reinicia à meia-noite no horário de Brasília; uma compra às 23:59 local conta no dia, e uma às 00:00 local conta no dia seguinte, independentemente da data em UTC.
- **Lacunas no histórico de cotação**: pontos não registrados são omitidos.

## Requirements *(mandatory)*

### Functional Requirements

**Contas e autenticação**

- **FR-001**: O sistema MUST permitir cadastro com nome, e-mail e senha, criando a conta com saldo R$ 0,00.
- **FR-002**: O sistema MUST garantir unicidade de e-mail por conta, comparando e-mails sem diferenciar maiúsculas/minúsculas e ignorando espaços nas extremidades.
- **FR-003**: O sistema MUST validar o cadastro: nome não vazio, e-mail em formato válido e senha com no mínimo 8 caracteres e no máximo 72 bytes em UTF-8.
- **FR-004**: O sistema MUST autenticar o cliente por e-mail e senha e, em caso de sucesso, emitir um token de acesso com validade limitada.
- **FR-005**: O sistema MUST responder com o mesmo erro genérico de credenciais inválidas para e-mail inexistente e senha incorreta.
- **FR-006**: Toda operação, exceto cadastro e login, MUST exigir token de acesso válido; token ausente, malformado, adulterado ou expirado MUST resultar em erro de autenticação, sem efeito.
- **FR-007**: O sistema MUST NOT armazenar, registrar em log ou devolver senhas em texto plano.

**Saldo e depósito**

- **FR-008**: O cliente MUST poder depositar um valor positivo em R$, com no máximo 2 casas decimais, creditado imediatamente no saldo.
- **FR-009**: O cliente MUST poder consultar o saldo disponível em R$ da própria conta.
- **FR-010**: O saldo MUST NOT ficar negativo em nenhuma circunstância.

**Cotação**

- **FR-011**: O sistema MUST fornecer a cotação atual do BTC em R$ com preço de compra, preço de venda e o instante da cotação. Os preços de compra e de venda MUST ser exatamente os fornecidos pela fonte externa, sem margem, taxa ou ajuste aplicado pela plataforma.
- **FR-012**: Uma cotação obtida há no máximo 10 segundos é considerada atual e MAY ser reutilizada em consultas e operações sem nova chamada à fonte. Quando a fonte de cotação não responder, o sistema MAY usar a última cotação válida obtida, desde que seu instante tenha no máximo 60 segundos, tanto em consultas quanto em compras e resgates. Se não houver cotação válida com até 60 segundos, consultas e operações que dependem dela MUST falhar com erro específico de cotação indisponível, sem efeito.
- **FR-012a**: Toda cotação apresentada ou utilizada MUST informar o instante real a que se refere (o instante da obtenção na fonte, não o da consulta).

**Compra**

- **FR-013**: O cliente MUST poder comprar BTC informando um valor em R$ positivo com no máximo 2 casas decimais; a quantidade de BTC MUST ser calculada pela cotação de venda do momento.
- **FR-014**: A compra MUST debitar o valor do saldo e registrar um investimento com data/hora da compra, valor investido em R$, cotação utilizada e quantidade de BTC adquirida.
- **FR-015**: A compra com valor maior que o saldo disponível MUST ser recusada com erro de saldo insuficiente, sem qualquer efeito.

**Posição**

- **FR-016**: O cliente MUST poder listar seus investimentos ativos com data de compra, valor investido em R$, cotação na compra, percentual de variação e valor bruto atual em R$.
- **FR-017**: O valor bruto atual de um investimento MUST ser a quantidade de BTC multiplicada pela cotação de compra atual, arredondada para baixo no centavo; o percentual de variação MUST ser (cotação de compra atual − cotação da compra) ÷ cotação da compra × 100.

**Venda (resgate)**

- **FR-018**: O cliente MUST poder resgatar informando um valor em R$ positivo com no máximo 2 casas decimais.
- **FR-019**: O resgate MUST liquidar investimentos ativos em ordem crescente de data de compra (mais antigo primeiro), usando a cotação de compra do momento, até atingir o valor solicitado, e MUST creditar no saldo exatamente o valor solicitado.
- **FR-019a**: A conversão no resgate MUST seguir esta regra, por investimento: (1) investimento liquidado por inteiro credita quantidade de BTC × cotação de compra, arredondado para baixo no centavo; (2) o investimento liquidado em parte vende a quantidade de BTC necessária para cobrir o valor que ainda falta (valor ÷ cotação de compra), arredondada para cima no satoshi, e credita exatamente esse valor que falta; (3) se a quantidade calculada em (2) for igual ou maior que todo o BTC do investimento, ele é liquidado por inteiro, sem reinvestimento.
- **FR-020**: Quando um investimento for liquidado parcialmente, ele MUST ser encerrado por completo e o residual MUST ser reinvestido automaticamente como um novo investimento usando a cotação original do investimento encerrado. O reinvestimento MUST preservar exatamente a quantidade de BTC não vendida (quantidade original − quantidade vendida), com valor investido em R$ = quantidade residual de BTC × cotação original, arredondado para baixo no centavo. Nenhum BTC é criado ou destruído pelo reinvestimento.
- **FR-021**: O novo investimento gerado por reinvestimento MUST manter a data de compra original do investimento encerrado, preservando sua posição na fila de liquidação (mais antigo primeiro) e o histórico de variação exibido na posição. O instante do resgate é registrado na transação de reinvestimento do extrato.
- **FR-022**: Resgate de valor maior que o valor atual total dos investimentos ativos MUST ser recusado com erro explícito, sem qualquer efeito. O valor atual total MUST ser a soma dos valores brutos atuais de cada investimento ativo, cada um arredondado para baixo no centavo (FR-017), e é o mesmo total exibido na posição.

**Extrato**

- **FR-023**: O sistema MUST registrar no extrato cada depósito, compra, resgate e reinvestimento, com tipo, valor em R$, data/hora e, quando aplicável, quantidade de BTC e cotação utilizada.
- **FR-024**: Cada resgate MUST gerar no extrato uma linha de resgate para cada investimento liquidado, com valor em R$ creditado, quantidade de BTC vendida desse investimento e cotação de compra do momento. Todas as linhas de uma mesma solicitação MUST compartilhar o mesmo identificador de solicitação, e a soma dos valores das linhas de **resgate** MUST ser exatamente o valor resgatado (a linha de reinvestimento, FR-024a, não entra nessa soma).
- **FR-024a**: Em resgates com liquidação parcial, MUST haver, além das linhas de resgate, uma linha distinta de reinvestimento com o mesmo identificador de solicitação, contendo a quantidade de BTC residual, a cotação original e o valor investido (BTC residual × cotação original). Para o investimento liquidado parcialmente, a linha de resgate registra apenas a parte vendida.
- **FR-025**: O extrato MUST retornar, por padrão, as transações desde 00:00:00 (horário de Brasília) do dia de 90 dias atrás até o instante da consulta, da mais recente para a mais antiga, e MUST aceitar um intervalo customizado de datas (inclusivo); intervalo inválido MUST resultar em erro de intervalo inválido.

**Volume diário e histórico**

- **FR-026**: O sistema MUST informar o total de BTC comprado e o total de BTC vendido no dia corrente, agregando todos os clientes, sem expor dados individuais. Reinvestimentos MUST NOT ser contabilizados como compras; apenas o BTC efetivamente vendido para atender resgates conta como venda.
- **FR-027**: O sistema MUST registrar automaticamente a cotação (compra e venda) a cada 10 minutos, em instantes alinhados ao relógio (minutos 00, 10, 20, 30, 40, 50).
- **FR-028**: O sistema MUST fornecer o histórico de cotação das últimas 24 horas com os pontos registrados, em ordem cronológica, omitindo pontos não registrados.
- **FR-029**: O sistema MUST expurgar automaticamente registros de cotação com mais de 90 dias.

**Notificações**

- **FR-030**: O sistema MUST enviar e-mail ao cliente após cada depósito (valor depositado), compra (valor investido em R$ e quantidade de BTC) e resgate (quantidade de BTC vendida e valor resgatado em R$).
- **FR-031**: Falha no envio de e-mail MUST NOT desfazer nem impedir a operação financeira, e SHOULD ser registrada para diagnóstico.

**Regras transversais**

- **FR-032**: Valores em R$ MUST ser representados com exatidão em centavos (2 casas decimais) e quantidades de BTC com exatidão em 8 casas decimais (satoshi), sem perda por arredondamento implícito. Toda conversão entre R$ e BTC MUST aplicar uma regra de arredondamento explícita, determinística e documentada, que nunca credite ao cliente valor ou quantidade não lastreados.
- **FR-033**: Toda operação financeira MUST estar associada à conta autenticada; um cliente MUST NOT ler nem alterar dados de outro cliente.
- **FR-034**: Compras, resgates (incluindo liquidações e reinvestimentos) e depósitos MUST ser atômicos e seguros sob concorrência: ou todos os seus efeitos ocorrem, ou nenhum.
- **FR-034a**: O fuso horário de referência do negócio MUST ser o de Brasília (America/Sao_Paulo): ele define o "dia corrente" do volume diário (00:00:00 a 23:59:59 local), o período padrão de 90 dias e os limites das datas informadas no extrato (data inicial a partir de 00:00:00 e data final até 23:59:59, horário local). Todo instante devolvido ao cliente MUST estar em ISO-8601 com deslocamento explícito (ex.: `2026-11-09T08:10:00-03:00`).
- **FR-035**: Erros previsíveis MUST ter respostas distintas, estáveis e previsíveis, no mínimo: dados de entrada inválidos, valor inválido, e-mail já cadastrado, credenciais inválidas, token ausente ou inválido, saldo insuficiente, valor de resgate acima do disponível, intervalo de datas inválido e cotação indisponível. Respostas de erro MUST NOT expor detalhes internos.

### Key Entities

- **Conta (Cliente)**: pessoa titular; nome, e-mail (único), credencial protegida, saldo em R$, data de criação.
- **Investimento**: lote de BTC de um cliente; data/hora de compra, valor investido em R$, cotação utilizada, quantidade de BTC, situação (ativo/encerrado), origem (compra ou reinvestimento, com referência ao investimento encerrado que o originou).
- **Transação (lançamento de extrato)**: registro imutável de movimentação de um cliente; tipo (depósito, compra, resgate, reinvestimento), valor em R$, quantidade de BTC e cotação quando aplicável, data/hora, investimento relacionado (compra, resgate e reinvestimento) e identificador da solicitação (agrupa as linhas de resgate e de reinvestimento de um mesmo resgate).
- **Cotação**: preço de compra e preço de venda do BTC em R$ e o instante a que se referem.
- **Registro histórico de cotação**: cotação capturada em instante alinhado a 10 minutos; expurgado após 90 dias.
- **Notificação**: e-mail enviado ao cliente após depósito, compra ou resgate, com o resumo da operação.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Uma pessoa consegue se cadastrar, fazer login, depositar e realizar sua primeira compra em menos de 3 minutos.
- **SC-002**: Em 100% das contas, o saldo é exatamente igual a depósitos − compras + resgates registrados no extrato, sem diferença de nenhum centavo.
- **SC-003**: Em 100% das contas, a soma das quantidades de BTC dos investimentos ativos é exatamente igual ao BTC comprado menos o BTC vendido, sem diferença de nenhum satoshi.
- **SC-004**: 0 ocorrências de acesso a dados ou operação sobre recursos de outro cliente em testes de isolamento.
- **SC-005**: 0 ocorrências de saldo negativo ou resgate acima do disponível sob testes de operações simultâneas.
- **SC-006**: 95% das consultas (saldo, cotação, posição, extrato, volume, histórico) retornam resultado em até 2 segundos.
- **SC-007**: 95% dos e-mails de notificação são enviados em até 5 minutos após a operação.
- **SC-008**: Com a fonte de cotação disponível, o histórico das últimas 24 horas contém pelo menos 99% dos 144 pontos esperados.
- **SC-009**: Nenhum registro de cotação com mais de 91 dias permanece armazenado.
- **SC-010**: 100% dos erros previsíveis listados em FR-035 produzem a resposta distinta esperada em testes de contrato.

## Assumptions

- **Fonte de cotação**: a cotação vem de uma fonte de mercado externa que fornece preço de compra e de venda do BTC em R$; a escolha da fonte é decisão do Plan, desde que ela forneça os dois preços prontos. Uma cotação é considerada válida para uso por até 60 segundos após o instante em que foi obtida (ver FR-012).
- **Arredondamento**: na compra, a quantidade de BTC é arredondada para baixo em 8 casas decimais; no resgate vale a regra de FR-019a; o valor investido do reinvestimento é arredondado para baixo no centavo (FR-020); o valor bruto atual é arredondado para baixo no centavo (FR-017); o percentual de variação é exibido com 2 casas decimais.
- **Valores de entrada**: depósitos, compras e resgates aceitam valores positivos com no máximo 2 casas decimais; não há valor mínimo além de R$ 0,01 nem valor máximo definido neste escopo (exceto saldo/posição disponíveis).
- **Token**: o token de acesso tem validade limitada (ex.: 1 hora); não há renovação (refresh) nem logout com revogação neste escopo — ao expirar, o cliente faz login novamente.
- **Política de senha**: mínimo de 8 caracteres e máximo de 72 bytes em UTF-8 (limite técnico do algoritmo de hash), sem outras exigências de composição.
- **Sem taxas**: não há cobrança de taxas, spread adicional ou impostos; a única diferença entre preço de compra e de venda é a praticada pela fonte externa.
- **Notificações**: o envio de e-mail é feito após a confirmação da operação e é desacoplado dela; não há confirmação de recebimento.
- **Extrato**: sem paginação neste escopo; o intervalo customizado não tem limite máximo de duração.
- **Volume diário e histórico**: acessíveis a qualquer cliente autenticado, pois contêm apenas dados agregados de mercado.
- **Fora de escopo**: transferência bancária real, saque de reais para fora da plataforma, múltiplas moedas ou criptoativos, perfil administrativo, recuperação de senha, verificação de e-mail.
