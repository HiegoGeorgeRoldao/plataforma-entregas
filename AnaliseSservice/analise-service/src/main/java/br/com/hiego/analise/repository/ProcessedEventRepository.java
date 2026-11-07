package br.com.hiego.analise.repository;

import br.com.hiego.analise.entity.ProcessedEvent;

/** Registro de eventos processados para deduplicacao do consumidor. */
public interface ProcessedEventRepository {
    /** Informa se a ocorrencia do evento ja foi registrada. */
    boolean existsById(String eventId);

    /** Insere a ocorrencia, usando seu identificador unico para impedir duplicatas. */
    void insert(ProcessedEvent event);
}
