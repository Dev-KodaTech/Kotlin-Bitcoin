# Feature Specification: Cliente Web do Investimento em Bitcoins

**Feature Branch**: `002-frontend-web`

**Created**: 2026-11-11

**Status**: Draft

**Input**: User description: "Cliente web para a API de Investimento em Bitcoins já especificada em specs/001-bitcoin-investment-api, consumindo o contrato autoritativo specs/001-bitcoin-investment-api/contracts/openapi.yaml sem redefini-lo. As regras de negócio continuam na API; a interface só coleta dados, exibe resultados e mostra estimativas identificadas como tal. Público: pessoas que investem pelo navegador, no celular ou no desktop. Idioma pt-BR. (descrição completa com user stories P1–P3, regras transversais e fora de escopo fornecida pelo usuário)"

## Contexto e relação com a feature 001

- Esta feature é o **cliente web** do produto. A API (feature 001) continua sendo a única fonte de verdade das regras de negócio (saldo, conversão, FIFO, arredondamento, limites, fuso de referência).
- O contrato da API pertence à feature 001 (`specs/001-bitcoin-investment-api/contracts/openapi.yaml`). Esta feature o consome por referência e não mantém cópia própria.
- Uma única necessidade nova na API é exigida aqui (consulta do perfil do cliente autenticado, FR-005). Ela MUST ser incluída no contrato de origem da 001 e implementada na API, e não definida neste cliente.
- Os termos **cotação de compra**, **cotação de venda**, **investimento**, **resgate** e **reinvestimento** têm o significado do glossário da 001. Em resumo: a compra de BTC pelo cliente usa a **cotação de venda**; o resgate e o valor atual dos investimentos usam a **cotação de compra**.
- **Estimativa**: valor calculado pela interface apenas para orientar a pessoa antes de confirmar uma operação, sempre identificado na tela como estimado. O resultado final é sempre o que a API devolve.

## Clarifications

### Session 2026-11-11

- Q: Como evitar operação financeira duplicada quando a conexão cai após o envio (resultado incerto)? → A: Proteção só na interface — ação bloqueada durante o envio; com resultado incerto, orientar a conferir saldo e extrato, sem reenvio; contrato da API inalterado.
- Q: Com estimativa de compra abaixo de 1 satoshi, a pessoa ainda pode confirmar? → A: Sim — a revisão só avisa; a confirmação continua disponível e a decisão é da API.
- Q: A sessão sobrevive ao fechamento completo do navegador? → A: Não — vale enquanto o navegador estiver aberto (sobrevive a recarregamento e novas abas) e termina ao fechá-lo, ou antes, na expiração do token.
- Q: "Meus investimentos" se atualiza sozinha como o painel? → A: Sim — atualização automática a cada ~10 s, com as mesmas regras do painel (suspensa com a aba oculta, controle de pausa, sem anúncio a cada ciclo).
- Q: A tela de resgate deve ter um atalho "Resgatar tudo"? → A: Sim — preenche o campo com o disponível exibido no momento; a revisão avisa que o valor pode ficar acima do disponível se a cotação cair; contrato da API inalterado.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cadastro, login e sessão (Priority: P1)

A pessoa cria uma conta informando nome, e-mail e senha e entra automaticamente. Depois pode sair e voltar a entrar com e-mail e senha. A sessão continua ao recarregar a página até expirar; se expirar durante o uso, a pessoa é levada ao login com o aviso de sessão expirada e, depois de entrar, volta à página em que estava. Páginas protegidas não abrem sem sessão.

**Why this priority**: sem conta e sessão nenhuma outra tela pode ser usada; é a porta de entrada do produto.

**Independent Test**: cadastrar uma conta pela interface, verificar a entrada automática no painel, sair, tentar abrir uma página protegida (deve levar ao login), entrar de novo e recarregar a página (a sessão continua).

**Acceptance Scenarios**:

1. **Given** a página de cadastro, **When** a pessoa informa nome, e-mail e senha válidos e envia, **Then** a conta é criada, a pessoa entra automaticamente e chega ao painel.
2. **Given** a página de cadastro, **When** a pessoa deixa o nome vazio, informa e-mail em formato inválido ou senha com menos de 8 caracteres e tenta enviar, **Then** cada campo inválido mostra sua própria mensagem junto a ele, o foco vai para o primeiro campo inválido e nada é enviado.
3. **Given** um e-mail já cadastrado, **When** a pessoa tenta se cadastrar com ele, **Then** o campo de e-mail mostra que o e-mail já está cadastrado, com um atalho para a página de login, e os demais dados digitados (exceto a senha) são mantidos.
4. **Given** a página de login, **When** a pessoa informa e-mail inexistente ou senha incorreta, **Then** vê a mesma mensagem genérica de e-mail ou senha incorretos nos dois casos, sem indicar qual dos dois está errado.
5. **Given** uma sessão ativa, **When** a pessoa recarrega a página ou abre o endereço do aplicativo em outra aba do mesmo navegador, **Then** continua conectada, sem precisar entrar de novo, enquanto a sessão não expirar.
6. **Given** uma sessão que expira enquanto a pessoa está na página de extrato com um filtro aplicado, **When** a expiração é detectada, **Then** ela é levada ao login com a mensagem "Sua sessão expirou. Entre novamente para continuar." e, depois de entrar, volta à página de extrato com o mesmo filtro.
7. **Given** uma sessão ativa, **When** a pessoa usa a ação "Sair", **Then** a sessão termina, ela vai para a página de login e, ao usar o botão voltar do navegador, nenhum dado da conta é exibido.
8. **Given** nenhuma sessão ativa, **When** alguém abre diretamente o endereço de uma página protegida, **Then** é levado ao login e, depois de entrar, chega à página que tentou abrir.
9. **Given** uma sessão ativa ainda não expirada, **When** a pessoa fecha o navegador por completo e o abre de novo no endereço do aplicativo, **Then** precisa entrar novamente.

