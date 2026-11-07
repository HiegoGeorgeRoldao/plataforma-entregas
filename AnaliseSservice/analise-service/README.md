# analise-service

Serviço de análise de entregas com ViaCEP, CPTEC, MongoDB, Redis e Kafka.

## Como o serviço participa do fluxo de entregas

1. O `entrega-service` recebe o `POST /entregas` com os CEPs de origem e destino.
   Ele salva a entrega e publica `EntregaSolicitada` no Kafka pela Outbox.
2. O `analise-service` consome esse evento no tópico `entrega.solicitada.v1`.
   Ele consulta o ViaCEP para obter os endereços e o CPTEC para buscar a previsão.
   Em seguida, grava o resultado no MongoDB e publica `EntregaAnalisada` no tópico
   `entrega.analisada.v1` pela Outbox.
3. O `entrega-service` consome o resultado, atualiza a entrega e publica
   `EntregaAtualizada` no tópico `entrega.atualizada.v1`. O `notificacao-service`
   consome esse evento e registra a notificação.

Para acompanhar o fluxo pelo Postman, comece pelo `POST /entregas` no
`entrega-service` (porta padrão `8080`). Use o `id` retornado para chamar
`GET /analises/{entregaId}` no `analise-service` (porta padrão `8081`).
A análise é assíncrona, então a consulta pode retornar `404` enquanto o resultado
ainda está sendo processado.

O `analise-service` usa Kafka para receber e publicar eventos, MongoDB em replica
set para persistir a análise e a Outbox, e Redis como cache opcional. O
`notificacao-service` participa da etapa posterior do fluxo, após a atualização
da entrega. Os nomes dos tópicos acima são os padrões da configuração local.

## Responsabilidades

```text
GET /analises/{entregaId}
  -> controller/AnaliseController
  -> service/AnaliseQueryService
  -> repository/AnaliseRepository
  -> repository/mongo/MongoAnaliseRepository
  -> MongoDB

Kafka EntregaSolicitada
  -> kafka/consumer/EntregaListener
  -> service/AnalysisService (validação, deduplicação, aprovação/rejeição e fallback)
  -> integration/AnalysisGateway (HTTP e conversão das respostas externas)
  -> service/AnalysisWriter (transação)
  -> repositories de análise, evento processado e Outbox

Agendamento -> MongoOutboxPublisher -> OutboxService -> OutboxRepository / Kafka
```

- **Controller:** recebe HTTP e traduz o resultado para 200/404. UUID inválido retorna 400.
- **Services:** executam os casos de uso e as regras, dependendo de interfaces de persistência.
- **Repositories:** contêm queries, inserções, reservas atômicas, índices e acesso ao Redis.
- **Integration:** contém o cliente das APIs externas e a conversão de JSON/XML.
- **Config:** registra o gerenciador transacional e dispara a preparação do schema.

As interfaces de repository expõem apenas as operações necessárias; services não
recebem MongoTemplate, Query, Criteria, Update ou StringRedisTemplate. O pacote
HTTP se chama `controller`, substituindo o nome anterior `controlle`.

## Garantias preservadas

A consulta filtra por entrega **e usuário**. O usuário vem da configuração local
do MVP; isso ainda não representa uma autenticação real.

AnalysisWriter mantém deduplicação, análise e evento de Outbox na mesma transação
MongoDB. A segunda conferência de deduplicação fica dentro dessa transação e o
identificador do evento continua único. Chamadas HTTP ficam fora da transação.

A Outbox reserva um evento por vez, recupera reservas expiradas e usa um token
para impedir atualizações de um publicador cuja reserva foi retomada. O status
PUBLISHED só é gravado após o ACK do Kafka. Falhas são reagendadas com backoff.
A entrega continua sendo pelo menos uma vez, exigindo consumidores idempotentes.

Redis permanece um cache opcional: indisponibilidade permite consultar a API.
Falha de CEP inexistente rejeita a análise; falha técnica do ViaCEP propaga para
o retry do consumidor; indisponibilidade do CPTEC permite concluir sem previsão.

O JSON de sucesso e o endpoint consumidos pelo frontend foram mantidos.

## Verificação

Na raiz de plataforma-entregas, com JDK 21:

```powershell
# Testes que não precisam de Docker.
mvn -f AnaliseSservice/analise-service/pom.xml test -Dtest=AnalysisGatewayTests,AnaliseControllerTests,AnalysisServiceTests,OutboxServiceTests,ContractCompatibilityTests

# Inclui transações, filtro por usuário e reservas da Outbox em MongoDB isolado.
# Requer Docker disponível; o MongoDB é iniciado em replica set pelo Testcontainers.
mvn -f AnaliseSservice/analise-service/pom.xml verify
```

## Estados, campos e validacao

- enuns/AnalysisStatus e enuns/OutboxStatus tipam os estados em Java; os nomes
  continuam sendo gravados como texto no MongoDB e serializados como texto no JSON.
- enuns/ForecastStatus define os estados da previsao. Os mapas e o cache continuam
  usando name(), evitando diferencas entre uma resposta nova e outra lida do Redis.
- filds/EntregaEventContract centraliza os nomes dos eventos consumidos e produzidos.
  filds/EntregaEventFields centraliza seus campos; AddressFields e ForecastFields
  ficam no mesmo pacote filds e nomeiam os mapas internos.
- filds/ExternalApiFields isola campos definidos pelo ViaCEP e pelo CPTEC.
  Um campo do provedor nao deve ser renomeado junto com um campo do contrato interno.
- filds/MongoDocumentFields concentra nomes das queries e dos indices,
  usados tambem nos mapeamentos Field dos componentes correspondentes das entidades.
- validation/CepValidator valida oito digitos ASCII com Pattern pre-compilado.
  A existencia do CEP continua sendo responsabilidade da consulta ao ViaCEP.

Nao ha uma classe generica Variaveis nem uma interface de constantes. Cada grupo
tem uma responsabilidade. URLs, topicos e parametros de ambiente permanecem no YAML.
Os valores de contrato atuais foram preservados: trocar o nome de um enum ou campo
externo pode exigir versionamento de eventos e migracao de dados/cache/clientes.
Esta organizacao foi aplicada somente ao analise-service, sem alterar a biblioteca
messaging-core instalada ou os contratos dos demais servicos.

Os enums ficam no pacote `enuns` e as classes de campos no pacote `filds`.
As classes de campos sao separadas por contexto; `ExternalApiFields` agrupa
`ViaCep` e `Cptec` como classes internas. Os nomes dos pacotes seguem a convencao
solicitada neste projeto.
