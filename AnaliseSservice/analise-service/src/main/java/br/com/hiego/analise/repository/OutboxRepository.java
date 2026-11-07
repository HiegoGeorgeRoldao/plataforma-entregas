package br.com.hiego.analise.repository;

import br.com.hiego.analise.entity.MongoOutbox;
import java.time.Instant;
import java.util.Optional;

/** Persistencia e reserva atomica dos eventos pendentes de publicacao. */
public interface OutboxRepository {
    /** Insere o evento na mesma transacao da analise. */
    void insert(MongoOutbox event);

    /** Reserva um evento elegivel ou recupera uma reserva expirada, incrementando as tentativas. */
    Optional<MongoOutbox> claimNext(Instant now, Instant lockedUntil, String leaseToken);

    /** Marca como publicado somente se a reserva ainda pertence ao token informado. */
    boolean markPublished(String eventId, String leaseToken, Instant publishedAt);

    /** Reagenda somente se a reserva ainda pertence ao token informado. */
    boolean reschedule(String eventId, String leaseToken, Instant nextAttemptAt, String error);
}