---

### User Story 2 - Painel (Priority: P1)

Após entrar, a pessoa vê o painel: uma saudação com seu nome, o saldo em R$, a cotação atual (compra, venda e o instante a que se refere), atualizada automaticamente a cada ~10 segundos, e o valor atual total dos seus investimentos. Há atalhos para depositar, comprar e resgatar.

**Why this priority**: é a página inicial e o ponto de partida de todas as operações; reúne a informação mínima para decidir investir.

**Independent Test**: entrar com uma conta que tenha saldo e investimentos e verificar saudação, saldo, cotação com instante, valor total dos investimentos e os três atalhos; observar a cotação mudar sozinha; simular cotação indisponível e verificar que o resto do painel continua funcionando.

**Acceptance Scenarios**:

1. **Given** uma pessoa chamada "Maria Souza" com saldo R$ 1.234,56, **When** abre o painel, **Then** vê "Olá, Maria", o saldo "R$ 1.234,56", a cotação de compra, a cotação de venda, o instante da cotação no horário de Brasília e o valor atual total dos investimentos.
2. **Given** o painel aberto e visível, **When** passam ~10 segundos, **Then** a cotação e o valor atual total dos investimentos são atualizados sem recarregar a página e sem tirar o foco do teclado de onde estava.
3. **Given** a cotação indisponível na API, **When** o painel tenta atualizá-la, **Then** o bloco de cotação mostra "Cotação indisponível no momento" (sem exibir valores antigos como se fossem atuais), o valor total dos investimentos mostra que depende da cotação, saldo, saudação e atalhos continuam funcionando, e as tentativas automáticas continuam até a cotação voltar.
4. **Given** uma conta sem investimentos, **When** abre o painel, **Then** o valor atual total aparece como R$ 0,00 com um convite para fazer a primeira compra.
5. **Given** o painel, **When** a pessoa usa os atalhos "Depositar", "Comprar" ou "Resgatar", **Then** vai para a tela da respectiva operação.
6. **Given** o painel aberto, **When** a pessoa aciona o controle de pausar a atualização automática, **Then** a cotação para de se atualizar até ela retomar, e o estado (pausado/ativo) é visível e anunciado.
7. **Given** a falha ao carregar o perfil, **When** o painel abre, **Then** a saudação aparece sem o nome ("Olá!") e o restante do painel funciona normalmente.

---

### User Story 3 - Depósito (Priority: P1)

A pessoa informa um valor em R$ e o deposita. Vê a confirmação com o valor depositado e o novo saldo.

**Why this priority**: é a única forma de obter saldo para investir.

**Independent Test**: em uma conta nova, depositar R$ 100,00 e verificar a confirmação com o valor e o novo saldo R$ 100,00, também refletido no painel.

**Acceptance Scenarios**:

1. **Given** saldo R$ 50,00, **When** a pessoa deposita "100,00", **Then** vê a confirmação "Depósito de R$ 100,00 realizado", o novo saldo R$ 150,00 e a data/hora da operação.
2. **Given** a tela de depósito, **When** a pessoa informa zero, valor negativo, texto não numérico, campo vazio ou mais de 2 casas decimais, **Then** o campo mostra a mensagem de valor inválido e nada é enviado.
3. **Given** a tela de depósito, **When** a pessoa digita "1.234,56", **Then** o valor é entendido como mil duzentos e trinta e quatro reais e cinquenta e seis centavos.
4. **Given** um depósito em envio, **When** a pessoa clica de novo no botão, pressiona Enter repetidamente ou recarrega a página de resultado, **Then** apenas um depósito é realizado.

---

### User Story 4 - Compra de bitcoin (Priority: P1)

A pessoa informa um valor em R$ a investir. Antes de confirmar, vê uma estimativa da quantidade de BTC pela cotação de venda atual, com o aviso de que o valor final usa a cotação do momento da operação. Após confirmar, vê o valor investido, o BTC comprado, a cotação utilizada e o novo saldo.

**Why this priority**: é a proposta central do produto; junto com cadastro, painel e depósito forma o MVP.

**Independent Test**: com saldo R$ 1.000,00, informar R$ 400,00, conferir a estimativa e o aviso, confirmar e verificar valor investido, BTC comprado, cotação utilizada e novo saldo R$ 600,00.

**Acceptance Scenarios**:

1. **Given** saldo R$ 1.000,00 e cotação de venda R$ 400.000,00, **When** a pessoa informa R$ 400,00, **Then** vê, antes de confirmar, "Estimativa: 0,00100000 BTC", a cotação de venda e o instante usados na estimativa, e o aviso "Valor estimado. A quantidade final usa a cotação do momento da compra."
2. **Given** a tela de revisão de uma compra, **When** a cotação é atualizada antes da confirmação, **Then** a estimativa é recalculada e o instante exibido muda junto.
3. **Given** a revisão da compra, **When** a pessoa confirma, **Then** vê o resultado devolvido pela API: valor investido, BTC comprado (8 casas), cotação utilizada, data/hora e novo saldo, sem qualquer valor estimado.
4. **Given** saldo R$ 100,00, **When** a pessoa confirma uma compra de R$ 100,01, **Then** vê "Saldo insuficiente para esta compra", o saldo atual e um atalho para depositar, e nada é comprado.
5. **Given** um valor zero, negativo, não numérico ou com mais de 2 casas decimais, **When** a pessoa tenta avançar, **Then** o campo mostra a mensagem de valor inválido e nada é enviado.
6. **Given** um valor tão pequeno que a estimativa resulta em menos de 0,00000001 BTC (1 satoshi), **When** a pessoa vê a revisão, **Then** a estimativa avisa que o valor provavelmente é pequeno demais para comprar a menor fração de bitcoin; **and When** a pessoa confirma e a API recusa o valor, **Then** vê a mensagem de que o valor é inválido ou pequeno demais para comprar 1 satoshi.
7. **Given** a cotação indisponível, **When** a pessoa está na revisão ou confirma a compra, **Then** a estimativa mostra "Cotação indisponível" e, se confirmada, a compra é recusada com a mensagem de cotação indisponível e a opção de tentar de novo; nada é comprado.
8. **Given** uma compra em envio, **When** a pessoa clica de novo, pressiona Enter repetidamente ou recarrega a página de resultado, **Then** apenas uma compra é realizada.

