package br.com.hiego.analise.repository.mongo;

import br.com.hiego.analise.filds.MongoDocumentFields;
import br.com.hiego.analise.enuns.OutboxStatus;
import br.com.hiego.analise.entity.MongoOutbox;
import br.com.hiego.analise.repository.OutboxRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@Repository
public class MongoOutboxRepository implements OutboxRepository {
    private final MongoTemplate mongo;

    /** Recebe o cliente MongoDB usado nas operações da Outbox. */
    public MongoOutboxRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** {@inheritDoc} */
    @Override
    public void insert(MongoOutbox event) {
        mongo.insert(event);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<MongoOutbox> claimNext(Instant now, Instant lockedUntil, String leaseToken) {
        var criteria = new Criteria().orOperator(
            where(MongoDocumentFields.STATUS).is(OutboxStatus.PENDING.name()).and(MongoDocumentFields.NEXT_ATTEMPT_AT).lte(now),
            where(MongoDocumentFields.STATUS).is(OutboxStatus.PROCESSING.name()).and(MongoDocumentFields.LOCKED_UNTIL).lte(now));
        var query = new Query(criteria).with(Sort.by(MongoDocumentFields.CREATED_AT));
        var update = new Update().set(MongoDocumentFields.STATUS, OutboxStatus.PROCESSING.name()).set(MongoDocumentFields.LOCKED_UNTIL, lockedUntil)
            .set(MongoDocumentFields.LEASE_TOKEN, leaseToken).inc(MongoDocumentFields.ATTEMPTS, 1);
        return Optional.ofNullable(mongo.findAndModify(query, update,
            FindAndModifyOptions.options().returnNew(true), MongoOutbox.class));
    }

    /** {@inheritDoc} */
    @Override
    public boolean markPublished(String eventId, String leaseToken, Instant publishedAt) {
        var update = new Update().set(MongoDocumentFields.STATUS, OutboxStatus.PUBLISHED.name()).set(MongoDocumentFields.PUBLISHED_AT, publishedAt)
            .unset(MongoDocumentFields.LOCKED_UNTIL).unset(MongoDocumentFields.LEASE_TOKEN).unset(MongoDocumentFields.LAST_ERROR);
        return mongo.updateFirst(owned(eventId, leaseToken), update, MongoOutbox.class).getModifiedCount() > 0;
    }

    /** {@inheritDoc} */
    @Override
    public boolean reschedule(String eventId, String leaseToken, Instant nextAttemptAt, String error) {
        var update = new Update().set(MongoDocumentFields.STATUS, OutboxStatus.PENDING.name()).set(MongoDocumentFields.NEXT_ATTEMPT_AT, nextAttemptAt)
            .set(MongoDocumentFields.LAST_ERROR, error).unset(MongoDocumentFields.LOCKED_UNTIL).unset(MongoDocumentFields.LEASE_TOKEN);
        return mongo.updateFirst(owned(eventId, leaseToken), update, MongoOutbox.class).getModifiedCount() > 0;
    }

    /** Monta o filtro pelo ID do evento e pelo token do publicador que detém a reserva. */
    private Query owned(String eventId, String leaseToken) {
        return new Query(where(MongoDocumentFields.ID).is(eventId).and(MongoDocumentFields.LEASE_TOKEN).is(leaseToken));
    }
}
