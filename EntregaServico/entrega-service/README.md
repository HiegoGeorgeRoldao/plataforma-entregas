# entrega-service

Serviço Java 21 e Spring Boot responsável por receber solicitações de entrega,
consultar seu estado e aplicar o resultado da análise. Usa PostgreSQL, Redis,
Kafka, Liquibase e o módulo compartilhado `messaging-core`.

## Papel no fluxo

1. `POST /entregas` grava a entrega e um evento `EntregaSolicitada` na Outbox do
   PostgreSQL, dentro da mesma transação. A API responde `202 Accepted`.
2. Um publicador agendado consulta a Outbox a cada 3 segundos, por padrão, e
   envia os eventos pendentes para `entrega.solicitada.v1` no Kafka.
3. O `analise-service` consome a solicitação, consulta ViaCEP e CPTEC e publica
   `EntregaAnalisada` em `entrega.analisada.v1`.
4. Este serviço consome a análise, deduplica pelo `eventId`, atualiza a entrega
   e grava `EntregaAtualizada` na Outbox. O publicador envia esse evento para
   `entrega.atualizada.v1`, consumido pelo `notificacao-service`.

`messaging-core` fornece o envelope `IntegrationEvent` e a configuração
compartilhada de tópicos, retentativas e DLT. A gravação transacional da Outbox,
as consultas e a publicação dos eventos são responsabilidades deste serviço.
Os nomes dos tópicos acima são os valores padrão e podem ser configurados por
variáveis de ambiente.

## Estrutura do código

| Pacote | Responsabilidade |
|---|---|
| `controller` | Endpoints HTTP e validação dos parâmetros |
| `service` | Casos de uso, cota diária, processamento da análise e Outbox |
| `repository` | Consultas JPA, SQL de reserva da Outbox e acesso ao Redis |
| `entity` | Entrega e evento persistido na Outbox |
| `dto` | Contratos de entrada e resposta da API |
| `kafka/consumer` | Recepção de `EntregaAnalisada` |
| `kafka/producer` | Agendamento da publicação da Outbox |
| `config` | Importação da configuração Kafka de `messaging-core` |

## Executar localmente

Requisitos: JDK 21, Docker iniciado e `messaging-core` instalado no repositório Maven local.
Para **executar a aplicação**, use o `compose.yaml` da raiz de plataforma-entregas. No
diretório `plataforma-entregas`, suba a infraestrutura. Depois de instalar `messaging-core`, abra o `pom.xml` deste serviço no IntelliJ:

```powershell
docker compose up -d --wait postgres redis kafka
```

Execute `EntregaServiceApplication` no IntelliJ com JDK 21. Para executar pelo
Maven em um checkout novo, instale antes os módulos necessários no repositório
Maven local, a partir da raiz:

```powershell
mvn -B -ntp -f EntregaServico/entrega-service/pom.xml install -DskipTests
mvn -f EntregaServico/entrega-service/pom.xml spring-boot:run
```

A API usa `http://localhost:8080`. Por padrão, conecta ao PostgreSQL em
`localhost:5433/entregas`, Redis em `localhost:6379` e Kafka em `localhost:9092`.
O Liquibase cria e atualiza o schema no início da aplicação.

As principais variáveis de ambiente são `DB_URL`, `DB_USER`, `DB_PASSWORD`,
`REDIS_HOST`, `REDIS_PORT`, `KAFKA_BOOTSTRAP_SERVERS`, `SERVER_PORT`,
`DAILY_LIMIT`, `MESSAGING_ENABLED` e `OUTBOX_ENABLED`. O `USUARIO_LOCAL_ID`
é uma identidade fixa para o MVP; ainda não há autenticação real. O limite
padrão é de 20 solicitações por usuário a cada dia, no fuso de São Paulo.

## Endpoints

| Método e caminho | Uso |
|---|---|
| `POST /entregas` | Solicita uma entrega com `cepOrigem` e `cepDestino` de oito dígitos |
| `GET /entregas/{id}` | Consulta o estado da entrega |
| `GET /entregas` | Lista entregas com `pagina`, `tamanho`, `busca` e `status` opcionais |
| `GET /entregas/resumo` | Conta entregas por estado |
| `GET /entregas/cota` | Consulta o saldo da cota diária |
| `GET /entregas/{id}/eventos` | Mostra os eventos registrados na Outbox |
| `GET /actuator/health` | Verifica a saúde da aplicação |

Na listagem, `pagina` começa em zero e `tamanho` aceita de 1 a 50. O campo
`status` aceita `SOLICITADA`, `ANALISADA` ou `REJEITADA`. A resposta contém
`itens`, `total`, `pagina` e `tamanho`. A análise é assíncrona; após o POST,
o estado pode permanecer `SOLICITADA` por alguns segundos.

Exemplo de solicitação:

```http
POST http://localhost:8080/entregas
Content-Type: application/json

{"cepOrigem":"01001000","cepDestino":"20040002"}
```

Use o `id` retornado em `GET /entregas/{id}`. O endpoint de eventos mostra o
estado de publicação da Outbox; um evento ainda `PENDING` não chegou ao Kafka.

## Testes e PostgreSQL temporário

Os testes que acessam o banco são **testes de integração**. O Testcontainers
inicia um PostgreSQL temporário pelo Docker e o Liquibase aplica nele os mesmos
scripts usados pela aplicação. Consultas e comandos SQL dos testes executam
nesse banco isolado, sem alterar o PostgreSQL local em `localhost:5433`.

**Não é necessário um Dockerfile ou `compose.yaml` dentro deste serviço para os
testes.** As classes `TestcontainersConfiguration` e `RateLimitIntegrationTests`
declaram as imagens PostgreSQL e Redis; o Testcontainers cria e remove os
contêineres automaticamente durante a execução do Maven. Basta iniciar o
Docker Desktop e verificar se o Docker responde com `docker info`. O
`compose.yaml` da raiz sobe a infraestrutura. O Dockerfile dentro deste serviço
empacota o JAR gerado pelo Maven.

O PostgreSQL é necessário para testar recursos usados pelo schema e pela
Outbox, como `JSONB`, índices parciais, `FOR UPDATE SKIP LOCKED`, `RETURNING`
e `ON CONFLICT`. O H2 não reproduz fielmente essas consultas e as regras de
concorrência. O teste de cota também inicia um Redis temporário. Nos testes
de integração do serviço, os listeners Kafka são desabilitados; algumas falhas
são simuladas para conferir rollback e nova tentativa.

Com Docker iniciado, execute na raiz de plataforma-entregas, sem rodar `docker compose up`:

```powershell
mvn -B -ntp -f EntregaServico/entrega-service/pom.xml test
```

A biblioteca `messaging-core` deve ser instalada antes, conforme o README da raiz.
O parâmetro `-DskipTests` pula os testes. Os contêineres de teste são
descartáveis; não é preciso criar um banco manualmente no DBeaver.

## Limitações atuais

O usuário é definido por configuração local. O controle de cota no Redis
ocorre antes da gravação da entrega e uma falha posterior não devolve a
tentativa consumida. Como o envio ao Kafka e a confirmação no banco são etapas
separadas, um evento pode ser reenviado; o consumidor precisa ser idempotente.