---

### User Story 5 - Meus investimentos (Priority: P2)

A pessoa vê a lista dos investimentos ativos, com data de compra, valor investido, cotação na compra, variação percentual e valor bruto atual de cada um, além do total.

**Why this priority**: permite acompanhar o resultado; depende de existirem compras.

**Independent Test**: com dois investimentos, um com variação positiva e outro negativa, verificar cada campo, a diferenciação visual não dependente só de cor e o total; com uma conta sem investimentos, verificar o estado vazio.

**Acceptance Scenarios**:

1. **Given** um investimento comprado a R$ 400.000,00 e cotação de compra atual R$ 440.000,00, **When** a pessoa abre "Meus investimentos", **Then** vê a data de compra, o valor investido, a cotação na compra, a variação "+10,00%" com indicação de alta por símbolo e texto (além de cor) e o valor bruto atual devolvido pela API.
2. **Given** um investimento com variação negativa, **When** a lista é exibida, **Then** a variação aparece como "−5,00%" com indicação de queda por símbolo e texto (além de cor); variação zero aparece como "0,00%" neutra.
3. **Given** investimentos ativos, **When** a lista é exibida, **Then** aparece o valor atual total devolvido pela API, a cotação de compra em que a posição se baseia e o instante dessa cotação.
4. **Given** uma conta sem investimentos ativos, **When** abre a página, **Then** vê o estado vazio com um convite e atalho para comprar.
5. **Given** a cotação indisponível, **When** a página é aberta, **Then** vê a mensagem de cotação indisponível com a opção de tentar de novo, sem quebrar a navegação.
6. **Given** a página aberta e visível, **When** passam ~10 segundos, **Then** a variação, o valor bruto atual de cada investimento, o total e a cotação de referência são atualizados sem recarregar a página e sem mover o foco.

---

### User Story 6 - Resgate (Priority: P2)

A pessoa informa um valor em R$ a resgatar e vê o valor disponível para resgate. Antes de confirmar, vê que o valor é estimado pela cotação de compra atual. Após confirmar, vê o valor resgatado, o BTC vendido, os investimentos liquidados e o reinvestimento, quando houver.

**Why this priority**: completa o ciclo de investimento, permitindo realizar ganhos ou perdas.

**Independent Test**: com dois investimentos, resgatar um valor que liquide o primeiro inteiro e parte do segundo e verificar o resultado: valor resgatado, BTC vendido, cotação, novo saldo, os dois investimentos liquidados (um total, um parcial) e o reinvestimento.

**Acceptance Scenarios**:

1. **Given** valor atual total dos investimentos de R$ 800,00, **When** a pessoa abre a tela de resgate, **Then** vê "Disponível para resgate: R$ 800,00", a cotação de compra em que o valor se baseia, o instante dessa cotação e a indicação de que é um valor estimado que muda com a cotação.
2. **Given** a pessoa informa R$ 500,00, **When** revisa o resgate, **Then** vê o valor a resgatar, a estimativa de BTC a vender pela cotação de compra atual e o aviso de que a quantidade final usa a cotação do momento do resgate.
3. **Given** a confirmação de um resgate que liquida o investimento A por completo e o B em parte, **When** a API devolve o resultado, **Then** a pessoa vê o valor resgatado, o BTC vendido no total, a cotação utilizada, o novo saldo, a data/hora, a lista dos investimentos liquidados (valor, BTC e se a liquidação foi total ou parcial) e o reinvestimento (BTC residual, valor investido, cotação original e data de compra original), com uma explicação curta do que é reinvestimento.
4. **Given** um resgate sem liquidação parcial, **When** o resultado é exibido, **Then** não aparece seção de reinvestimento.
5. **Given** disponível R$ 800,00, **When** a pessoa usa "Resgatar tudo", **Then** o campo é preenchido com 800,00 e a revisão avisa que, se a cotação cair antes da confirmação, o valor pode ficar acima do disponível; se isso ocorrer, a recusa mostra o novo disponível e o atalho pode ser usado de novo.
6. **Given** disponível R$ 800,00, **When** a pessoa confirma o resgate de R$ 800,01, **Then** vê "O valor solicitado é maior que o disponível para resgate", o valor disponível atualizado e nada é resgatado.
7. **Given** uma conta sem investimentos, **When** abre a tela de resgate, **Then** vê disponível R$ 0,00 com convite para comprar, e uma tentativa de resgate recebe a mensagem de valor acima do disponível.
8. **Given** valor inválido ou cotação indisponível, **When** a pessoa tenta resgatar, **Then** vê a mensagem correspondente (com opção de tentar de novo no caso da cotação) e nada é resgatado.
9. **Given** um resgate em envio, **When** a pessoa clica de novo, pressiona Enter repetidamente ou recarrega a página de resultado, **Then** apenas um resgate é realizado.

