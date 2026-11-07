package br.com.hiego.analise.entity;

import br.com.hiego.analise.filds.MongoDocumentFields;
import org.springframework.data.mongodb.core.mapping.Field;
import br.com.hiego.analise.enuns.AnalysisStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.Map;

@Document("analises")
public record Analise(
    @Id String id,
    @Field(MongoDocumentFields.USUARIO_ID) String usuarioId,
    @Field(MongoDocumentFields.STATUS) AnalysisStatus status,
    String observacao,
    Map<String, Object> enderecoOrigem,
    Map<String, Object> enderecoDestino,
    Map<String, Object> previsao,
    Instant createdAt
) {}
