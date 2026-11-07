# Plataforma de entregas

Três serviços Java 21 / Spring Boot com comunicação por eventos Kafka, Outbox,
PostgreSQL, MongoDB, Redis e migrações Liquibase nos bancos SQL.

```text
plataforma-entregas/
├── EntregaServico/entrega-service/
├── AnaliseSservice/analise-service/
├── NotificacaoService/notificacao-service/
├── compose.yaml
└── README.md
```

Cada serviço possui seu próprio POM e pode ser aberto separadamente no IntelliJ.
Os nomes originais das pastas foram preservados, inclusive `AnaliseSservice`.

## Fluxo

1. `POST /entregas` grava uma entrega e o evento `EntregaSolicitada` na mesma transação PostgreSQL.
2. O publicador da Outbox envia para `entrega.solicitada.v1`.
3. O serviço de análise consulta ViaCEP e CPTEC, persiste no MongoDB e publica `EntregaAnalisada` por sua Outbox.
4. O serviço de entregas consome `entrega.analisada.v1`, atualiza a entrega e prepara `EntregaAtualizada` na Outbox.
5. O serviço de notificações consome `entrega.atualizada.v1` e registra uma notificação no seu PostgreSQL.

## Preparar a biblioteca compartilhada

Os serviços dependem de `br.com.hiego:messaging-core:0.0.1-SNAPSHOT`, mantida em
[bibliotecas](https://github.com/HiegoGeorgeRoldao/bibliotecas). Esse repositório
é privado: é necessário acesso aos fontes para instalar a biblioteca. Tornar a
plataforma pública não concede acesso ao repositório da biblioteca.

Com JDK 21, Maven 3.9+ e Docker iniciado para os testes, execute em uma pasta separada:

```powershell
git clone https://github.com/HiegoGeorgeRoldao/bibliotecas.git
cd bibliotecas
mvn clean install
```

Execute o comando na raiz de `bibliotecas` para instalar o POM pai e a biblioteca
no `.m2/repository`. Para instalar sem rodar os testes, use
`mvn clean install -DskipTests`. Os serviços usam a cópia instalada no mesmo
repositório Maven local; essa etapa deve ser repetida em cada máquina ou agente CI.

## Compilar os serviços

Na raiz de `plataforma-entregas`, depois de instalar a biblioteca:

```powershell
mvn -f EntregaServico/entrega-service/pom.xml clean package -DskipTests
mvn -f AnaliseSservice/analise-service/pom.xml clean package -DskipTests
mvn -f NotificacaoService/notificacao-service/pom.xml clean package -DskipTests
```

O Maven deve usar JDK 21. Os comandos geram os JARs em `target/` dentro de cada
aplicação. Não há POM agregador ou Maven Wrapper na raiz deste repositório.

## Executar

Abra o Docker Desktop em modo Linux. Para subir infraestrutura e aplicações,
após gerar os três JARs:

```powershell
docker compose --profile apps up -d --build
```

Os Dockerfiles de cada serviço copiam os JARs gerados localmente. A construção
das imagens não baixa a biblioteca privada nem compila o código Java.

Para desenvolver pelo IntelliJ, suba somente a infraestrutura:

```powershell
docker compose up -d --wait
```

Abra os POMs dos serviços e execute `EntregaServiceApplication`,
`AnaliseApplication` e `NotificacaoApplication`. Pare as aplicações em containers
antes de executar as mesmas portas na IDE:

```powershell
docker compose --profile apps stop entrega-service analise-service notificacao-service
```

## Portas e conexões locais

| Componente | Endereço | Banco / usuário / senha local |
|---|---|---|
| API de entregas | `http://localhost:8080` | — |
| API de análises | `http://localhost:8081` | — |
| API de notificações | `http://localhost:8082` | — |
| Kafka UI | `http://localhost:8085` | Consulta de tópicos, mensagens e DLTs |
| PostgreSQL de entregas | `localhost:5433` | `entregas` / `entregas` / `entregas_local` |
| PostgreSQL de notificações | `localhost:5434` | `notificacoes` / `notificacoes` / `notificacoes_local` |
| MongoDB de análises | `localhost:27017` | `analises`, sem autenticação, replica set `rs0` |
| Redis | `localhost:6379` | Cache e cota diária, sem autenticação |
| Kafka | `localhost:9092` | Listener para aplicações executadas no host |

Essas credenciais são padrões de desenvolvimento local. As portas do Compose
ficam vinculadas a `127.0.0.1`. O MongoDB é inicializado como replica set pelo
healthcheck embutido no `compose.yaml`.

## Exercitar o fluxo

No PowerShell:

```powershell
$entrega = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/entregas' -ContentType 'application/json' -Body '{"cepOrigem":"01001000","cepDestino":"20040002"}'
Invoke-RestMethod "http://localhost:8080/entregas/$($entrega.id)"
Invoke-RestMethod "http://localhost:8081/analises/$($entrega.id)"
Invoke-RestMethod "http://localhost:8082/notificacoes?entregaId=$($entrega.id)"
```

A análise é assíncrona; a consulta pode retornar `404` até o processamento
terminar. A saúde de cada serviço está disponível em `/actuator/health`.

## Testes

Com Docker disponível, execute na raiz:

```powershell
mvn -f EntregaServico/entrega-service/pom.xml verify
mvn -f AnaliseSservice/analise-service/pom.xml verify
mvn -f NotificacaoService/notificacao-service/pom.xml verify
```

Os testes de integração usam Testcontainers para criar bancos temporários
isolados dos dados locais. Não é necessário subir o Compose para eles.
`-DskipTests` ignora a execução dos testes.

## Configuração e limites do MVP

As configurações de cada aplicação ficam no respectivo `application.yaml`.
`DB_URL`, `DB_USER`, `DB_PASSWORD`, `MONGODB_URI`, `REDIS_HOST`, `REDIS_PORT`,
`KAFKA_BOOTSTRAP_SERVERS` e `SERVER_PORT` permitem ajustar as conexões.
`MESSAGING_ENABLED=false` desabilita a mensageria; `OUTBOX_ENABLED=false`
desabilita apenas o publicador dos serviços que usam Outbox.

`USUARIO_LOCAL_ID` simula uma identidade fixa nos três serviços; ainda não há
autenticação real. A cota padrão é de 20 solicitações por usuário/dia, no fuso
de São Paulo. As notificações são internas, sem envio real de e-mail ou SMS.
Mensagens podem ser reenviadas e os consumidores deduplicam por `eventId`.
Não há garantia de ordem global entre eventos publicados pela Outbox.

## Documentação dos serviços

- [Entregas](EntregaServico/entrega-service/README.md)
- [Análises](AnaliseSservice/analise-service/README.md)
- [Notificações](NotificacaoService/notificacao-service/README.md)
