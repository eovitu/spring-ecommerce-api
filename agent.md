# Regras de Engenharia do Repositório

Estas regras são obrigatórias para qualquer agente ou colaborador que altere este repositório.

## Commits

- Use estritamente Conventional Commits, com tipos como `feat:`, `fix:`, `chore:` e `refactor:`.
- Escreva mensagens objetivas que descrevam a alteração realizada.
- Gere commits sem a tag `Co-authored-by`.

## Pull Requests

- Ao concluir uma funcionalidade ou correção, preencha `.github/pull_request_template.md` integralmente.
- Registre contexto, alterações, forma de teste e evidências visuais quando aplicáveis.

## Proteção da branch principal

- Trate `main` e `master` como branches protegidas.
- Faça todo novo código em uma branch separada.
- Use `feature/nome-da-feature` para funcionalidades.
- Use `fix/nome-do-bug` para correções.
- Integre mudanças à branch principal somente por Pull Request revisado.