---

### User Story 7 - Extrato (Priority: P2)

A pessoa vê suas transações dos últimos 90 dias, da mais recente para a mais antiga, e pode filtrar por data inicial e final. Cada linha mostra tipo, valor, data/hora e, quando houver, BTC e cotação. As linhas de um mesmo resgate aparecem agrupadas.

**Why this priority**: dá transparência e permite conferir a movimentação.

**Independent Test**: com depósito, compra e um resgate que gerou duas linhas de resgate e uma de reinvestimento, verificar o extrato padrão, a ordem, o agrupamento do resgate, um filtro válido, um intervalo inválido e o estado vazio.

**Acceptance Scenarios**:

1. **Given** transações nos últimos 90 dias, **When** a pessoa abre o extrato sem filtro, **Then** vê as transações do período padrão, da mais recente para a mais antiga, com o período exibido (data inicial e final).
2. **Given** um depósito, **When** aparece no extrato, **Then** mostra tipo "Depósito", valor e data/hora, sem BTC e sem cotação.
3. **Given** uma compra, **When** aparece no extrato, **Then** mostra tipo "Compra", valor, BTC, cotação e data/hora.
4. **Given** um resgate com duas linhas de resgate e uma de reinvestimento com o mesmo identificador de solicitação, **When** o extrato é exibido, **Then** as três linhas aparecem juntas num grupo identificado como um único resgate, com o valor total resgatado (soma das linhas de resgate), e a linha de reinvestimento é identificada como reinvestimento que não altera o saldo.
5. **Given** datas inicial e final válidas, **When** a pessoa aplica o filtro, **Then** vê apenas as transações do intervalo (inclusivo, horário de Brasília) e pode limpar o filtro para voltar ao período padrão.
6. **Given** data inicial posterior à final, apenas uma das datas preenchida ou data inválida, **When** a pessoa tenta aplicar o filtro, **Then** vê a mensagem de intervalo inválido junto aos campos de data e o extrato anterior permanece visível.
7. **Given** nenhum lançamento no período, **When** o extrato é exibido, **Then** vê o estado vazio indicando o período consultado.

---

### User Story 8 - Mercado (Priority: P3)

A pessoa vê o volume de BTC comprado e vendido no dia corrente (todos os clientes, horário de Brasília) e um gráfico de linha das últimas 24 horas com o preço de compra e o de venda. Pontos ausentes aparecem como lacunas.

**Why this priority**: informação de mercado complementar, não essencial ao ciclo de investimento.

**Independent Test**: com histórico que tenha um intervalo sem registro, verificar o volume do dia, as duas linhas do gráfico, a lacuna sem ligação entre os pontos vizinhos e a alternativa em texto/tabela dos dados.

**Acceptance Scenarios**:

1. **Given** volume do dia de 0,00300000 BTC comprados e 0,00050000 BTC vendidos, **When** a pessoa abre "Mercado", **Then** vê os dois totais com 8 casas decimais e a data a que se referem.
2. **Given** pontos de histórico das últimas 24 horas, **When** o gráfico é exibido, **Then** mostra duas linhas (compra e venda) com eixo de tempo no horário de Brasília, diferenciadas por cor e também por estilo de linha ou marcador, com legenda.
3. **Given** um intervalo de 10 minutos sem registro no histórico, **When** o gráfico é exibido, **Then** aparece uma lacuna nesse trecho, sem linha ligando os pontos vizinhos e sem valor inventado.
4. **Given** o gráfico, **When** a pessoa usa leitor de tela ou teclado, **Then** tem acesso aos mesmos dados em forma textual (tabela ou resumo com mínimo, máximo e último valor de cada linha).
5. **Given** nenhum ponto de histórico, **When** a página abre, **Then** vê o estado vazio do gráfico sem afetar o bloco de volume.

---

### Edge Cases

- **Resultado incerto de operação financeira**: a conexão cai depois de um depósito, compra ou resgate ser enviado e antes da resposta. A interface informa que não foi possível confirmar se a operação foi concluída, orienta a conferir saldo e extrato antes de tentar de novo, oferece atalhos para eles e **não** reenvia a operação automaticamente nem oferece reenvio com um clique.
- **Sessão expira com operação em revisão**: a pessoa volta à página da operação após entrar, mas o valor digitado não é restaurado nem enviado automaticamente.
- **Sessão expira em várias abas**: todas as abas do mesmo navegador passam a exigir login na próxima interação; "Sair" em uma aba encerra a sessão nas demais.
- **Destino de retorno após login**: só páginas do próprio aplicativo são aceitas; endereços externos ou inválidos levam ao painel.
- **"Resgatar tudo" com cotação em queda**: o disponível preenchido pode ficar acima do novo disponível; a API recusa como valor acima do disponível, a interface mostra o disponível atualizado e a pessoa pode usar o atalho de novo. Pode restar um saldo residual de investimentos se a cotação subir entre o preenchimento e a confirmação.
- **Cotação muda entre a revisão e a confirmação**: o resultado exibido é o da API, que pode diferir da estimativa; a tela de resultado não mostra a estimativa para não confundir.
- **Cotação volta a ficar disponível**: os blocos que dependem dela (painel, revisão, posição, disponível para resgate) voltam a exibir valores sem a pessoa recarregar a página, nas telas que se atualizam automaticamente.
- **Aba em segundo plano**: a atualização automática da cotação é suspensa enquanto a página não está visível e é feita imediatamente ao voltar.
- **Fuso do dispositivo diferente de Brasília**: datas e horas continuam exibidas no horário de Brasília, identificadas como tal; os filtros do extrato usam datas do calendário de Brasília.
- **Valores muito grandes ou muito pequenos**: R$ 99.999.999.999,99 e 0,00000001 BTC são exibidos com todos os dígitos, sem notação científica e sem arredondamento.
- **Cotação com mais de 2 casas decimais**: cotações são exibidas com todas as casas devolvidas pela API (mínimo 2), sem arredondar.
- **Formatos de digitação de valor**: "1234,56", "1.234,56" e "1234" são aceitos; "1,234.56", "12,345" (3 casas), "R$ -5" e "1e3" são recusados com mensagem de formato.
- **Senha com caracteres multibyte**: senha com pelo menos 8 caracteres, mas acima de 72 bytes em UTF-8 (por exemplo, por usar emojis ou letras acentuadas), é recusada com mensagem compreensível de senha longa demais.
- **Falha de cadastro após sucesso**: a conta é criada mas a entrada automática falha; a pessoa vai para o login com a mensagem "Conta criada. Entre com seu e-mail e senha."
- **Grupo de resgate cortado pelo filtro**: se o intervalo filtrado incluir só parte das linhas de um resgate, as linhas presentes ainda aparecem agrupadas pelo identificador.
- **Lista de extrato extensa**: sem paginação na API; a página continua utilizável (rolagem, navegação por teclado) com centenas de linhas.
- **Sem conexão ao abrir uma tela**: a tela mostra o estado de erro de conexão com opção de tentar de novo, sem perder a navegação.

