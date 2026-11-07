package br.com.hiego.analise.config;

import br.com.hiego.analise.repository.mongo.MongoSchemaRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

/** Configura a transacao compartilhada pelos repositories e a inicializacao do schema. */
@Configuration(proxyBeanMethods = false)
public class MongoConfiguration {

    @Bean
    MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }

    @Bean
    ApplicationRunner initializeCollections(MongoSchemaRepository schema) {
        return args -> schema.initialize();
    }
}
