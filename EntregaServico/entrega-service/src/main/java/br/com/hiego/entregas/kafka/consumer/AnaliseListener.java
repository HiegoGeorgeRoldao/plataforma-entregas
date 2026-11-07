package br.com.hiego.entregas.kafka.consumer;

import br.com.hiego.entregas.service.AnaliseHandler;
import br.com.hiego.messaging.IntegrationEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class AnaliseListener {
    private final AnaliseHandler handler;
    private final ObjectMapper mapper;

    public AnaliseListener(AnaliseHandler handler, ObjectMapper mapper) {
        this.handler = handler; this.mapper = mapper;
    }

    /**
     * Desserializa o payload de {@code entrega.analisada} para o contrato
     * {@link IntegrationEvent} de {@code messaging-core} e delega o processamento.
     * Excecoes propagadas ao Kafka sao tratadas pelo error handler da biblioteca,
     * que aplica as retentativas configuradas e encaminha a mensagem para a DLT.
     *
     * @param payload JSON recebido do topico configurado
     */
    @KafkaListener(topics = "${app.messaging.topics.analisada}", groupId = "entrega-service-v1")
    public void receive(String payload) { handler.process(mapper.readValue(payload, IntegrationEvent.class)); }
}
