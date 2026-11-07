package br.com.hiego.analise;

import br.com.hiego.analise.enuns.OutboxStatus;
import br.com.hiego.analise.entity.MongoOutbox;
import br.com.hiego.analise.repository.OutboxRepository;
import br.com.hiego.analise.service.OutboxService;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutboxServiceTests {
    private OutboxRepository repository;
    private KafkaTemplate<String, String> kafka;
    private OutboxService service;
    private MongoOutbox event;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        repository = mock(OutboxRepository.class);
        kafka = mock(KafkaTemplate.class);
        service = new OutboxService(repository, kafka);
        Instant now = Instant.now();
        event = new MongoOutbox("evento", "entrega", "topico", "payload", OutboxStatus.PROCESSING, 1,
            now, now, now.plusSeconds(30), "reserva", null, null);
        when(repository.claimNext(any(), any(), anyString())).thenReturn(Optional.of(event), Optional.empty());
        when(repository.markPublished(anyString(), anyString(), any())).thenReturn(true);
        when(repository.reschedule(anyString(), anyString(), any(), anyString())).thenReturn(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void marcaPublicadaSomenteAposConfirmacaoDoKafka() throws Exception {
        CompletableFuture<SendResult<String, String>> ack = mock(CompletableFuture.class);
        when(kafka.send("topico", "entrega", "payload")).thenReturn(ack);
        service.publishPending();
        var order = inOrder(kafka, ack, repository);
        order.verify(kafka).send("topico", "entrega", "payload");
        order.verify(ack).get(10, TimeUnit.SECONDS);
        order.verify(repository).markPublished(eq("evento"), anyString(), any());
        verify(repository, never()).reschedule(anyString(), anyString(), any(), anyString());
    }

    @Test
    void falhaNoBrokerReagendaSemMarcarComoPublicada() {
        when(kafka.send("topico", "entrega", "payload"))
            .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Broker indisponivel")));
        Instant before = Instant.now();
        service.publishPending();
        var retryAt = ArgumentCaptor.forClass(Instant.class);
        verify(repository).reschedule(eq("evento"), anyString(), retryAt.capture(), eq("ExecutionException"));
        assertThat(retryAt.getValue()).isAfterOrEqualTo(before.plusSeconds(2));
        verify(repository, never()).markPublished(anyString(), anyString(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void interrupcaoPreservaSinalEInterrompeLote() throws Exception {
        CompletableFuture<SendResult<String, String>> ack = mock(CompletableFuture.class);
        when(ack.get(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException());
        when(kafka.send("topico", "entrega", "payload")).thenReturn(ack);
        try {
            service.publishPending();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(repository, times(1)).claimNext(any(), any(), anyString());
            verify(repository).reschedule(eq("evento"), anyString(), any(), eq("InterruptedException"));
        } finally {
            Thread.interrupted();
        }
    }
}
