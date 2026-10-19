# Revogação global de sessões

Regras aprovadas pelo usuário em 09/10/2026. Autorização prévia para aplicar o plano, testar, revisar, publicar e integrar após checks continua válida. Frente própria a partir de develop 66c1446, separada da auditoria e das outras melhorias.

Todos os JWT de um usuário deixam de autenticar após commit de logout, mudança efetiva de senha, exclusão ou papel. Requisições autenticadas antes do commit não são canceladas. JWT novos levam sessionVersion inteira não negativa; tokens antigos sem a claim serão rejeitados. A invalidação dos tokens existentes é consequência necessária da política aprovada.

Escolha técnica: versão de sessão persistida por usuário e conferência no PostgreSQL em cada autenticação. Comparar usuário existente, versão, subject e papel atual com claims assinadas. Sem blacklist individual ou cache que atrase revogação. Falha de banco nunca autentica nem vira silenciosamente senha inválida.

Adicionar POST /auth/logout autenticado, sem corpo, retornando 204 após revogar todas as sessões. Regra explícita deve preceder /auth/**. JWT inválido/revogado retorna 401 genérico; manter os demais contratos existentes de rotas públicas e autorização.

Login, logout e alteração de usuário devem coordenar concorrência por lock do usuário. Atualizar perfil com a mesma senha não revoga por efeito do salt BCrypt; mudança real incrementa versão atomicamente. Exclusão recusada por FK preserva a sessão. Não criar endpoint de alteração de papel; divergência de papel atual invalida token, e qualquer futura operação controlada de papel deve incrementar versão na mesma transação.

Migration nova V6, preservando V1–V5. Coluna BIGINT não negativa, não usar @Version JPA como regra de sessão. Overflow falha fechado. Logs e erros sem email, senha, token ou detalhe do banco.

Aceites: HTTP/PostgreSQL real para dois tokens, senha nova/antiga, mesma senha, logout 204/401, exclusão bem-sucedida/recusada, role divergente, claim ausente/malformada, concorrência sem perda de incremento, migration preenchida e falha de banco. Suíte completa e CI remoto devem passar. Sem deploy real ou credenciais reais.
