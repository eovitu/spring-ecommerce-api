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

## Concorrência de estoque

Toda mutação de quantidade de estoque existente — reserva no checkout, baixa após pagamento e liberação por expiração — ocorre depois de adquirir `PESSIMISTIC_WRITE` sobre os SKUs envolvidos, sempre em ordem crescente de identificador quando há mais de um produto. Essa é a única estratégia de concorrência do estoque: `Stock` não usa versão otimista porque hoje não existe outro caminho de atualização fora desse bloqueio.
