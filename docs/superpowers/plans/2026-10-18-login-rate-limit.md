# Plano de proteção do login

> Executar com superpowers:subagent-driven-development, um implementador e revisão independente por etapa.

**Goal:** aplicar o limite aprovado de dez falhas por IP/conta e bloqueio de quinze minutos.
**Architecture:** estado e locks PostgreSQL em transação que confirma o resultado antes de traduzir falha para HTTP.
**Tech Stack:** Java21, Spring Boot4, PostgreSQL, Flyway, Testcontainers.
**Spec:** docs/superpowers/specs/2026-10-18-login-rate-limit-design.md.

## Global Constraints

- Preservar V1–V6, contratos de sessões, pagamentos simulados e trabalho alheio.
- Sem secrets em saída, banco real, force-push, dependência paga ou mudança de política comercial independente.
- Usuário autorizou aplicação antecipada do plano, commits convencionais sem coautor, PR para develop e merge após revisão e checks aprovados.

## Review Focus

Falha de login não pode reverter contador. Locks de chaves precedem usuário e seguem ordem global. Comparação de identidade não muda cadastro. Header forjado não burla IP. Bloqueio não se prolonga por tentativa rejeitada; sucesso não limpa falhas. Cleanup não perde atualizações.

## Task 1: Implementar e provar limite compartilhado

Arquivos: AuthController/AuthService, novo serviço de tentativas e repositório JDBC coesos, exceção/handler429, V7, configuração apenas se necessária, testes HTTP/PostgreSQL e README.

- [ ] Escrever RED significativo para décima falha/contador persistido antes da mudança.
- [ ] Implementar janela móvel, transação, chaves pseudonimizadas, Retry-After e origem de IP confiável conforme spec.
- [ ] Testar falhas, sucesso, fronteiras temporais, conta inexistente, concorrência, contexto compartilhado, rollback, headers e cleanup.
- [ ] Executar focados, suíte completa/build, self-review, diff check e commit.

## Task 2: Revisar e publicar

- [ ] Revisão independente de spec, qualidade, transações e código completo da frente; corrigir achados e re-revisar.
- [ ] Fetch/diff/check, push e PR para develop; confirmar CI do commit final e mergeabilidade antes do merge autorizado.
- [ ] Registrar o que foi validado e limites, sem declarar deploy não realizado.
