package br.com.hiego.analise;

import br.com.hiego.analise.enuns.AnalysisStatus;
import br.com.hiego.analise.enuns.OutboxStatus;
import br.com.hiego.analise.dto.AnalysisResult;
import br.com.hiego.analise.entity.Analise;
import br.com.hiego.analise.entity.MongoOutbox;
import br.com.hiego.analise.repository.AnaliseRepository;
import br.com.hiego.analise.repository.OutboxRepository;
import br.com.hiego.analise.repository.ProcessedEventRepository;
import br.com.hiego.analise.service.AnalysisWriter;
import br.com.hiego.analise.validation.CepValidator;
import br.com.hiego.messaging.IntegrationEvent;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** As expectativas usam os nomes antigos literalmente para detectar quebra do contrato. */
class ContractCompatibilityTests {
    private MappingMongoConverter converter() throws Exception {
        var conversions = MongoCustomConversions.create(config -> {});
        var context = new MongoMappingContext();
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        context.afterPropertiesSet();
        var converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
        return converter;
    }

    @Test
    void leAnaliseAntigaEGravaMesmoStatusTextual() throws Exception {
        var mapper = converter();
        var old = new Document("_id", UUID.randomUUID().toString())
            .append("usuarioId", "usuario").append("status", "ANALISADA")
            .append("observacao", "Concluida").append("enderecoOrigem", new Document())
            .append("enderecoDestino", new Document()).append("previsao", new Document())
            .append("createdAt", Date.from(Instant.now()));
        var analise = mapper.read(Analise.class, old);
        assertThat(analise.status()).isEqualTo(AnalysisStatus.ANALISADA);
        var stored = new Document();
        mapper.write(analise, stored);
        assertThat(stored.getString("status")).isEqualTo("ANALISADA");
        assertThat(stored.getString("usuarioId")).isEqualTo("usuario");
        var json = JsonMapper.builder().build().readTree(JsonMapper.builder().build().writeValueAsString(analise));
        assertThat(json.path("status").asText()).isEqualTo("ANALISADA");
    }

    @Test
    void leOutboxAntigaEPreservaCamposDeReserva() throws Exception {
        var mapper = converter();
        var now = Date.from(Instant.now());
        var old = new Document("_id", "evento").append("aggregateId", "entrega")
            .append("topic", "entrega.analisada.v1").append("payload", "{}")
            .append("status", "PROCESSING").append("attempts", 2)
            .append("createdAt", now).append("nextAttemptAt", now)
            .append("lockedUntil", now).append("leaseToken", "reserva");
        var event = mapper.read(MongoOutbox.class, old);
        assertThat(event.status()).isEqualTo(OutboxStatus.PROCESSING);
        var stored = new Document();
        mapper.write(event, stored);
        assertThat(stored.getString("status")).isEqualTo("PROCESSING");
        assertThat(stored.getString("leaseToken")).isEqualTo("reserva");
        assertThat(stored.getInteger("attempts")).isEqualTo(2);
        assertThat(stored).containsKeys("nextAttemptAt", "createdAt", "lockedUntil");
    }

    @Test
    void eventoPublicadoMantemNomesEStatusString() {
        var outbox = mock(OutboxRepository.class);
        var mapper = JsonMapper.builder().build();
        var writer = new AnalysisWriter(mock(AnaliseRepository.class), mock(ProcessedEventRepository.class),
            outbox, mapper, 1, "entrega.analisada.v1");
        var input = IntegrationEvent.create("EntregaSolicitada", 1, UUID.randomUUID(), Map.of(
            "usuarioId", UUID.randomUUID().toString(), "cepOrigem", "01001000", "cepDestino", "20040002"));
        writer.persist(input, new AnalysisResult(AnalysisStatus.ANALISADA, "Concluida", Map.of(), Map.of(), Map.of()));
        var captured = ArgumentCaptor.forClass(MongoOutbox.class);
        verify(outbox).insert(captured.capture());
        assertThat(captured.getValue().status()).isEqualTo(OutboxStatus.PENDING);
        var json = mapper.readTree(captured.getValue().payload());
        assertThat(json.path("eventType").asText()).isEqualTo("EntregaAnalisada");
        assertThat(json.path("data").path("status").asText()).isEqualTo("ANALISADA");
        assertThat(json.path("data").path("usuarioId").asText()).isEqualTo(input.usuarioId().toString());
        assertThat(json.path("data").path("observacao").asText()).isEqualTo("Concluida");
    }

    @Test
    void cepExigeOitoDigitosAsciiSemMascara() {
        assertThat(CepValidator.isValid("01001000")).isTrue();
        assertThat(CepValidator.isValid("00000000")).isTrue(); // Formato valido; existencia depende do ViaCEP.
        for (var value : new String[]{"1234567", "123456789", "01001-000", "abcdefgh", "１２３４５６７８", " 01001000"}) {
            assertThat(CepValidator.isValid(value)).as(value).isFalse();
        }
        assertThat(CepValidator.isValid(null)).isFalse();
    }
}
