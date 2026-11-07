package br.com.hiego.analise.kafka.consumer;

import br.com.hiego.analise.service.AnalysisService;
import br.com.hiego.messaging.IntegrationEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class EntregaListener {

    private final AnalysisService service;
    private final ObjectMapper mapper;

    /** Recebe o serviço de análise e o conversor do JSON dos eventos. */
    public EntregaListener(AnalysisService service, ObjectMapper mapper) {
        this.service = service; this.mapper = mapper;
    }

    /** Converte a mensagem recebida do Kafka em um evento e o encaminha ao serviço de análise. */
    @KafkaListener(topics = "${app.messaging.topics.solicitada}", groupId = "analise-service-v1")
    public void receive(String payload) {
        service.process(mapper.readValue(payload, IntegrationEvent.class));
    }
}
