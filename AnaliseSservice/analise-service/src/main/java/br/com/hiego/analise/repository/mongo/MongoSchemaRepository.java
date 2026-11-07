package br.com.hiego.analise.repository.mongo;

import br.com.hiego.analise.filds.MongoDocumentFields;
import br.com.hiego.analise.entity.Analise;
import br.com.hiego.analise.entity.MongoOutbox;
import br.com.hiego.analise.entity.ProcessedEvent;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Repository;

@Repository
public class MongoSchemaRepository {
    private final MongoTemplate mongo;

    /** Recebe o cliente MongoDB usado para preparar as coleções e os índices. */
    public MongoSchemaRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** Inicializacao idempotente, tolerando criacao concorrente das colecoes. */
    public void initialize() {
        for (Class<?> type : List.of(Analise.class, MongoOutbox.class, ProcessedEvent.class)) {
            if (!mongo.collectionExists(type)) {
                try {
                    mongo.createCollection(type);
                } catch (DataAccessException exception) {
                    if (!mongo.collectionExists(type)) {
                        throw exception;
                    }
                }
            }
        }
        mongo.indexOps(MongoOutbox.class).createIndex(new Index()
            .on(MongoDocumentFields.STATUS, Sort.Direction.ASC).on(MongoDocumentFields.NEXT_ATTEMPT_AT, Sort.Direction.ASC));
        mongo.indexOps(MongoOutbox.class).createIndex(new Index()
            .on(MongoDocumentFields.STATUS, Sort.Direction.ASC).on(MongoDocumentFields.LOCKED_UNTIL, Sort.Direction.ASC));
        mongo.indexOps(Analise.class).createIndex(new Index().on(MongoDocumentFields.USUARIO_ID, Sort.Direction.ASC));
    }
}
