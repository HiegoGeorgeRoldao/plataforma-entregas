package br.com.hiego.analise.entity;

import br.com.hiego.analise.filds.MongoDocumentFields;
import br.com.hiego.analise.enuns.OutboxStatus;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document("outbox_events")
public record MongoOutbox(
    @Id String id,
    String aggregateId,
    String topic,
    String payload,
    @Field(MongoDocumentFields.STATUS) OutboxStatus status,
    @Field(MongoDocumentFields.ATTEMPTS) int attempts,
    @Field(MongoDocumentFields.NEXT_ATTEMPT_AT) Instant nextAttemptAt,
    @Field(MongoDocumentFields.CREATED_AT) Instant createdAt,
    @Field(MongoDocumentFields.LOCKED_UNTIL) Instant lockedUntil,
    @Field(MongoDocumentFields.LEASE_TOKEN) String leaseToken,
    @Field(MongoDocumentFields.PUBLISHED_AT) Instant publishedAt,
    @Field(MongoDocumentFields.LAST_ERROR) String lastError
) {}
