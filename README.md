# Spring E-Commerce API

API REST de catálogo, pedidos, reservas de estoque e pagamentos simulados. Java 21,
Spring Boot 4.0.8, PostgreSQL 16, Flyway, RabbitMQ e autenticação JWT.
Redis/cache ainda não está implementado. Não há integração com adquirente ou cobrança real.

## Configuração

Copie `.env.example` para `.env` e preencha `DB_PASSWORD`, `RABBITMQ_PASSWORD`,
`JWT_SECRET` e `WEBHOOK_SECRET` com valores próprios. Nenhum possui senha padrão.
A chave JWT deve conter pelo menos 32 bytes em Base64. Gere-a com
`openssl rand -base64 32` ou, no PowerShell:

```powershell
$jwtBytes = [byte[]]::new(32)
[System.Security.Cryptography.RandomNumberGenerator]::Fill($jwtBytes)
[Convert]::ToBase64String($jwtBytes)
```

O `.env` é ignorado pelo Git e pelo build Docker. Nunca publique valores preenchidos.
O Compose usa PostgreSQL e RabbitMQ internos; suas portas no host ficam em `127.0.0.1`.
A API atende na porta 8080. Os volumes `postgres-data` e `rabbitmq-data` persistem os dados.
`docker compose down` preserva volumes; não remova volumes de ambientes com dados necessários.

## Execução

Com Docker em execução, no Windows ou Linux:

```sh
docker compose up --build
```

Para rodar Java localmente, suba a infraestrutura com `docker compose up -d db rabbitmq`.
No `.env`, mantenha a URL PostgreSQL e o host RabbitMQ locais; o usuário RabbitMQ do Compose é
`ecommerce`, portanto configure `RABBITMQ_USERNAME=ecommerce` e `RABBITMQ_HOST=localhost`.

Linux:

```sh
./mvnw spring-boot:run
```

PowerShell:

```powershell
./mvnw.cmd spring-boot:run
```

Flyway aplica migrations versionadas; Hibernate valida o schema. Não edite migrations aplicadas.
O runtime da imagem executa como usuário `ecommerce`, sem root.
Os comandos acima descrevem execução local. `startup.sh` oferece o mesmo fluxo Maven no Linux;
`web.config` é um artefato histórico do Azure Windows, sem validação no ambiente implantado.
Nenhum deploy saudável foi comprovado nesta entrega.

## Contratos e documentação

`POST /auth/register` e `/auth/login` identificam o usuário. Use
`Authorization: Bearer <token>` nas rotas protegidas.
`GET /api/v1/products` e `/api/v1/categories` são públicos; mutações de catálogo exigem ADMIN.
`/v3/api-docs` e `/swagger-ui/index.html` permanecem protegidos; não se promete acesso anônimo ao Swagger.

Checkout reserva estoque por 15 minutos. `POST /payments` reutiliza a intenção existente,
sem confirmar pagamento. O webhook simulado exige segredo externo; confirmação após expiração
leva à reconciliação pendente. Não existe estorno automático nem integração financeira real.
Produtos novos começam com estoque zero. Não existe endpoint administrativo de reposição
nesta entrega. O catálogo recebe `imageUrl`; upload de arquivos permanece uma evolução separada.
Eventos de expiração ficam persistidos em outbox. Um evento só recebe `published_at` após ACK
correlacionado do RabbitMQ e ausência de retorno por rota inexistente. NACK, retorno, timeout,
interrupção ou indisponibilidade mantêm o lote pendente via rollback para nova tentativa.
A entrega é pelo menos uma vez: falhas após o envio e antes do commit podem gerar duplicatas.
O `messageId` é o UUID persistido do evento e permanece estável nas tentativas, permitindo
deduplicação pelo consumidor. O payload JSON com `orderId` foi preservado.
Um registro publicado comprova aceitação em uma fila pelo broker, não processamento pelo consumidor.

## Verificação e CI

```powershell
./mvnw.cmd -B clean verify
```

No Linux, use `./mvnw -B clean verify`. Java 21 e Docker acessível são obrigatórios.
Os testes PostgreSQL usam Testcontainers e bancos descartáveis, sem URL de banco externo.
A ausência de Docker falha a execução em vez de ignorar testes.
A suíte cobre migrations V1 a V7, concorrência de estoque, rollback, pagamento repetido/concorrente,
limites de catálogo e OpenAPI autenticado em servidor HTTP real.

