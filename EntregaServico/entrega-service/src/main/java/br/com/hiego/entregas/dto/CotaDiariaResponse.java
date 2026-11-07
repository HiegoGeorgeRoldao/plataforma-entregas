package br.com.hiego.entregas.dto;

import java.time.Instant;

public record CotaDiariaResponse(int limite, long utilizadas, long disponiveis,
                                 Instant reiniciaEm, boolean habilitada) {}
