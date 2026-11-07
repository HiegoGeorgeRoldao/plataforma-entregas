package br.com.hiego.analise.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document("processed_events")
public record ProcessedEvent(@Id String id, Instant processedAt) {}
