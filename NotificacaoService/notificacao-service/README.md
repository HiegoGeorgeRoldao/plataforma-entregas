# notificacao-service

Serviço Java 21 e Spring Boot que recebe atualizações de entregas pelo Kafka,
registra notificações no PostgreSQL e oferece endpoints para consulta e leitura.
Usa Spring JDBC com `JdbcClient`, Liquibase e a biblioteca `messaging-core`.

## Participação no fluxo

```text
Usuário -> entrega-service -> EntregaSolicitada -> Kafka
  -> analise-service -> EntregaAnalisada -> Kafka
  -> entrega-service -> EntregaAtualizada -> Kafka
  -> notificacao-service -> PostgreSQL
  -> GET /notificacoes
```

O fluxo começa com `POST /entregas` no `entrega-service`, na porta `8080`.
O `analise-service` consulta ViaCEP e CPTEC. Quando o resultado é aplicado à
entrega, o `entrega-service` publica `EntregaAtualizada` pela sua Outbox.

Este serviço consome o tópico `entrega.atualizada.v1` com o grupo
`notificacao-service-v1`. Valida versão, tipo e campos do evento, aceita os
estados `ANALISADA` e `REJEITADA` e registra uma notificação inicialmente não
lida. As notificações ficam disponíveis pela API na porta `8082`.

O serviço não envia e-mail ou SMS e não publica novos eventos de negócio.
Por isso, possui `kafka/consumer`, sem um producer ou Outbox próprios.

## Estrutura dos pacotes

| Pacote | Responsabilidade |
|---|---|
| `controller` | Receber HTTP, validar o limite e delegar ao service |
| `service` | Validar eventos, coordenar transações, consultar e marcar leitura |
| `repository` | Executar SQL com `JdbcClient` e parâmetros nomeados |
| `kafka/consumer` | Converter JSON em evento e chamar o handler |
| `config` | Importar a configuração Kafka da biblioteca compartilhada |
| `dto` | Definir o formato das respostas HTTP |
| `enuns` | Definir os estados permitidos em `NotificationStatus` |
| `filds` | Centralizar nomes do evento, campos do payload e colunas mapeadas |
| `exception` | Representar notificação não encontrada |

A controller não acessa o banco. Os services dependem dos repositórios e
mantêm as transações; SQL e mapeamento das linhas ficam em `repository`.

## Biblioteca messaging-core

O módulo depende de `br.com.hiego:messaging-core:0.0.1-SNAPSHOT`.
`IntegrationEvent` fornece o envelope dos eventos e seus métodos de validação.
`MessagingConfiguration` declara tópicos e DLTs e registra o tratamento de
falhas Kafka. Os valores padrão vêm de `kafka-defaults.yaml`, importado pela
configuração do serviço.

Falhas de consumo seguem o tratamento da biblioteca: por padrão, quatro
retentativas adicionais com intervalo de dois segundos e recuperação na DLT.
Contratos inválidos que geram `IllegalArgumentException` seguem diretamente
para recuperação. O tópico de falhas padrão deste consumidor é
`entrega.atualizada.v1.DLT`.

## Persistência e deduplicação

O Liquibase cria as tabelas `notificacao` e `processed_event` no início da
aplicação, usando [o changelog do serviço](src/main/resources/db/changelog/db.changelog-master.yaml).

O handler registra `eventId` em `processed_event` e insere a notificação na
mesma transação. Um erro de persistência reverte as duas operações. Eventos
já registrados são ignorados. A restrição única em `notificacao.entrega_id`
também impede criar mais de uma notificação para a mesma entrega.

O uso de `ON CONFLICT` preserva essas garantias mesmo com chamadas concorrentes.
As consultas e a marcação de leitura filtram pelo usuário configurado.

## Executar localmente

Requisitos: JDK 21, Maven 3.9+, Docker Desktop em modo Linux e `messaging-core` instalado localmente.
Os comandos abaixo devem ser executados na **raiz de plataforma-entregas**, que contém
o arquivo `compose.yaml`.

Para subir as dependências da aplicação:

```powershell
docker compose up -d --wait postgres-notificacao kafka
```

