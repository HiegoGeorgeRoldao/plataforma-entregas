package br.com.hiego.entregas.entity;

import br.com.hiego.entregas.enuns.StatusEntrega;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "entrega")
public class Entrega {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID usuarioId;

    @Column(nullable = false, length = 8)
    private String cepOrigem;

    @Column(nullable = false, length = 8)
    private String cepDestino;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusEntrega status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Entrega() {}

    /**
     * Cria uma entrega solicitada com novo identificador e horario inicial.
     *
     * @param usuarioId dono da entrega
     * @param cepOrigem CEP de origem
     * @param cepDestino CEP de destino
     * @param agora instante da solicitacao
     * @return entidade ainda nao persistida
     */
    public static Entrega solicitar(UUID usuarioId, String cepOrigem, String cepDestino, Instant agora) {
        Entrega entrega = new Entrega();
        entrega.id = UUID.randomUUID();
        entrega.usuarioId = usuarioId;
        entrega.cepOrigem = cepOrigem;
        entrega.cepDestino = cepDestino;
        entrega.status = StatusEntrega.SOLICITADA;
        entrega.createdAt = agora;
        entrega.updatedAt = agora;
        return entrega;
    }

    public UUID getId() { return id; }
    public UUID getUsuarioId() { return usuarioId; }
    public String getCepOrigem() { return cepOrigem; }
    public String getCepDestino() { return cepDestino; }
    public StatusEntrega getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    /**
     * Aplica o resultado da analise e atualiza o horario da ultima alteracao.
     *
     * @param resultado estado final da analise
     * @throws IllegalArgumentException se o resultado ainda for {@code SOLICITADA}
     */
    public void concluirAnalise(StatusEntrega resultado) {
        if (resultado == StatusEntrega.SOLICITADA) throw new IllegalArgumentException("Resultado invalido");
        this.status = resultado;
        this.updatedAt = Instant.now();
    }
}