O workflow CI verifica PRs para `develop` e pushes `fix/**` com Java 21.
O workflow Azure conserva seus gatilhos em `main`/execução manual e depende de verificação do build.
Sua execução bem-sucedida e o ambiente implantado não foram confirmados nesta auditoria.
A varredura OSV avalia dependências Maven resolvidas; não cobre imagens Docker nem substitui
análise da exposição de cada advisory.

## Riscos pendentes

Não há teto explícito para paginação. Segredos que tenham aparecido no histórico devem ser rotacionados pelo
responsável pelo ambiente; a remoção do código não comprova a rotação.
O relatório final da auditoria registra as demais pendências de segurança e entrega da outbox.

## Revogação de sessões

JWT novos incluem `sessionVersion` inteira não negativa. Cada autenticação consulta o usuário no PostgreSQL e compara versão, email/subject e papel atual. Tokens antigos sem essa claim deixam de autenticar após esta atualização.

`POST /auth/logout` exige Bearer válido, não recebe corpo e retorna 204 depois de revogar todas as sessões do usuário. Repetir com token revogado retorna 401. Uma senha efetivamente diferente também revoga todas as sessões; reenviar a mesma senha mantém a versão. Exclusão efetiva invalida tokens pela ausência do usuário, enquanto exclusão recusada por vínculos preserva a sessão. Login, atualização, exclusão e logout usam lock pessimista da mesma linha de usuário para serializar a emissão e a revogação.

A revogação vale após o commit; requisições já autenticadas em andamento continuam. Mudança de email ou divergência de papel também invalida tokens. Não há endpoint de alteração de papel; futuras operações controladas devem chamar `User.revokeSessions()` sob o mesmo lock e na mesma transação da alteração. Overflow de BIGINT aborta a operação. JWT inválido/revogado retorna 401 genérico; indisponibilidade do banco na autenticação retorna 503 genérico e nunca autentica. Rotas públicas e autorização por ownership mantêm seus contratos.

A migration V6 adiciona `tb_user.session_version` com valor inicial zero e constraint não negativa. V1–V5 permanecem imutáveis. A migration V7 adiciona o estado compartilhado de tentativas de login.

## Limite de tentativas de login

`POST /auth/login` conta falhas por IP e por conta na janela móvel de 15 minutos.
As falhas 1 a 9 retornam 401 genérico. A décima inicia bloqueio de 15 minutos e retorna
429 com `Retry-After` em segundos, arredondado para cima. Qualquer uma das duas chaves
bloqueada impede o login, inclusive com senha correta. Sucesso não zera falhas e tentativas
bloqueadas não prolongam o prazo. Conta ausente segue a mesma regra e executa BCrypt dummy.
O email mantém a identidade exata e a sensibilidade a caixa da busca existente.

O estado fica no PostgreSQL, em transação independente que confirma os contadores antes
de retornar 401/429. As duas chaves são bloqueadas em ordem determinística
antes da linha de usuário. Indisponibilidade da persistência retorna 503 genérico.
As chaves SHA-256 com domínio por IP/conta são pseudonimizadas, não anônimas; o limite
não persiste email, IP, senha ou token em claro e não exige segredo adicional.

O IP é o endereço remoto da conexão. `Forwarded` e `X-Forwarded-For` não são confiáveis;
`server.forward-headers-strategy=none` impede sua reescrita automática. Atrás de proxy,
clientes podem compartilhar o mesmo limite de IP. Um atacante também pode bloquear
uma conta temporariamente. Essas regras não representam proteção completa contra DDoS.

Uma tarefa a cada minuto remove até 500 chaves expiradas, com locks `SKIP LOCKED` e
exclusão condicional para preservar atualizações concorrentes. `jobs.login-cleanup.delay`
e `jobs.login-cleanup.initial-delay` permitem ajustar o intervalo em milissegundos.
A retenção cobre a última falha ou o bloqueio por 15 minutos; o tempo efetivo de remoção
depende do lote acumulado. Não foi medida capacidade de tráfego ou velocidade de limpeza.
