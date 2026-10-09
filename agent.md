# Manifesto de Engenharia — E-commerce API

Este documento define as regras obrigatórias para agentes e colaboradores durante todo o ciclo de vida do projeto. Correção, segurança e consistência de dados têm prioridade sobre velocidade de entrega.

## 1. 🎯 O Projeto e Visão de Negócio

Esta é uma API de e-commerce crítica, projetada para evoluir com alta disponibilidade, escala horizontal e consistência forte nos fluxos financeiros e de estoque.

A arquitetura deve sustentar picos intensos de leitura no catálogo sem comprometer a integridade de pedidos, pagamentos e reservas. Decisões técnicas devem considerar concorrência, falhas parciais, reprocessamento e observabilidade desde o início.

### Stack alvo

- Java 21;
- Spring Boot;
- PostgreSQL como fonte de verdade transacional;
- Flyway para criação e evolução versionada do schema;
- Redis para cache do catálogo e categorias;
- RabbitMQ para mensageria assíncrona;
- Transactional Outbox para publicação confiável de eventos.

## 2. 🏛️ Princípios Arquiteturais e DDD

### Entidades ricas e invariantes

- Modele `Order`, `Payment` e os demais agregados como entidades ricas.
- Não use `@Setter` no nível da classe em entidades de domínio.
- Altere estado por métodos de negócio com nomes explícitos, como `confirmarPagamento()`, `cancelar()` e `reservarEstoque()`.
- Cada método de negócio deve validar estado atual, transição permitida e invariantes antes de modificar a entidade.
- Construtores e fábricas devem produzir objetos válidos; estados parcialmente inicializados não podem escapar para o restante da aplicação.
- Preserve histórico comercial. Relacionamentos com pedidos, itens e pagamentos não devem usar cascade delete que permita apagar registros financeiros acidentalmente.

### Limites e responsabilidades

- Controllers tratam HTTP, validação estrutural e tradução de contratos.
- Casos de uso coordenam autorização, transações e dependências externas.
- O domínio protege regras e transições de estado.
- Repositórios e adaptadores encapsulam persistência, cache e integrações.
- DTOs transportam dados; não concentram regras de negócio.
- Mudanças arquiteturais devem preservar contratos existentes ou tornar incompatibilidades explícitas.

### Resiliência e idempotência

- Pagamentos, webhooks e consumidores de mensageria devem ser idempotentes.
- Atualizações de pedido, pagamento e inbox do webhook devem participar da mesma transação quando formarem uma única decisão de negócio.
- Eventos destinados ao RabbitMQ devem ser persistidos na mesma transação da mudança de negócio por meio de Transactional Outbox.
- Constraints do banco são a última linha de defesa contra concorrência; validações somente em memória não são suficientes.
- Operações concorrentes devem definir estratégia de lock, ordem determinística de aquisição e política limitada de retry.

### Segurança first

- Em ações autenticadas, derive sempre a identidade do usuário do `SecurityContext` preenchido pelo JWT.
- Nunca confie em `userId`, role ou ownership enviados no payload, query string ou headers controlados pelo cliente.
- Autorize o caso de uso no backend e valide ownership do recurso consultado ou modificado.
- Secrets são obrigatórios por ambiente e não podem possuir fallback conhecido em produção.
- Autenticação identifica o usuário; autorização determina cada ação permitida.

## 3. 🛠️ Regras Comportamentais do Agente

### Regra de ouro: use e abuse de Skills e Tools

- Use exaustivamente `find`, `grep`, `read_file` e listagem de diretórios, ou equivalentes disponíveis como `rg` e leitura pelo terminal, antes de alterar arquivos.
- Descubra caminhos, pacotes, métodos, contratos e convenções no repositório. Não adivinhe elementos que podem ser verificados.
- Leia integralmente as Skills aplicáveis antes de executar ações cobertas por elas.
- Quando faltar uma verificação necessária, crie e execute um script mínimo e reproduzível. Crie uma Skill somente quando o procedimento for reutilizável e tiver escopo claro.
- Preserve alterações locais existentes e mantenha cada mudança restrita ao objetivo solicitado.

### Test-to-Spec

- Entenda requisitos, endpoints, contratos, invariantes e schema antes de escrever código.
- Para comportamento novo ou correção, escreva primeiro um teste que expresse a especificação e confirme que ele falha pela razão esperada.
- Implemente a menor mudança coerente para o teste passar e só então refatore.
- Testes não substituem análise: confirme que eles verificam o comportamento de negócio correto.

### Verificação ativa

- Após alterar código, valide imports, tipos, assinaturas, mappings JPA e chamadas de banco.
- Execute os testes relevantes e, conforme o impacto, `mvn test`, build, validação de migrations e testes de integração com PostgreSQL.
- Para concorrência e transações, verifique rollback, repetição, conflito simultâneo e falha entre operações.
- Inspecione o diff antes de concluir; cada linha alterada deve estar ligada ao requisito.
- Relate separadamente o que foi implementado, o que foi efetivamente verificado e o que permanece pendente.

## 4. 🌿 Git Flow e Governança

### Proteção da branch principal

- `main` e `master` são branches protegidas: nenhum commit pode ser feito diretamente nelas.
- Crie uma branch antes de alterar código versionado.
- Use `feature/nome-da-feature`, `fix/nome-do-bug` ou `chore/nome-da-tarefa` conforme a natureza da mudança.
- Integre mudanças à branch principal somente por Pull Request revisado e com os checks obrigatórios aprovados.

### Commits

- Siga estritamente Conventional Commits, usando tipos como `feat:`, `fix:`, `refactor:`, `test:`, `docs:` e `chore:`.
- Mantenha commits coesos, revisáveis e com mensagem objetiva.
- Não inclua a tag `Co-authored-by`.

### Pull Requests

- Todo fechamento de feature ou correção deve preencher `.github/pull_request_template.md`.
- O PR deve registrar contexto, alterações, como testar, riscos e evidências aplicáveis.
- Não desabilite checks para obter aprovação; corrija a causa da falha.
