<!--
Essa constituição é a camada de governança do projeto: 
o conjunto de princípios inegociáveis que toda spec, todo plan, 
toda lista de tarefas e toda linha de implementação precisam respeitar. 
Governança, aqui, é o nível das regras que estão acima das decisões do dia a dia 
e que decidem o que é aceitável em todas elas.
-->

<!--
Nesse documento foi utilizado a linguagem normativa que é um conjunto de termos padronizados utilizados em documentos técnicos, 
normas e especificações para definir o nível de obrigatoriedade de um requisito.
Essa convenção foi popularizada principalmente pela RFC 2119 da IETF (Internet Engineering Task Force), 
um documento amplamente adotado em engenharia de software, contratos, redes e documentações de API para evitar ambiguidades.
MUST / SHALL / REQUIRED ("Deve" / "Obrigatório")
MUST NOT / SHALL NOT ("Não deve")
SHOULD / RECOMMENDED ("Deve" / "Recomendado")
SHOULD NOT / NOT RECOMMENDED ("Não deve" / "Não recomendado")
MAY / OPTIONAL ("Pode" / "Opcional")
-->

# Investimento em Bitcoins Constitution

API de investimento em bitcoins. O escopo inicial do produto inclui cadastro de usuário
(nome, e-mail e senha) e autenticação por login com emissão de token JWT. Este documento
define exclusivamente princípios permanentes de engenharia e arquitetura; decisões de
tecnologia e implementação pertencem ao Plan.

As palavras-chave MUST, MUST NOT, SHOULD, SHOULD NOT e MAY devem ser interpretadas conforme
a RFC 2119.

## Core Principles

### I. Arquitetura em Camadas

- A aplicação MUST ser organizada em camadas com responsabilidades distintas: domínio,
  aplicação (casos de uso), interface (entrada/saída) e infraestrutura.
- As dependências MUST apontar sempre para o domínio. O domínio MUST NOT depender de
  nenhuma outra camada.
- A camada de aplicação MUST depender apenas do domínio e de abstrações (contratos) que
  ela própria define; implementações concretas MUST residir na infraestrutura.
- A composição das dependências (instanciação e ligação entre abstrações e
  implementações) MUST ocorrer exclusivamente na borda da aplicação (ponto de entrada).
- Camadas MUST NOT acessar detalhes internos de camadas não adjacentes, contornando os
  contratos estabelecidos.

**Rationale**: evita acoplamento entre regras de negócio e detalhes técnicos, permitindo
substituir infraestrutura ou interface sem reescrever o núcleo e mantendo o sistema
testável de forma isolada.

### II. Lógica de Negócio Isolada

- Toda regra de negócio MUST residir no domínio ou nos casos de uso da aplicação.
- A camada de interface MUST se limitar a receber e validar o formato das entradas,
  delegar a execução aos casos de uso e apresentar os resultados.
- A camada de interface MUST NOT conter decisões de negócio (ex.: cálculo de saldo,
  regras de investimento, políticas de autenticação).
- A infraestrutura MUST NOT conter regras de negócio; ela apenas persiste, transporta ou
  integra dados conforme contratos definidos pela aplicação.
- Regras de negócio MUST ser executáveis e testáveis sem interface e sem infraestrutura
  reais.

**Rationale**: evita duplicação e divergência de regras espalhadas por controladores,
adaptadores e repositórios, garantindo uma única fonte de verdade para o comportamento do
negócio.

### III. Erro como Valor

- Falhas previsíveis (validação, regra de negócio violada, recurso inexistente,
  credenciais inválidas, saldo insuficiente etc.) MUST ser representadas por resultados
  explícitos de sucesso ou erro, retornados como valor.
- Exceções MUST NOT ser usadas para controle de fluxo nem propagadas entre camadas.
- Exceções MUST permanecer restritas às fronteiras com a infraestrutura, onde MUST ser
  capturadas e convertidas em resultados explícitos antes de cruzar para a aplicação.
