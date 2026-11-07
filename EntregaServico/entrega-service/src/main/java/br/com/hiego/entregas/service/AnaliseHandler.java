package br.com.hiego.entregas.service;

import br.com.hiego.entregas.entity.OutboxEvent;
import br.com.hiego.entregas.enuns.StatusEntrega;
import br.com.hiego.entregas.repository.EntregaRepository;
import br.com.hiego.entregas.repository.OutboxRepository;
import br.com.hiego.entregas.repository.ProcessedEventRepository;
import br.com.hiego.messaging.IntegrationEvent;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;

@Service
public class AnaliseHandler {
    private final int schemaVersion;
    private final String outputTopic;
    private final ProcessedEventRepository processedEvents;
    private final EntregaRepository entregas;
    private final OutboxRepository outbox;
    private final ObjectMapper mapper;

    public AnaliseHandler(ProcessedEventRepository processedEvents, EntregaRepository entregas, OutboxRepository outbox, ObjectMapper mapper, @Value("${app.messaging.schema.published-version}") int schemaVersion,
                          @Value("${app.messaging.topics.atualizada}") String outputTopic) {
        this.schemaVersion = schemaVersion;
        this.outputTopic = outputTopic;
        this.processedEvents = processedEvents;
        this.entregas = entregas;
        this.outbox = outbox;
        this.mapper = mapper;
    }

    /**
     * Aplica uma analise recebida pelo Kafka e prepara {@code EntregaAtualizada} na Outbox.
     * Os metodos de {@link IntegrationEvent} conferem versao, tipo e campos obrigatorios
     * do contrato de {@code messaging-core}. O identificador do evento impede reprocessamento;
     * a entrega so muda de estado se ainda estiver como {@code SOLICITADA}.
     * A atualizacao e o novo registro da Outbox participam da mesma transacao.
     *
     * @param event envelope {@code EntregaAnalisada} recebido
     * @throws IllegalArgumentException se o contrato, o resultado ou a entrega forem invalidos
     */
    @Transactional
    public void process(IntegrationEvent event) {
        event.requireVersion(schemaVersion);
        event.requireType("EntregaAnalisada");

        var status = StatusEntrega.valueOf(event.text("status"));

        if (status == StatusEntrega.SOLICITADA) throw new IllegalArgumentException("Resultado invalido");

        if (!processedEvents.registrar(event.eventId())) return;

        var entrega = entregas.findByIdAndUsuarioId(event.aggregateId(), event.usuarioId())
            .orElseThrow(() -> new IllegalArgumentException("Entrega ou usuario do evento inexistente")
            );

        if (entrega.getStatus() != StatusEntrega.SOLICITADA) return;

        entrega.concluirAnalise(status);

        var updated = IntegrationEvent.create("EntregaAtualizada",
                schemaVersion, entrega.getId(),
                Map.of("usuarioId", entrega.getUsuarioId().toString(),
                        "status", status.name(),
                        "observacao",
                        event.text("observacao"))
        );

        outbox.save(OutboxEvent.from(updated, mapper.writeValueAsString(updated), outputTopic));
    }
}
