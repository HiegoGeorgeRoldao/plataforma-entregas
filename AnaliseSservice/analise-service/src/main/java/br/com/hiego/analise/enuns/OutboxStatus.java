package br.com.hiego.analise.enuns;

/** Estados persistidos no ciclo de publicacao da Outbox. */
public enum OutboxStatus {
    PENDING,
    PROCESSING,
    PUBLISHED
}
