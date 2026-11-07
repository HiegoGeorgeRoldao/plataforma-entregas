package br.com.hiego.entregas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SolicitarEntregaRequest(
    @NotBlank(message = "Informe o CEP de origem")
    @Pattern(regexp = "[0-9]{8}", message = "O CEP deve conter exatamente 8 digitos numericos")
    String cepOrigem,
    @NotBlank(message = "Informe o CEP de destino")
    @Pattern(regexp = "[0-9]{8}", message = "O CEP deve conter exatamente 8 digitos numericos")
    String cepDestino
) {}
