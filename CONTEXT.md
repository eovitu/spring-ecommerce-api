# E-commerce

Este contexto reúne os conceitos que preservam a consistência comercial entre pedidos, pagamentos e estoque.

## Language

**Estoque**:
Quantidade total de um produto, da qual uma parte pode estar temporariamente reservada para pedidos ainda não pagos.
_Evite_: Saldo, disponibilidade

**Reserva de estoque**:
Compromisso temporário de uma quantidade de produto para um pedido, válido por quinze minutos e consumido somente após confirmação do pagamento.
_Evite_: Bloqueio, pré-baixa

**Reconciliação de pagamento**:
Análise manual exigida quando um pagamento é confirmado depois que sua reserva deixou de garantir o produto.
_Evite_: Estorno automático, pagamento confirmado

**Evento de cancelamento por expiração**:
Registro durável de que um pedido foi cancelado porque sua reserva de estoque expirou.
_Evite_: Notificação de cancelamento