## Requirements *(mandatory)*

### Functional Requirements

**Contas, sessão e navegação**

- **FR-001**: A interface MUST oferecer página de cadastro com nome, e-mail e senha, validando antes do envio: nome não vazio (ignorando espaços nas extremidades) e com no máximo 200 caracteres, e-mail em formato válido e senha com no mínimo 8 caracteres e no máximo 72 bytes em UTF-8 (FR-003 da 001). Cada campo inválido MUST exibir sua própria mensagem junto a ele.
- **FR-002**: Após o cadastro bem-sucedido, a interface MUST iniciar a sessão automaticamente com as credenciais informadas e levar a pessoa ao painel.
- **FR-003**: A interface MUST oferecer página de login por e-mail e senha e exibir a mesma mensagem genérica para e-mail inexistente e senha incorreta.
- **FR-004**: A sessão MUST persistir entre recarregamentos e abas do mesmo navegador enquanto o navegador estiver aberto, até o instante de expiração do token, e MUST terminar quando o navegador for fechado. A sessão MUST ser encerrada ao atingir esse instante ou ao receber da API recusa por autenticação, o que ocorrer primeiro; nesse caso a pessoa MUST ser levada ao login com a mensagem de sessão expirada e, depois de entrar, MUST voltar à página (incluindo filtros) em que estava. Valores digitados em formulários de operações financeiras MUST NOT ser restaurados nem reenviados.
- **FR-005** *(necessidade nova na API)*: A API MUST permitir que o cliente autenticado consulte os próprios dados de perfil — nome e e-mail — sem expor dados sensíveis e sem identificador de outro cliente. A operação MUST ser acrescentada ao contrato de origem da 001 e implementada na API antes do uso por esta interface.
- **FR-006**: A interface MUST oferecer a ação "Sair", disponível em todas as páginas protegidas, que encerra a sessão no navegador (em todas as abas), descarta os dados da conta exibidos e leva ao login.
- **FR-007**: Páginas protegidas MUST NOT exibir dados sem sessão ativa; o acesso direto sem sessão MUST levar ao login e, após entrar, à página solicitada. Só destinos internos do aplicativo MUST ser aceitos como retorno.
- **FR-008**: A credencial de acesso MUST NOT aparecer no endereço das páginas nem em registros de diagnóstico.
- **FR-009**: A interface MUST oferecer navegação persistente entre Painel, Depositar, Comprar, Meus investimentos, Resgatar, Extrato e Mercado, indicando a página atual.

**Painel**

- **FR-010**: O painel MUST ser a página inicial após o login e MUST exibir: saudação com o primeiro nome da pessoa, saldo em R$, cotação atual (compra, venda e instante a que se refere), valor atual total dos investimentos e atalhos para depositar, comprar e resgatar.
- **FR-011**: A cotação e o valor atual total dos investimentos MUST ser atualizados automaticamente a cada ~10 segundos enquanto a página estiver visível, sem recarregar a página e sem mover o foco. A atualização MUST ser suspensa com a página oculta e retomada imediatamente quando ela voltar a ficar visível.
- **FR-012**: A interface MUST oferecer um controle acessível para pausar e retomar a atualização automática, com o estado atual visível.
- **FR-013**: Cada bloco do painel MUST carregar, falhar e exibir seu estado de forma independente: falha da cotação, do perfil ou da posição MUST NOT impedir o uso dos demais blocos e atalhos. Com a cotação indisponível, a interface MUST exibir o estado "indisponível" em vez de valores anteriores e MUST continuar tentando nas atualizações seguintes.

**Depósito**

- **FR-014**: A pessoa MUST poder informar um valor em R$ e depositá-lo, vendo a confirmação com valor depositado, data/hora e novo saldo devolvidos pela API.

**Compra**

