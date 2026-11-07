package br.com.hiego.analise;

import br.com.hiego.analise.enuns.AnalysisStatus;
import br.com.hiego.analise.enuns.OutboxStatus;
import br.com.hiego.analise.controller.AnaliseController;
import br.com.hiego.analise.entity.Analise;
import br.com.hiego.analise.repository.OutboxRepository;
import br.com.hiego.analise.service.AnaliseQueryService;
import java.time.Instant;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import br.com.hiego.analise.entity.MongoOutbox;
import br.com.hiego.analise.integration.AnalysisGateway;
import br.com.hiego.analise.service.AnalysisService;
import br.com.hiego.messaging.IntegrationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.bean.override.mockito.*;
import org.testcontainers.mongodb.MongoDBContainer;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "app.messaging.enabled=false")
@Import(AnalysisIntegrationTests.Containers.class)
class AnalysisIntegrationTests {
    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean @ServiceConnection
        MongoDBContainer mongoContainer() { return new MongoDBContainer("mongo:8.0.16").withReplicaSet(); }
    }
    @Autowired
    AnalysisService service;
    @Autowired OutboxRepository outbox;
    @Autowired AnaliseQueryService queries;
    @MockitoSpyBean MongoTemplate mongo;
    @MockitoBean AnalysisGateway gateway;

    @BeforeEach
    void clean() throws Exception {
        for (String name : List.of("analises", "outbox_events", "processed_events"))
            mongo.getCollection(name).deleteMany(new org.bson.Document());
        when(gateway.findAddress("01001000")).thenReturn(Map.of("cep", "01001000", "cidade", "Sao Paulo", "uf", "SP"));
        when(gateway.findAddress("20040002")).thenReturn(Map.of("cep", "20040002", "cidade", "Rio de Janeiro", "uf", "RJ"));
        when(gateway.findForecast(anyString(), anyString())).thenReturn(Map.of("status", "DISPONIVEL"));
    }
    private IntegrationEvent event() {
        return IntegrationEvent.create("EntregaSolicitada", 1, UUID.randomUUID(), Map.of(
            "usuarioId", "11111111-1111-1111-1111-111111111111", "cepOrigem", "01001000", "cepDestino", "20040002"));
    }
    @Test
    void persisteAnaliseEventoEDeduplica() {
        var event = event();
        service.process(event);
        service.process(event);
        assertThat(mongo.getCollection("analises").countDocuments()).isEqualTo(1);
        assertThat(mongo.getCollection("outbox_events").countDocuments()).isEqualTo(1);
        assertThat(mongo.getCollection("processed_events").countDocuments()).isEqualTo(1);
        verify(gateway, times(1)).findAddress("01001000");
        verify(gateway, times(1)).findAddress("20040002");
    }
    @Test
    void falhaNaOutboxDesfazTodasAsColecoes() {
        doThrow(new IllegalStateException("Falha simulada")).when(mongo).insert(any(MongoOutbox.class));
        assertThatThrownBy(() -> service.process(event())).isInstanceOf(IllegalStateException.class);
        for (String name : List.of("analises", "outbox_events", "processed_events"))
            assertThat(mongo.getCollection(name).countDocuments()).isZero();
    }
    @Test
    void indisponibilidadeExternaNaoMarcaComoProcessado() {
        when(gateway.findAddress(anyString())).thenThrow(new IllegalStateException("ViaCEP indisponivel"));
        assertThatThrownBy(() -> service.process(event())).isInstanceOf(IllegalStateException.class);
        assertThat(mongo.getCollection("processed_events").countDocuments()).isZero();
    }
    @Test
    void consultaHttpFiltraUsuarioNoRepositorioReal() throws Exception {
        var event = event();
        service.process(event);
        UUID foreignId = UUID.randomUUID();
        mongo.insert(new Analise(foreignId.toString(), UUID.randomUUID().toString(), AnalysisStatus.ANALISADA, "Outro usuario",
            Map.of(), Map.of(), Map.of(), Instant.now()));
        var mvc = MockMvcBuilders.standaloneSetup(new AnaliseController(queries)).build();
        mvc.perform(get("/analises/{id}", event.aggregateId())).andExpect(status().isOk());
        mvc.perform(get("/analises/{id}", foreignId)).andExpect(status().isNotFound());
        mvc.perform(get("/analises/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void reservaExpiradaPodeSerRetomadaSemPermitirAtualizacaoDoDonoAntigo() {
        Instant now = Instant.parse("2026-11-05T12:00:00Z");
        mongo.insert(new MongoOutbox("reserva-test", "entrega", "topico", "payload", OutboxStatus.PENDING, 0,
            now, now, null, null, null, null));
        var first = outbox.claimNext(now, now.plusSeconds(30), "primeiro").orElseThrow();
        assertThat(first.attempts()).isEqualTo(1);
        assertThat(outbox.claimNext(now.plusSeconds(1), now.plusSeconds(31), "outro")).isEmpty();
        var recovered = outbox.claimNext(now.plusSeconds(31), now.plusSeconds(61), "segundo").orElseThrow();
        assertThat(recovered.attempts()).isEqualTo(2);
        assertThat(outbox.markPublished(first.id(), "primeiro", now.plusSeconds(32))).isFalse();
        assertThat(outbox.reschedule(first.id(), "primeiro", now.plusSeconds(32), "erro antigo")).isFalse();
        assertThat(outbox.markPublished(first.id(), "segundo", now.plusSeconds(32))).isTrue();
        var published = mongo.findById(first.id(), MongoOutbox.class);
        assertThat(published.status()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(published.leaseToken()).isNull();
    }

}
