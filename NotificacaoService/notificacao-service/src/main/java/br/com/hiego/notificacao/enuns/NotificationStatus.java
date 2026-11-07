package br.com.hiego.notificacao.enuns;

public enum NotificationStatus {
    ANALISADA,
    REJEITADA;

    public static NotificationStatus fromEvent(String value) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Estado final invalido", exception);
        }
    }
}
