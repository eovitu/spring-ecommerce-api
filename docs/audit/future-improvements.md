# Melhorias futuras

Solicitadas em 09/10/2026. Esta lista registra evolução proposta, não funcionalidades implementadas. A auditoria e a publicação das correções atuais continuam separadas dessas decisões.

| Frente | Decisão necessária | Aceite proposto |
|---|---|---|
| Revogação de sessões | Quando invalidar tokens: logout, senha, exclusão e mudança de papel; invalidar uma sessão ou todas | Token revogado falha; outra sessão segue a política escolhida; expiração e atualização de papel têm regressões |
| Rate limit | Limite, janela, bloqueio, chave por IP/conta e armazenamento compartilhado | Excesso retorna 429; expiração libera; instâncias compartilham limite; proxy não permite falsificar identidade |
| Idempotência de pedidos | Header/chave, validade, escopo por usuário e resposta para payload diferente | Repetição e concorrência criam um pedido e uma reserva; chave de outro usuário é isolada; conflito de payload é explícito |
| Reposição administrativa | Quem pode repor, ajuste absoluto ou incremento, motivo/auditoria e interação com reservas | Apenas papel autorizado altera; concorrência preserva reservas; histórico registra ator, quantidade e motivo |
| Integração financeira real | Provedor, meio de pagamento, ambiente sandbox, valores, webhooks, reconciliação, estorno e credenciais | Sandbox sem dinheiro real; assinatura e valor verificados; retries idempotentes; timeout e reconciliação testados |

Regras aprovadas pelo usuário em 09/10/2026: invalidar todos os tokens após troca de senha, exclusão, mudança de papel e logout; login permite até 10 falhas por IP e conta em 15 minutos, com bloqueio 429 de 15 minutos e contadores compartilhados no PostgreSQL; Idempotency-Key opcional por usuário, válida por 24 horas, payload diferente retorna 409; reposição somente ADMIN por incremento positivo, com motivo e histórico. Implementação em frentes próprias após a auditoria, com plano e testes.

Pagamento continuará simulado nesta entrega, por escolha explícita do usuário. Integração financeira permanece futura e nunca reutiliza pedidos ou pagamentos reais para testes.

Ordem sugerida: sessões e proteção de login; idempotência; reposição; integração financeira. Cada frente deve ter especificação, regressões e PR próprios. Essa ordem é uma proposta, não uma decisão de produto.