- Os tipos de erro MUST ser parte do contrato público de cada operação e MUST ser
  tratados explicitamente por quem os consome.
- A camada de interface MUST mapear cada tipo de erro para uma resposta coerente e
  previsível ao cliente, sem expor detalhes internos.

**Rationale**: evita fluxos de erro ocultos, falhas não tratadas e acoplamento implícito
entre camadas, tornando todos os caminhos de execução visíveis, tipáveis e testáveis.

### IV. Desenvolvimento Guiado por Testes

- Todo comportamento novo ou alterado MUST ser especificado por testes automatizados
  escritos antes ou durante sua implementação.
- O desenvolvimento MUST seguir o ciclo Red → Green → Refactor em pequenas iterações.
- Regras de domínio e casos de uso MUST possuir testes unitários independentes de
  infraestrutura.
- Contratos externos da API (endpoints, formatos de requisição/resposta e códigos de
  status) MUST possuir testes de integração ou de contrato.
- Toda correção de defeito MUST ser acompanhada de um teste que reproduza o defeito.
- Código MUST NOT ser integrado à linha principal com testes falhando.

**Rationale**: evita regressões, garante que requisitos sejam verificáveis e torna a
refatoração segura e contínua.

### V. Simplicidade

- Toda funcionalidade MUST ser justificada por um requisito presente na Spec; o que não
  for necessário ao escopo MUST NOT ser implementado (YAGNI).
- Abstrações MUST ser introduzidas apenas quando houver necessidade concreta (ex.:
  isolamento de infraestrutura ou testabilidade), nunca por antecipação especulativa.
- O código SHOULD priorizar legibilidade sobre engenhosidade e baixo acoplamento sobre
  reutilização prematura.
- Qualquer complexidade adicional MUST ser justificada explicitamente no Plan.

**Rationale**: evita sobre-engenharia, custos de manutenção desnecessários e código
difícil de compreender, mantendo o projeto evolutivo e fácil de modificar.

### VI. Agnosticismo Tecnológico

- Esta Constitution MUST NOT impor linguagem, framework, biblioteca, banco de dados,
  arquitetura de interface, padrão de gerenciamento de estado, mecanismo de injeção de
  dependência ou qualquer tecnologia específica.
- Escolhas tecnológicas MUST ser feitas e justificadas exclusivamente no Plan, e MUST
  respeitar os princípios desta Constitution.
- Domínio e casos de uso MUST NOT depender diretamente de tecnologias específicas;
  tecnologias MUST ser acessadas por meio de contratos definidos pela aplicação.

**Rationale**: evita que a Constitution se torne obsoleta com mudanças de tecnologia e
impede que decisões técnicas passageiras contaminem princípios permanentes.

## Engineering Standards

O projeto adota os seguintes padrões gerais, aplicados em conjunto com os princípios acima:

- **SOLID**: módulos MUST ter responsabilidade única, ser abertos para extensão e fechados
  para modificação, respeitar substituição de Liskov, expor interfaces enxutas e depender
  de abstrações em vez de implementações concretas.
- **Clean Code**: nomes MUST expressar intenção; funções SHOULD ser pequenas e coesas;
  duplicação SHOULD ser eliminada quando representar o mesmo conhecimento; comentários
  SHOULD explicar o "porquê", não o "o quê".
- **Clean Architecture**: a regra de dependência (Princípio I) é inegociável; entidades e
  casos de uso MUST permanecer independentes de detalhes de entrega e persistência.
- **Test-Driven Development**: conforme Princípio IV; a cobertura MUST priorizar regras de
  negócio e contratos públicos.
- **Error as Value**: conforme Princípio III; todo erro previsível MUST ser modelado
  explicitamente.
- **Separação de Responsabilidades**: cada componente MUST ter um único motivo para mudar;
  validação de formato, regra de negócio, persistência e apresentação MUST NOT ser
  misturadas.
