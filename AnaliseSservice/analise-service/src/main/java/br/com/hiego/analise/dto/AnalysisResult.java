package br.com.hiego.analise.dto;

import br.com.hiego.analise.enuns.AnalysisStatus;
import java.util.Map;

public record AnalysisResult(AnalysisStatus status, String observacao, Map<String, Object> origem,
                             Map<String, Object> destino, Map<String, Object> previsao) {}
