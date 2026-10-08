# Auditoria e correções da API de e-commerce

## Objetivo e autorização
Entregar uma API Java segura, consistente, testada e apresentável em entrevistas. Preservar comportamento comercial atual: pagamentos são intenções locais e confirmação por webhook de teste, sem gateway financeiro real. O usuário autorizou antecipadamente a execução do plano concreto em 08/10/2026, inclusive commits, PR e merge validado. Não alterar dados reais, credenciais nem resolver conflitos funcionais sem decisão.

## Base e isolamento
Base local mais recente: feature/phase2-inventory, 69197ab, 11/09/2026. Remota develop: f4d54ba, 20/05/2026. Há divergência funcional entre as branches; PR destina-se a develop e merge depende da ausência de conflitos. Trabalho em fix/ecommerce-audit, worktree isolado. Quatro arquivos locais de estoque foram copiados sem modificar o checkout original. Verificar e incorporar apenas após validar concorrência/migration.

## Abordagem
Correções por fluxo existente, sem reescrita arquitetural. Alternativas descartadas: reconstruir toda a API aumenta risco sem requisito; corrigir somente README deixaria falhas executáveis. Etapas independentes com regressão antes da mudança e revisão após cada etapa.

## Achados e decisões
| ID | Classe | Evidência inicial | Falha/impacto | Correção e prova |
|---|---|---|---|---|
| S1 | Confirmado P2 | ResourceExceptionHandler:102 | 500 expõe mensagem interna | Mensagem genérica; teste com marcador sensível |
| S2 | Confirmado P2 | UserRequest:76 / LoginRequest:22 | Limites 128/100 incompatíveis com BCrypt | Limite UTF-8 de 72 bytes coerente nos contratos; regressão ASCII/multibyte e encoder |
| D1 | Confirmado P2 | PaymentService:63 / OrderService:107 | POST pagamento repete intenção automática e falha | Retornar intenção existente sob lock; testar repetição |
| D2 | Confirmado P1 | PaymentWebhookService:73-90 | Novo evento após envio/reconciliação falha | Idempotência por estado já confirmado; testar enviado, entregue e reconciliação |
| D3 | Confirmado P2 | ProductService:182 | FK de estoque impede exclusão sem pedidos | Excluir estoque transacionalmente preservando histórico; teste PostgreSQL |
| D4 | Confirmado P2 | ProductRequest:26,34 / V1:26,28 | Contrato 500 caracteres excede varchar(255) | Migration corretiva para varchar(500), alinhar JPA e roundtrip |
| D5 | Confirmado P2 | ProductRequest:28-31 | Preço arredondado/rejeitado no banco | @Digits(36,2); teste validação e roundtrip |
| D6 | Confirmado P2 | OrderRequest.items / OrderService:81 | Elemento null e overflow produzem 500 | Rejeitar null e overflow com erro de cliente; regressão |
| Q1 | Confirmado P1 | workflow:24 | java25 inválido | Java 21, CI em PR e banco descartável |
| Q2 | Compatibilidade confirmada, runtime a verificar | pom:37 | SpringDoc 2.x incompatível com Boot 4 | SpringDoc 3.x conforme matriz oficial; /v3/api-docs autenticado |
| Q3 | Confirmado P1 | InventoryConcurrencyPostgresTest:41,71-80 | Teste apaga banco fornecido | Banco PostgreSQL descartável via Testcontainers; executar sempre com Docker |
| Q4 | Confirmado P2 | compose:4-32 | Persistência não definida explicitamente | Volumes nomeados, portas locais, usuário não root, dockerignore |
| Q5 | Confirmado P2 | README:11,31,35 | Instruções MySQL contradizem PostgreSQL | README real, execução Windows, testes e limites conhecidos |

## Riscos e pendências separados
JWT não possui revogação imediata após alteração de senha/exclusão; login não tem rate limit local; paginação pública não possui teto. Avaliar controles proporcionais sem impor novas políticas comerciais. Listagens gerais de pedidos/pagamentos sem paginação e N+1 exigem evidência/contrato antes de mudar respostas. Auditoria de segredos inclui arquivos rastreados e histórico, nunca valores; credenciais expostas exigem rotação humana. Há workflow Azure mas últimos deploys consultados falharam; saúde e configuração reais não comprovadas. Não redeployar PostgreSQL/RabbitMQ sobre configuração antiga sem confirmação de compatibilidade.

## Validação e publicação
Base atual: 53 testes descobertos, 48 executados, 5 ignorados; zero falhas/erros. Executar regressões unitárias, HTTP, PostgreSQL descartável, migrations completas, build e smoke OpenAPI. Revisão independente completa, CI remoto e mergeabilidade antecedem merge. Registrar status implementado/testado/publicado/merge/deploy separadamente.
