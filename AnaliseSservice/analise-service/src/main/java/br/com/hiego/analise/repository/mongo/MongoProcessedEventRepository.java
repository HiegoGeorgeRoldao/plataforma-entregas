package br.com.hiego.analise.repository.mongo;

import br.com.hiego.analise.filds.MongoDocumentFields;
import br.com.hiego.analise.entity.ProcessedEvent;
import br.com.hiego.analise.repository.ProcessedEventRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@Repository
public class MongoProcessedEventRepository implements ProcessedEventRepository {
    private final MongoTemplate mongo;

    /** Recebe o cliente MongoDB usado para registrar e consultar eventos processados. */
    public MongoProcessedEventRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** {@inheritDoc} */
    @Override
    public boolean existsById(String eventId) {
        return mongo.exists(new Query(where(MongoDocumentFields.ID).is(eventId)), ProcessedEvent.class);
    }

    /** {@inheritDoc} */
    @Override
    public void insert(ProcessedEvent event) {
        mongo.insert(event);
    }
}
