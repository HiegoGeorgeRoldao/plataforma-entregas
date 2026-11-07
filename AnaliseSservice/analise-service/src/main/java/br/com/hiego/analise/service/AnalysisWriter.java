package br.com.hiego.analise.service;

import br.com.hiego.analise.filds.EntregaEventFields;
import br.com.hiego.analise.enuns.OutboxStatus;
import br.com.hiego.analise.filds.EntregaEventContract;
import br.com.hiego.analise.dto.AnalysisResult;
import br.com.hiego.analise.entity.Analise;
import br.com.hiego.analise.entity.MongoOutbox;
import br.com.hiego.analise.entity.ProcessedEvent;
import br.com.hiego.analise.repository.AnaliseRepository;
import br.com.hiego.analise.repository.OutboxRepository;
import br.com.hiego.analise.repository.ProcessedEventRepository;
import br.com.hiego.messaging.IntegrationEvent;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class AnalysisWriter {
    private final AnaliseRepository analises;
    private final ProcessedEventRepository processedEvents;
    private final OutboxRepository outbox;
    private final ObjectMapper mapper;
    private final int schemaVersion;
    private final String outputTopic;

    /** Recebe os repositórios, o serializador e a configuração do evento de saída. */
    public AnalysisWriter(AnaliseRepository analises, ProcessedEventRepository processedEvents,
                          OutboxRepository outbox, ObjectMapper mapper,
                          @Value("${app.messaging.schema.published-version}") int schemaVersion,
                          @Value("${app.messaging.topics.analisada}") String outputTopic) {
        this.analises = analises;
        this.processedEvents = processedEvents;
        this.outbox = outbox;
        this.mapper = mapper;
        this.schemaVersion = schemaVersion;
        this.outputTopic = outputTopic;
    }

    /** Grava deduplicacao, analise e Outbox atomicamente; qualquer falha desfaz as tres escritas. */
    @Transactional
    public void persist(IntegrationEvent input, AnalysisResult result) {
        input.requireVersion(schemaVersion);

        if (processedEvents.existsById(input.eventId().toString())) {
            return;
        }

        Instant now = Instant.now();
        processedEvents.insert(new ProcessedEvent(input.eventId().toString(), now));

        analises.insert(new Analise(input.aggregateId().toString(), input.usuarioId().toString(),
                result.status(), result.observacao(), result.origem(),
                result.destino(), result.previsao(), now)
        );

        var event = IntegrationEvent.create(EntregaEventContract.ENTREGA_ANALISADA, schemaVersion, input.aggregateId(),
                Map.of(EntregaEventFields.USUARIO_ID, input.usuarioId().toString(), EntregaEventFields.STATUS, result.status().name(),
                        EntregaEventFields.OBSERVACAO, result.observacao())
        );

        outbox.insert(new MongoOutbox(event.eventId().toString(), event.aggregateId().toString(),
            outputTopic, mapper.writeValueAsString(event), OutboxStatus.PENDING, 0,
            event.occurredAt(), event.occurredAt(), null, null, null, null)
        );
    }
}