- **Padrões de Arquitetura**: o projeto MAY adotar padrões como DDD, Hexagonal (Ports and
  Adapters), Onion, Clean Architecture ou CQRS, desde que compatíveis com esta
  Constitution. A adoção MUST ser justificada no Plan e SHOULD ser aplicada apenas na
  medida em que o escopo exigir (Princípio V).
- **Semântica HTTP**: a API MUST usar verbos HTTP conforme sua semântica (GET para leitura
  sem efeitos colaterais; POST para criação ou ações; PUT/PATCH para atualização total ou
  parcial; DELETE para remoção) e MUST retornar códigos de status corretos (ex.: 200, 201,
  204, 400, 401, 403, 404, 409, 422, 500). Respostas de erro MUST seguir um formato
  padronizado e consistente em toda a API.
- **Segurança e Dados Sensíveis**: senhas MUST NOT ser armazenadas ou registradas em texto
  plano; credenciais e segredos MUST NOT residir no código-fonte; rotas protegidas MUST
  exigir token válido; respostas e logs MUST NOT expor dados sensíveis ou detalhes
  internos.
- **Integridade de Valores Financeiros**: valores monetários e quantidades de criptoativos
  MUST ser representados com precisão exata, sem perdas por arredondamento implícito.
- **Padronização e Sintaxe do Código**: o projeto MUST definir no Plan convenções de
  nomenclatura, formatação e estrutura de diretórios, e MUST aplicá-las de forma
  automatizada e verificável; todo código MUST segui-las de maneira uniforme.

## Workflow

O desenvolvimento MUST seguir o fluxo abaixo, em ordem:

1. **Constitution** — define os princípios permanentes que governam todas as etapas.
2. **Spec** — descreve o que deve ser construído e por quê, sem decisões técnicas.
3. **Plan** — define como construir: tecnologias, arquitetura e justificativas, com
   verificação explícita de conformidade com esta Constitution.
4. **Tasks** — decompõe o Plan em tarefas pequenas, ordenadas e verificáveis, incluindo as
   tarefas de teste.
5. **Implementação** — executa as tarefas em pequenas iterações, seguindo o ciclo TDD.
6. **Testes** — valida que todos os testes automatizados passam e que os critérios de
   aceitação da Spec são atendidos.
7. **Revisão** — verifica a conformidade do código com a Spec, o Plan e esta
   Constitution antes da integração.

Nenhuma etapa SHOULD ser pulada; violações justificadas MUST ser registradas no Plan.

## Governance

- **Supremacia**: esta Constitution prevalece sobre quaisquer outras práticas, guias ou
  convenções do projeto. Toda Spec, Plan, Task e revisão MUST verificar conformidade com
  ela.
- **Versionamento semântico**: a Constitution MUST seguir o formato MAJOR.MINOR.PATCH:
  - **MAJOR**: remoção ou redefinição incompatível de princípios ou regras de governança.
  - **MINOR**: adição de princípio ou seção, ou expansão material de orientação existente.
  - **PATCH**: esclarecimentos, ajustes de redação e correções sem efeito semântico.
- **Processo de emendas**: toda emenda MUST ser proposta por escrito contendo a alteração
  pretendida, a motivação, a classificação de versão e o impacto sobre artefatos
  existentes (Specs, Plans, Tasks e código). A emenda MUST ser revisada e aprovada antes
  de entrar em vigor, e MUST atualizar a versão e a data de última emenda.
- **Compatibilidade entre versões**: alterações MINOR e PATCH MUST ser retrocompatíveis
  com artefatos produzidos sob a versão anterior. Alterações MAJOR MUST incluir um plano
  de migração descrevendo como os artefatos existentes serão adequados.
- **Revisão obrigatória**: sempre que uma alteração arquitetural permanente ocorrer, esta
  Constitution MUST ser revisada para confirmar sua aderência ou ser emendada de acordo.
- **Conformidade**: violações de princípios MUST ser justificadas explicitamente no Plan
  (seção de verificação de conformidade) ou corrigidas antes da integração.

**Version**: 1.0.0 | **Ratified**: 2026-10-27 | **Last Amended**: 2026-10-27
