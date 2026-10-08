# Plano de correções da API de e-commerce

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Corrigir falhas comprovadas sem alterar regras comerciais nem dados reais.
**Architecture:** Serviços transacionais existentes, domínio e constraints preservados. Etapas por fluxo com testes de regressão e revisão independente.
**Tech Stack:** Java 21, Spring Boot 4.0.3, PostgreSQL, Flyway, RabbitMQ, Maven.
**Spec:** docs/superpowers/specs/2026-10-08-ecommerce-audit-design.md

## Global Constraints
- Não editar migrations V1-V3; preservar V4 local e validar antes de incorporar.
- Sem cobrança real, force push, alterações em dados reais ou segredo em saída.
- Commits tipo(domínio): descrição, sem coautor; PR para develop.
- Autorização antecipada do usuário substitui pausa de aprovação da skill para este plano.

## Review Focus
- Senha multibyte deve respeitar capacidade real do BCrypt.
- Webhook repetido não pode baixar estoque novamente nem regredir entrega.
- Exclusão de produto com pedido preserva histórico e estoque no rollback.
- JSON malformado/null/overflow deve retornar erro de cliente sem mensagem interna.
- Integração nunca pode aceitar banco real por variável externa.

### Task 1: Segurança e contratos HTTP
**Files:** ResourceExceptionHandler.java, dto/request/UserRequest.java, LoginRequest.java, nova constraint UTF-8 em validation/, config/JwtAuthenticationFilter.java apenas sanitização de logs se necessária, testes novos de validação/handler.
**Interfaces:** Produz contratos compatíveis de senha e respostas 500 genéricas; não muda formato StandardError.
- [ ] Reproduzir vazamento de mensagem 500 e limitação real do BCrypt com teste.
- [ ] Implementar validação UTF-8 de 72 bytes no cadastro e login, preservando mínimos existentes; login aceita toda senha cadastrável. Rejeitar JSON inválido com 400 genérico.
- [ ] Rodar testes focados e revisar; commit fix(security).

### Task 2: Consistência comercial e persistência
**Files:** PaymentService.java, PaymentWebhookService.java, ProductService.java, entity/Product.java, dto/request/ProductRequest.java, OrderRequest.java, service/OrderService.java, migration V5__align_product_contract.sql, testes services/domain correspondentes.
**Interfaces:** Consome contratos existentes; produz POST pagamento repetível, webhook idempotente e catálogo consistente com schema.
- [ ] Escrever regressões de pagamento existente; eventos com novo ID em PAGO/ENVIADO/ENTREGUE/RECONCILIACAO_PENDENTE; null e overflow de SKU.
- [ ] Corrigir intenção usando lock do pedido; não baixar reserva já consumida, não reconciliar estado novamente.
- [ ] Excluir estoque antes de produto apenas quando não houver histórico; garantir flush e rollback.
- [ ] Preservar contrato de 500 caracteres via V5/JPA; @Digits(integer=36,fraction=2); categorias null com validação; null item inválido e overflow com resposta de cliente.
- [ ] Rodar regressões focadas e revisar; commits por assunto.

### Task 3: Execução reproduzível e CI
**Files:** pom.xml, Dockerfile, .dockerignore, docker-compose.yml, .github/workflows/main_ecommercevitinho.yml, novo workflow ci.yml, InventoryConcurrencyPostgresTest.java, README.md.
**Interfaces:** Consome Task 2/schema; produz suíte PostgreSQL isolada, Java 21 e OpenAPI compatível.
- [ ] Migrar teste para Testcontainers PostgreSQL, sem URL externa/destructive reset de banco existente; adicionar provas PostgreSQL para exclusão, limites de catálogo e migrations.
- [ ] Atualizar Spring Boot para patch 4.0.8 por advisories OSV e SpringDoc para linha 3 compatível; repetir scan de dependências, comprovar /v3/api-docs em app real de teste autenticado.
- [ ] CI em PR develop com Java 21 e Docker; deploy existente somente após validação, não ampliar alvo automaticamente.
- [ ] Volumes nomeados, portas locais de infra, runtime não root e dockerignore.
- [ ] README factual com configuração, Windows/Linux, pagamentos simulados, CI e limitações.
- [ ] Executar suíte/build, revisar; commits por assunto.

### Task 4: Outbox sem perda por rota ausente
**Files:** service/OutboxPublisher.java, application.properties, OutboxPublisherTest.java, novo OutboxRoutingRabbitIntegrationTest.java, README.md apenas seção outbox.
**Interfaces:** Consome outbox existente e schema; mantém payload orderId e adiciona messageId estável para deduplicação.
- [ ] Reproduzir com broker descartável: rota ausente devolve mensagem apesar de ACK; execução antiga marca evento publicado indevidamente. Probe real já confirmou BROKER_ACK=true RETURNED_UNROUTABLE=true em .superpowers/sdd/ecommerce-audit/outbox-route-probe.log.
- [ ] Usar confirmação correlacionada por evento e verificar returned antes de marcar; NACK, timeout, conexão indisponível ou retorno mantêm evento pendente via rollback. ID de mensagem é UUID persistido do outbox; não mudar payload JSON.
- [ ] Testar broker real para rota ausente, rota existente, messageId e retry; validar teste unitário de falha e rollback PostgreSQL existente.
- [ ] Executar testes focados e suíte, revisar; commit fix(outbox).

### Task 5: Revisão e publicação
**Files:** relatório docs/audit/2026-10-08-results.md e ledger ignorado.
- [ ] Auditar segredos atuais/histórico sem imprimir valores; consultar dependências em fonte primária/scanner.
- [ ] Revisão independente de requisitos e código completo; corrigir achados relevantes via agente.
- [ ] Atualizar base remota, revisar diff/mergeabilidade, push e PR para develop, CI remoto.
- [ ] Merge somente com checks aprovados e sem conflito funcional. Investigar implantação; redeploy somente compatível com configuração atual verificada. Registrar impedimentos sem contornar checks.

## Pendências de política
Revogação JWT imediata, rate limit distribuído, teto de paginação e paginação de listagens gerais permanecem riscos documentados até avaliação de contrato e implantação. Não declarar proteção inexistente.