- **FR-015**: A compra MUST ter duas etapas: informar o valor e revisar antes de confirmar. A revisão MUST exibir o saldo disponível, a estimativa de BTC (valor ÷ cotação de venda atual, truncada em 8 casas), a cotação e o instante usados na estimativa e o aviso de que a quantidade final usa a cotação do momento da compra. A estimativa MUST ser recalculada quando a cotação for atualizada.
- **FR-016**: Quando a estimativa resultar em menos de 0,00000001 BTC, a revisão MUST avisar que o valor provavelmente é pequeno demais para comprar a menor fração de bitcoin. O aviso MUST NOT bloquear a confirmação; a decisão final MUST ser da API.
- **FR-017**: Após a confirmação, a interface MUST exibir somente o resultado devolvido pela API: valor investido, BTC comprado, cotação utilizada, data/hora e novo saldo.
- **FR-018**: A interface MUST tratar, com mensagem própria e sem efeito, saldo insuficiente (exibindo o saldo atual e atalho para depositar), valor inválido (incluindo valor pequeno demais para 1 satoshi) e cotação indisponível (com opção de tentar de novo).

**Meus investimentos**

- **FR-019**: A interface MUST listar os investimentos ativos, na ordem devolvida pela API, com data de compra, valor investido, cotação na compra, variação percentual e valor bruto atual, além do valor atual total e da cotação (com instante) em que a posição se baseia, todos como devolvidos pela API. A lista MUST ser atualizada automaticamente a cada ~10 segundos com as mesmas regras do painel (FR-011, FR-012, FR-047).
- **FR-020**: A variação positiva, negativa e nula MUST ser diferenciada por sinal, ícone ou texto, além da cor.
- **FR-021**: Sem investimentos ativos, a página MUST exibir estado vazio com convite e atalho para comprar.

**Resgate**

- **FR-022**: A tela de resgate MUST exibir o valor disponível para resgate (valor atual total devolvido pela API), a cotação de compra e o instante em que ele se baseia, identificando-o como valor que varia com a cotação. A tela MUST oferecer o atalho "Resgatar tudo", que preenche o campo de valor com o disponível exibido naquele momento (sem confirmar automaticamente); quando usado, a revisão MUST avisar que o valor pode ficar acima do disponível se a cotação cair antes da confirmação.
- **FR-023**: O resgate MUST ter duas etapas: informar o valor e revisar antes de confirmar. A revisão MUST exibir a estimativa de BTC a vender pela cotação de compra atual, identificada como estimada, e o aviso de que o resultado final usa a cotação do momento do resgate.
- **FR-024**: Após a confirmação, a interface MUST exibir o resultado devolvido pela API: valor resgatado, BTC vendido, cotação utilizada, data/hora, novo saldo, cada investimento liquidado (valor, BTC e se a liquidação foi total ou parcial) e, quando houver, o reinvestimento (BTC residual, valor investido, cotação original e data de compra original) com explicação curta do conceito.
- **FR-025**: A interface MUST tratar, com mensagem própria e sem efeito, valor acima do disponível (exibindo o disponível atualizado), valor inválido e cotação indisponível (com opção de tentar de novo).

**Extrato**

- **FR-026**: O extrato MUST exibir, por padrão, o período padrão da API (últimos 90 dias), da transação mais recente para a mais antiga, e MUST exibir as datas do período consultado.
- **FR-027**: A interface MUST oferecer filtro por data inicial e final (ambas obrigatórias juntas, datas do calendário de Brasília, inclusivas), validando antes do envio que ambas estejam preenchidas, sejam datas válidas e a inicial não seja posterior à final; e MUST permitir limpar o filtro. O filtro aplicado MUST fazer parte do endereço da página, para sobreviver a recarregamento e retorno após login.
- **FR-028**: Cada linha MUST mostrar o tipo (Depósito, Compra, Resgate, Reinvestimento), o valor em R$, a data/hora e, quando houver, a quantidade de BTC e a cotação.
- **FR-029**: Linhas com o mesmo identificador de solicitação MUST ser exibidas agrupadas como um único resgate, com o valor total resgatado (soma exata das linhas de resgate do grupo, sem a de reinvestimento); a linha de reinvestimento MUST indicar que não altera o saldo.
- **FR-030**: Intervalo inválido (detectado pela interface ou recusado pela API) MUST exibir a mensagem de intervalo inválido junto aos campos de data, mantendo o extrato anterior visível.

**Mercado**

- **FR-031**: A página Mercado MUST exibir o total de BTC comprado e vendido no dia corrente (agregado, horário de Brasília), com a data de referência devolvida pela API.
- **FR-032**: A página MUST exibir um gráfico de linha das últimas 24 horas com o preço de compra e o de venda, eixo de tempo no horário de Brasília e legenda; as linhas MUST ser diferenciáveis sem depender só de cor.
- **FR-033**: Intervalos de 10 minutos sem ponto registrado MUST aparecer como lacuna no gráfico, sem ligação entre os pontos vizinhos e sem interpolação.
- **FR-034**: Os dados do gráfico MUST estar disponíveis em forma textual acessível (tabela ou resumo com mínimo, máximo e último valor de cada série).
- **FR-035**: Volume e gráfico MUST carregar ao abrir a página e MUST poder ser atualizados por ação explícita da pessoa.

**Formatos e precisão**

- **FR-036**: Valores em R$ MUST ser exibidos no formato "R$ 1.234,56"; quantidades de BTC com exatamente 8 casas decimais e vírgula decimal (ex.: "0,00100000 BTC"); cotações em R$ com todas as casas decimais devolvidas pela API (mínimo 2); percentuais com 2 casas e sinal (ex.: "+10,00%").
- **FR-037**: Datas e horas MUST ser exibidas no horário de Brasília, independentemente do fuso do dispositivo, no formato "dd/mm/aaaa hh:mm" (com segundos no instante da cotação), identificadas como horário de Brasília.
- **FR-038**: Valores monetários e quantidades de BTC MUST ser exibidos e manipulados sem perda de precisão, sem arredondamento implícito e sem notação científica; toda estimativa e soma exibida MUST usar aritmética decimal exata.
- **FR-039**: Campos de valor em R$ MUST aceitar a digitação no padrão brasileiro (vírgula decimal, ponto de milhar opcional) e MUST recusar, antes do envio, valor vazio, zero, negativo, não numérico ou com mais de 2 casas decimais. A API MUST sempre revalidar.

