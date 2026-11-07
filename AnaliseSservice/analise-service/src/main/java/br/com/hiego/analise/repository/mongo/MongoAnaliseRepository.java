package br.com.hiego.analise.repository.mongo;

import br.com.hiego.analise.filds.MongoDocumentFields;
import br.com.hiego.analise.entity.Analise;
import br.com.hiego.analise.repository.AnaliseRepository;
import java.util.Optional;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@Repository
public class MongoAnaliseRepository implements AnaliseRepository {
    private final MongoTemplate mongo;

    /** Recebe o cliente MongoDB usado para consultar e inserir análises. */
    public MongoAnaliseRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Analise> findByEntregaIdAndUsuarioId(String entregaId, String usuarioId) {
        var query = new Query(where(MongoDocumentFields.ID).is(entregaId).and(MongoDocumentFields.USUARIO_ID).is(usuarioId));

        return Optional.ofNullable(mongo.findOne(query, Analise.class));
    }

    /** {@inheritDoc} */
    @Override
    public void insert(Analise analise) {
        mongo.insert(analise);
    }
}
