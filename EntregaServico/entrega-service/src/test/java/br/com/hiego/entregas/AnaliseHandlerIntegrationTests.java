package br.com.hiego.entregas;

import br.com.hiego.entregas.dto.SolicitarEntregaRequest;
import br.com.hiego.entregas.enuns.StatusEntrega;
import br.com.hiego.entregas.repository.OutboxDispatchRepository;
import br.com.hiego.entregas.service.AnaliseHandler;
import br.com.hiego.entregas.service.EntregaService;
import br.com.hiego.entregas.service.OutboxService;
import br.com.hiego.messaging.IntegrationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"app.messaging.enabled=false", "app.rate-limit.enabled=false"})
@Import(TestcontainersConfiguration.class)
class AnaliseHandlerIntegrationTests {
    @Autowired EntregaService service;
    @Autowired AnaliseHandler handler;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void clean() {
        jdbc.update("DELETE FROM processed_event"); jdbc.update("DELETE FROM outbox_event"); jdbc.update("DELETE FROM entrega");
    }
    @Test void atualizaEPublicaUmaVez() {
        var entrega = service.solicitar(new SolicitarEntregaRequest("01001000", "20040002"));
        var event = IntegrationEvent.create("EntregaAnalisada", 1, entrega.id(), Map.of(
            "usuarioId", "11111111-1111-1111-1111-111111111111", "status", "ANALISADA", "observacao", "Concluida"));
        handler.process(event); handler.process(event);
        assertThat(service.consultar(entrega.id()).status()).isEqualTo(StatusEntrega.ANALISADA);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_event", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE event_type='EntregaAtualizada'", Long.class)).isEqualTo(1L);
    }
    @Test void erroDesfazRegistroDeDeduplicacao() {
        var event = IntegrationEvent.create("EntregaAnalisada", 1, UUID.randomUUID(), Map.of(
            "usuarioId", "11111111-1111-1111-1111-111111111111", "status", "ANALISADA", "observacao", "Concluida"));
        assertThatThrownBy(() -> handler.process(event)).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_event", Long.class)).isZero();
    }

    @Test @SuppressWarnings("unchecked")
    void outboxReagendaFalhaERecuperaReservaVencida() {
        service.solicitar(new SolicitarEntregaRequest("01001000", "20040002"));
        var kafka = (org.springframework.kafka.core.KafkaTemplate<String, String>)
            org.mockito.Mockito.mock(org.springframework.kafka.core.KafkaTemplate.class);
        org.mockito.Mockito.when(kafka.send(org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("Broker indisponivel")));
        var publisher = new OutboxService(new OutboxDispatchRepository(jdbc), kafka);
        publisher.publishPending();
        assertThat(jdbc.queryForObject("SELECT status FROM outbox_event", String.class)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT attempts FROM outbox_event", Integer.class)).isEqualTo(1);
        jdbc.update("UPDATE outbox_event SET status='PROCESSING', locked_until=now()-interval '1 second', lease_token=?", UUID.randomUUID());
        org.mockito.Mockito.when(kafka.send(org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        publisher.publishPending();
        assertThat(jdbc.queryForObject("SELECT status FROM outbox_event", String.class)).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT attempts FROM outbox_event", Integer.class)).isEqualTo(2);
    }
}