**Erros e estados**

- **FR-040**: Cada código de erro previsível do contrato MUST ter mensagem própria, em pt-BR, compreensível e sem detalhes técnicos, no mínimo:

  | Situação (código do contrato) | Mensagem / comportamento esperado |
  |-------------------------------|------------------------------------|
  | Dados de entrada inválidos (VALIDATION_ERROR) | Mensagem por campo, junto ao campo correspondente; resumo no topo do formulário |
  | Valor inválido (INVALID_AMOUNT) | "Informe um valor maior que zero, com no máximo 2 casas decimais." Na compra, acrescenta que o valor pode ser pequeno demais para comprar 1 satoshi |
  | E-mail já cadastrado (EMAIL_ALREADY_IN_USE) | "Este e-mail já está cadastrado." com atalho para entrar |
  | Credenciais inválidas (INVALID_CREDENTIALS) | "E-mail ou senha incorretos." |
  | Sessão ausente ou expirada (UNAUTHORIZED) | Fluxo de sessão expirada (FR-004) |
  | Saldo insuficiente (INSUFFICIENT_BALANCE) | "Saldo insuficiente para esta compra." com saldo atual e atalho para depositar |
  | Resgate acima do disponível (REDEMPTION_EXCEEDS_AVAILABLE) | "O valor solicitado é maior que o disponível para resgate." com o disponível atualizado |
  | Intervalo de datas inválido (INVALID_DATE_RANGE) | "Intervalo de datas inválido. A data inicial deve ser igual ou anterior à final." |
  | Cotação indisponível (QUOTE_UNAVAILABLE) | "Cotação indisponível no momento. Tente novamente em instantes." com opção de tentar de novo |
  | Serviço indisponível (SERVICE_UNAVAILABLE) | "Serviço temporariamente indisponível." com opção de tentar de novo |

- **FR-041**: Erros inesperados (incluindo INTERNAL_ERROR, NOT_FOUND e respostas fora do contrato) e falta de conexão MUST exibir mensagem genérica com opção de tentar de novo. Para operações financeiras com resultado incerto, MUST valer o comportamento descrito em Edge Cases (sem reenvio automático nem com um clique).
- **FR-042**: Toda tela ou bloco que carrega dados MUST ter estados distintos de carregando, vazio (quando aplicável) e erro.

**Proteção contra envio duplicado**

- **FR-043**: Depósito, compra e resgate MUST ser enviados no máximo uma vez por confirmação: enquanto o envio estiver em andamento, a ação MUST ficar indisponível (clique, toque ou tecla Enter) e indicar que está em processamento; recarregar, voltar ou avançar no navegador após o envio MUST NOT reenviar a operação.

**Aparência e acessibilidade**

- **FR-044**: A interface MUST ser responsiva, utilizável de 320 px de largura (celular) a telas de desktop sem rolagem horizontal da página, e com zoom de texto de até 200%.
- **FR-045**: A interface MUST seguir o tema claro ou escuro conforme a preferência do sistema, com contraste conforme WCAG 2.1 AA nos dois temas, e MUST respeitar a preferência de movimento reduzido.
- **FR-046**: A interface MUST atender a WCAG 2.1 nível AA, incluindo: todas as funções operáveis por teclado, foco sempre visível e em ordem lógica, rótulo visível e associado em todos os campos, mensagens de erro associadas aos campos e anunciadas por leitores de tela, foco levado ao primeiro erro ou ao resultado após o envio, títulos de página únicos e estrutura de cabeçalhos coerente.
- **FR-047**: Atualizações automáticas da cotação MUST NOT ser anunciadas a cada ciclo por leitores de tela; apenas mudanças de estado relevantes (cotação indisponível ou restabelecida) e resultados de operações MUST ser anunciados.
- **FR-048**: Todo texto da interface MUST estar em pt-BR.

### Key Entities

