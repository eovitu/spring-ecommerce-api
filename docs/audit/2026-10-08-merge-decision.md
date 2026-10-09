# Conflitos de integração com develop

## Estado verificado
Worktree de correções: fix/ecommerce-audit, iniciado em 69197ab. Destino remoto: origin/develop f4d54ba. O comando git merge-tree --write-tree --name-only HEAD origin/develop detectou 28 arquivos conflitantes; não houve merge nem alteração do checkout por esse comando.

## Dois lados dos conflitos funcionais
| Fluxo | develop remoto | Implementação local mais recente |
|---|---|---|
| Criação de pedido | OrderService:63-70 recebe userId do request e usa setters de estado | Identidade autenticada, domínio com invariantes e reserva transacional de estoque |
| Pagamento | PaymentService:51-64 cria registro e marca pedido PAGO; update/delete disponíveis | Intenção pendente, confirmação autenticada por webhook, inbox e decisão de reconciliação |
| Estados comerciais | OrderService:94 recebe status arbitrário por update | Transições explícitas protegidas no domínio; expiração e histórico preservados |
| Persistência | Configuração legada e relações sem migrations novas | PostgreSQL, Flyway, validate, estoque/reservas e outbox |
| JWT | AuthService:60 gera token a partir do email | Claims de identidade/role assinadas e ownership no servidor |

## Recomendação e pendência
Recomenda-se integrar a evolução local validada preservando os contratos seguros de identidade, pagamento e estoque, avaliando separadamente recursos remotos ausentes. Isso exige decisão explícita sobre os conflitos funcionais; as instruções globais proíbem resolvê-los automaticamente. Publicação do PR é autorizada e continua. Merge e redeploy permanecem pendentes; CI verde não elimina esse impedimento.

Não substituir develop, não usar resolução global ours/theirs, não reescrever histórico e não integrar a branch remota sobre o checkout original.

## Decisão e integração em 09/10/2026

O usuário confirmou preservar a evolução local validada. O merge bd350d4 incorpora develop f4d54ba, com pais 4c32168 e f4d54ba. A rodada final teve 29 conflitos, incluindo CategoryRepository alterado pela correção posterior à contagem de 28. Todos foram examinados individualmente; não houve resolução global. Nove arquivos legados incompatíveis foram rejeitados; startup.sh e web.config compatíveis foram preservados como artefatos sem prova Azure. A suíte integrada executou 95 testes, zero falhas/erros/ignorados. Os impedimentos de decisão e conflito descritos acima foram superados; revisão da integração, CI remoto e deploy devem ser relatados separadamente.