Abra o `pom.xml` deste serviço no IntelliJ e execute
`NotificacaoApplication` com JDK 21. Para executar pelo Maven em um checkout
novo, instale os módulos necessários no repositório local:

```powershell
mvn -B -ntp -f NotificacaoService/notificacao-service/pom.xml install -DskipTests
mvn -f NotificacaoService/notificacao-service/pom.xml spring-boot:run
```

A API fica em `http://localhost:8082`. Para gerar notificações pelo fluxo
completo, os serviços de entregas e análises também precisam estar em execução.

## Configuração

| Variável de ambiente | Valor padrão local | Uso |
|---|---|---|
| `SERVER_PORT` | `8082` | Porta HTTP |
| `DB_URL` | `jdbc:postgresql://localhost:5434/notificacoes` | Banco do serviço |
| `DB_USER` | `notificacoes` | Usuário PostgreSQL |
| `DB_PASSWORD` | `notificacoes_local` | Senha de desenvolvimento |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Endereço do Kafka |
| `MESSAGING_ENABLED` | `true` | Ativação da configuração e do listener Kafka |
| `TOPIC_ENTREGA_ATUALIZADA` | `entrega.atualizada.v1` | Tópico consumido |
| `EVENT_PUBLISHED_VERSION` | `1` | Versão esperada do envelope |
| `USUARIO_LOCAL_ID` | `11111111-1111-1111-1111-111111111111` | Usuário usado nas consultas HTTP |

O usuário local é uma identidade fixa para o MVP e ainda não representa uma
autenticação. Para consultar pelo fluxo local, os três serviços devem usar o
mesmo `USUARIO_LOCAL_ID`.

## Endpoints

| Método e caminho | Comportamento |
|---|---|
| `GET /notificacoes` | Lista notificações do usuário local |
| `PATCH /notificacoes/{id}/leitura` | Marca uma notificação como lida; retorna `204` |
| `GET /actuator/health` | Consulta a saúde da aplicação |
| `GET /actuator/info` | Consulta as informações disponibilizadas pelo Actuator |

A listagem aceita `limit` de 1 a 100, com padrão 50, e o filtro opcional
`entregaId`. Retorna uma lista JSON ordenada por criação decrescente, com
desempate por identificador. Cada item contém `id`, `entregaId`, `status`,
`mensagem`, `lida` e `createdAt`.

Exemplos para executar em um cliente HTTP ou importar no Postman:

```http
GET http://localhost:8082/notificacoes?limit=10
```

```http
GET http://localhost:8082/notificacoes?entregaId=SUBSTITUA_PELO_ID_DA_ENTREGA
```

```http
PATCH http://localhost:8082/notificacoes/SUBSTITUA_PELO_ID_DA_NOTIFICACAO/leitura
```

O PATCH usa o `id` da notificação, retornado na listagem. Repetir a marcação
de uma notificação já lida também retorna `204`. Um registro inexistente ou
de outro usuário retorna `404`; um limite inválido retorna `400`.

## Testes com PostgreSQL temporário

`NotificationIntegrationTests` contém testes de integração que iniciam Spring
Boot e um PostgreSQL temporário pelo Testcontainers. O Liquibase cria as
tabelas nesse banco isolado. Os testes cobrem deduplicação, listagem com e sem
filtro, limite, ordenação, isolamento por usuário, leitura e validação de estados.
O consumo Kafka fica desabilitado nos testes desse serviço.

Usamos PostgreSQL para verificar o SQL nativo, incluindo `ON CONFLICT`, as
restrições e a persistência dos dados no mesmo mecanismo usado pela aplicação.
O modo de compatibilidade do H2 não substitui essa validação.

Para os testes, basta o Docker Desktop estar iniciado. Não é necessário subir
o Compose, criar banco no DBeaver ou ter um Dockerfile dentro deste módulo:
o Testcontainers cria os contêineres a partir da configuração Java dos testes.

Na raiz de plataforma-entregas:

```powershell
docker info
mvn -B -ntp -f NotificacaoService/notificacao-service/pom.xml test
```

A biblioteca `messaging-core` deve ser instalada antes, conforme o README da raiz. Os dados dos testes ficam separados
do PostgreSQL local da aplicação e os contêineres de teste são descartáveis.
`-DskipTests` pula a execução dos testes.