- **Sessão**: estado de acesso da pessoa no navegador; credencial de acesso emitida pela API, instante de expiração e página de retorno após novo login. Compartilhada entre as abas do mesmo navegador; termina na expiração do token, no "Sair" ou no fechamento do navegador.
- **Perfil**: nome e e-mail do cliente autenticado, obtidos da API (FR-005); usados na saudação.
- **Cotação exibida**: preço de compra, preço de venda e instante de referência devolvidos pela API; pode estar disponível ou indisponível.
- **Estimativa**: valor calculado pela interface a partir de um valor em R$ e de uma cotação exibida (BTC a comprar ou a vender), sempre rotulado como estimado e nunca exibido como resultado de operação.
- **Resultado de operação**: dados devolvidos pela API após depósito, compra ou resgate (incluindo liquidações e reinvestimento), exibidos sem alteração.
- **Lançamento de extrato**: tipo, valor, data/hora, BTC e cotação quando houver e identificador de solicitação.
- **Grupo de resgate**: conjunto de lançamentos com o mesmo identificador de solicitação, exibido como uma unidade com o total resgatado.
- **Ponto de histórico**: instante alinhado a 10 minutos com preço de compra e de venda; a ausência de um ponto esperado é uma **lacuna**.
- **Mensagem de erro**: texto em pt-BR associado a cada código de erro do contrato, a erros inesperados e à falta de conexão.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Uma pessoa sem conta consegue se cadastrar, depositar e concluir a primeira compra pela interface em menos de 3 minutos, no celular e no desktop.
- **SC-002**: Em 100% dos casos de teste, os valores em R$, BTC, cotações e percentuais exibidos contêm exatamente os mesmos dígitos devolvidos pela API (incluindo limites como 0,00000001 BTC e R$ 99.999.999.999,99), sem arredondamento nem notação científica.
- **SC-003**: 100% dos códigos de erro do contrato produzem a mensagem própria esperada, e 0 mensagens exibidas contêm detalhes técnicos (códigos, rastros, nomes internos).
- **SC-004**: 0 operações financeiras duplicadas em testes de clique duplo, toque duplo, Enter repetido, recarregamento e navegação voltar/avançar após o envio.
- **SC-005**: Com a fonte de cotação disponível e a atualização automática ativa, o instante da cotação exibida no painel nunca fica mais de 25 segundos atrás do horário atual.
- **SC-006**: Com a cotação indisponível, 100% das funções que não dependem dela (saldo, depósito, extrato, navegação, sair) continuam utilizáveis.
- **SC-007**: 100% dos fluxos (cadastro, login, sair, depósito, compra, resgate, investimentos, extrato, mercado) podem ser concluídos apenas com teclado e com leitor de tela, e uma auditoria de acessibilidade não encontra nenhuma violação WCAG 2.1 A ou AA.
- **SC-008**: Todas as telas são utilizáveis sem rolagem horizontal da página de 320 px a 1920 px de largura e com zoom de 200%.
- **SC-009**: Em 100% dos testes de expiração de sessão, a pessoa é levada ao login com a mensagem de sessão expirada e, após entrar, volta à mesma página com os mesmos filtros.
- **SC-010**: Em conexão móvel típica (4G: cerca de 9 Mbps de descida e 60 ms de latência): (a) em 95% das aberturas de tela com o aplicativo já carregado, o conteúdo principal (ou seu estado de carregando) aparece em até 1 segundo e os dados em até 3 segundos; (b) na primeira carga, sem cache, o estado de carregando aparece em até 1 segundo e o aplicativo fica utilizável em até 6 segundos.
- **SC-011**: Em teste de usabilidade, pelo menos 90% dos participantes concluem uma compra e um resgate na primeira tentativa, sem ajuda, e identificam corretamente que os valores da revisão são estimados.

## Assumptions

- **Fonte de verdade**: saldo, conversões, arredondamentos, ordem de liquidação, período padrão do extrato e totais vêm sempre da API; a interface só calcula estimativas rotuladas (FR-015, FR-023) e somas de exibição (FR-029).
- **Estimativa de resgate**: aproximada como valor ÷ cotação de compra atual; não reproduz a regra de liquidação por investimento da 001.
- **Estimativa de 1 satoshi** *(decidido em Clarifications)*: o aviso da revisão (FR-016) é orientativo e não bloqueia a confirmação; a recusa vem da API como valor inválido, porque o contrato não tem código específico para esse caso.
- **Envio duplicado** *(decidido em Clarifications)*: a proteção é feita apenas na interface (FR-043). O contrato da 001 não muda; com resultado incerto, a interface orienta a conferir saldo e extrato em vez de reenviar (FR-041). Idempotência na API e verificação automática do extrato ficam fora deste escopo.
- **Sessão**: não há renovação de token nem revogação no servidor (premissa da 001); "Sair" encerra a sessão apenas no navegador. *(decidido em Clarifications)* A sessão não sobrevive ao fechamento do navegador e não há opção "manter conectado". Se o navegador estiver configurado para restaurar a sessão anterior ao reabrir, ele pode restaurar também a sessão do aplicativo; nesse caso ela continua válida só até a expiração do token. O cenário US1-9 considera o navegador sem essa opção.
- **Retorno após login**: preserva a página e seus filtros, não valores digitados em formulários.
- **Saudação**: usa o primeiro nome (primeira palavra do nome cadastrado).
- **Depósito**: tem uma única etapa (sem revisão), pois não envolve cotação nem estimativa.
- **Atualização automática** *("Meus investimentos" decidido em Clarifications)*: aplica-se ao painel, a "Meus investimentos", à tela de resgate (disponível) e às telas de revisão de compra e resgate; "Mercado" carrega ao abrir e por ação explícita.
- **Tema**: segue a preferência do sistema, sem seletor manual.
- **Navegadores**: versões atuais dos principais navegadores de celular e desktop.
- **Extrato**: sem paginação, conforme a API.

## Dependencies

- **Feature 001 (API)**: contrato `specs/001-bitcoin-investment-api/contracts/openapi.yaml` como referência autoritativa e API em funcionamento.
- **Emenda na 001**: a consulta de perfil do cliente autenticado (FR-005) MUST ser especificada na 001 (spec e contrato) e implementada na API antes da entrega do painel com saudação.
- **Constituição 1.1.0**: princípios aplicáveis ao cliente web (camadas, regras de negócio na API, erros tipados, testes de consumidor contra o contrato, WCAG 2.1 AA, integridade de valores).

## Out of Scope

- Aplicativos nativos (Android, iOS, desktop).
- Exportação de extrato.
- Recuperação de senha e verificação de e-mail.
- Edição de perfil.
- Notificações dentro do app.
- Perfil administrativo.
- Funcionamento offline.
- Outros idiomas além de pt-BR.
- Seletor manual de tema.
- Idempotência de operações na API.
