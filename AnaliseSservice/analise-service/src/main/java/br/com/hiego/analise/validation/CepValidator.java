package br.com.hiego.analise.validation;

import java.util.regex.Pattern;

/** Valida o formato do contrato de CEP: exatamente oito digitos ASCII, sem mascara. */
public final class CepValidator {
    private static final Pattern CEP_PATTERN = Pattern.compile("[0-9]{8}");

    /** Restringe a criação de instâncias do validador estático. */
    private CepValidator() {}

    /** Verifica o formato; a existencia do CEP continua sendo consultada no ViaCEP. */
    public static boolean isValid(String cep) {
        return cep != null && CEP_PATTERN.matcher(cep).matches();
    }
}
