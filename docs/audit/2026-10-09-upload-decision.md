# Imagens na integração

O usuário delegou a escolha técnica e pediu números em 09/10/2026. Decisão: manter `imageUrl` na entrega da auditoria e registrar upload como evolução própria, sem reativar a implementação legada.

Evidência em origin/develop f4d54ba: PhotoStorageService aceita quatro extensões (.jpg, .jpeg, .png, .webp); não verifica assinatura, MIME real ou dimensões. application-dev.properties configura 5 MB por arquivo/request. Não há remoção de arquivos antigos no serviço. O Compose atual tem volumes de PostgreSQL e Rabbit, mas nenhum de uploads da API.

Estimativa de capacidade: 1.000 arquivos no limite configurado de 5 MB representam aproximadamente 5 GB, sem contar imagens substituídas. Não há métricas de tráfego, quantidade de produtos ou ocupação real disponíveis. Não foi medido desempenho nem custo de provedor.

A [OWASP](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html) recomenda validação de conteúdo, tamanho e armazenamento seguro. A [documentação Azure](https://learn.microsoft.com/en-us/azure/app-service/configure-custom-container) distingue armazenamento persistente do filesystem do container. A aplicação Azure atual não foi localizada; persistência de uploads nesse ambiente não é comprovada.

Reativar o legado acrescentaria endpoint, armazenamento e superfície de validação sem prova de persistência. Uma frente futura deve definir armazenamento, autorização ADMIN, conteúdo realmente aceito, limites de dimensões, substituição/limpeza, rollback e testes de acesso. Não há alegação de que URL externa resolva disponibilidade ou confiança do conteúdo.
