# Plano de revogação de sessões

Spec: docs/superpowers/specs/2026-10-09-session-revocation.md. Stack Java21/Boot4/PostgreSQL/JWT. Execução pela skill subagent-driven-development, implementador único seguido de revisão independente e publicação. Usuário autorizou aplicação antecipada do plano e aprovou as regras.

## Restrições

Preservar V1–V5, pagamentos simulados, contratos de ownership e alterações de terceiros. Nenhum segredo em saída, banco externo, force-push ou cobrança real. PR para develop; commits convencionais sem coautor. Rate limit, idempotência e reposição ficam em frentes próprias.

## Task 1: Implementação e regressões

- [ ] RED das falhas de revogação; V6, User/sessionVersion, repository locks, emissão/validação JWT e logout autenticado.
- [ ] Coordenar login/update/logout/delete; mesma senha não revoga, senha diferente e role divergente revogam; falhas de persistência fecham autenticação.
- [ ] Regressões HTTP/PostgreSQL e concorrência; migration preservada; atualizar fixtures existentes intencionalmente afetadas e README.
- [ ] Focados, clean verify, diff check, self-review e commit por domínio.

## Task 2: Revisão e publicação

- [ ] Revisão independente ampla da frente e correções delimitadas com re-revisão.
- [ ] Fetch, diff contra develop, push e PR, CI do commit final, merge apenas aprovado e sem conflito.
- [ ] Registrar resultados reais e limites; nenhuma prova de deploy inferida do merge.
