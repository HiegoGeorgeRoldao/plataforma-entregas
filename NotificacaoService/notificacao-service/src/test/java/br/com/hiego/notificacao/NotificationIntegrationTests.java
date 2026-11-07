package br.com.hiego.notificacao;

import br.com.hiego.messaging.IntegrationEvent;
import br.com.hiego.notificacao.service.NotificationHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.messaging.enabled=false")
@AutoConfigureMockMvc
@Import(NotificationIntegrationTests.Containers.class)
class NotificationIntegrationTests {
    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean @ServiceConnection
        PostgreSQLContainer postgres() { return new PostgreSQLContainer("postgres:17.6-alpine"); }
    }
    @Autowired NotificationHandler handler;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @BeforeEach void clean() { jdbc.update("DELETE FROM notificacao"); jdbc.update("DELETE FROM processed_event"); }

    private IntegrationEvent event(String usuario) {
        return IntegrationEvent.create("EntregaAtualizada", 1, UUID.randomUUID(), Map.of(
            "usuarioId", usuario, "status", "ANALISADA", "observacao", "Analise concluida"));
    }
    @Test
    void deduplicaListaEMarcaLeitura() throws Exception {
        var event = event("11111111-1111-1111-1111-111111111111");
        handler.process(event); handler.process(event);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacao", Long.class)).isEqualTo(1L);
        var id = jdbc.queryForObject("SELECT id FROM notificacao", UUID.class);
        mvc.perform(get("/notificacoes")).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].lida").value(false));
        var maisRecente = event("11111111-1111-1111-1111-111111111111");
        handler.process(maisRecente);
        mvc.perform(get("/notificacoes").param("limit", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].entregaId").value(maisRecente.aggregateId().toString()));
        mvc.perform(get("/notificacoes").param("entregaId", event.aggregateId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].entregaId").value(event.aggregateId().toString()));
        mvc.perform(patch("/notificacoes/{id}/leitura", id)).andExpect(status().isNoContent());
        mvc.perform(patch("/notificacoes/{id}/leitura", id)).andExpect(status().isNoContent());
        mvc.perform(get("/notificacoes").param("entregaId", event.aggregateId().toString()))
            .andExpect(jsonPath("$[0].lida").value(true));
    }
    @Test
    void isolaUsuarioEValidaLimite() throws Exception {
        var outroUsuario = event(UUID.randomUUID().toString());
        handler.process(outroUsuario);
        var id = jdbc.queryForObject("SELECT id FROM notificacao", UUID.class);
        mvc.perform(get("/notificacoes")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/notificacoes").param("entregaId", outroUsuario.aggregateId().toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(patch("/notificacoes/{id}/leitura", id)).andExpect(status().isNotFound());
        mvc.perform(get("/notificacoes?limit=101")).andExpect(status().isBadRequest());
    }

    @Test
    void aceitaRejeitadaERecusaEstadoForaDoContrato() {
        UUID usuario = UUID.fromString("11111111-1111-1111-1111-111111111111");
        var rejeitada = IntegrationEvent.create("EntregaAtualizada", 1, UUID.randomUUID(), Map.of(
            "usuarioId", usuario.toString(), "status", "REJEITADA", "observacao", "CEP inexistente"));
        handler.process(rejeitada);
        assertThat(jdbc.queryForObject("SELECT status FROM notificacao WHERE entrega_id=?", String.class,
            rejeitada.aggregateId())).isEqualTo("REJEITADA");

        var invalida = IntegrationEvent.create("EntregaAtualizada", 1, UUID.randomUUID(), Map.of(
            "usuarioId", usuario.toString(), "status", "SOLICITADA", "observacao", "Invalida"));
        assertThatThrownBy(() -> handler.process(invalida)).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_event", Long.class)).isEqualTo(1L);
    }
}
