package br.com.hiego.analise.filds;

/** Campos definidos pelos provedores externos, separados dos contratos internos do servico. */
public final class ExternalApiFields {
    private ExternalApiFields() {}

    public static final class ViaCep {
        public static final String ERRO = "erro";
        public static final String LOCALIDADE = "localidade";
        public static final String UF = "uf";
        private ViaCep() {}
    }

    public static final class Cptec {
        public static final String CITY_QUERY = "city";
        public static final String CIDADE = "cidade";
        public static final String NOME = "nome";
        public static final String UF = "uf";
        public static final String ID = "id";
        public static final String PREVISAO = "previsao";
        public static final String DIA = "dia";
        public static final String TEMPO = "tempo";
        public static final String MINIMA = "minima";
        public static final String MAXIMA = "maxima";
        private Cptec() {}
    }
}
