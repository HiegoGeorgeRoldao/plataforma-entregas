package br.com.hiego.analise.filds;

/** Nomes persistidos usados nas queries, indices e mapeamentos MongoDB. Mudancas exigem migracao dos dados. */
public final class MongoDocumentFields {
    public static final String ID = "_id";
    public static final String USUARIO_ID = "usuarioId";
    public static final String STATUS = "status";
    public static final String NEXT_ATTEMPT_AT = "nextAttemptAt";
    public static final String CREATED_AT = "createdAt";
    public static final String LOCKED_UNTIL = "lockedUntil";
    public static final String LEASE_TOKEN = "leaseToken";
    public static final String ATTEMPTS = "attempts";
    public static final String PUBLISHED_AT = "publishedAt";
    public static final String LAST_ERROR = "lastError";

    private MongoDocumentFields() {}
}
