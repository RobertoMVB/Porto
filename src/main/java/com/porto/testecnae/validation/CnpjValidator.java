package com.porto.testecnae.validation;

import com.porto.testecnae.error.ErrorCode;
import com.porto.testecnae.util.TextoNormalizador;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Locale;
import java.util.regex.Pattern;

public class CnpjValidator implements ConstraintValidator<Cnpj, String> {

    /**
     * CNPJ numerico ou alfanumerico (vigente desde jul/2026): 12 posicoes [A-Z0-9] + 2 digitos verificadores.
     * Espera o valor ja normalizado por {@link #normalizar(String)}.
     */
    static final Pattern FORMATO_CNPJ = Pattern.compile("[A-Z0-9]{12}[0-9]{2}");

    /** CPF sem mascara (11 digitos) ou com a mascara padrao (000.000.000-00). */
    static final Pattern FORMATO_CPF = Pattern.compile("\\d{11}|\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}");

    /** Pontuacao de mascara removida em qualquer posicao (decisao: aceitar com e sem mascara). */
    private static final Pattern PONTUACAO_MASCARA = Pattern.compile("[./\\- ]");

    private static final int[] PESOS_PRIMEIRO_DV = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] PESOS_SEGUNDO_DV = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    @Override
    public boolean isValid(String documento, ConstraintValidatorContext context) {
        var erro = classificar(documento);
        if (erro == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(erro.name()).addConstraintViolation();
        return false;
    }

    /**
     * Normaliza espacos (inclusive NBSP), coloca em maiusculas (o CNPJ alfanumerico oficial usa apenas maiusculas)
     * e remove a mascara ({@code .}, {@code /}, {@code -} e espacos em qualquer posicao).
     * Ex.: {@code " 12.abc.345/01de-35 "} vira {@code "12ABC34501DE35"}.
     */
    public static String normalizar(String documento) {
        var normalizado = TextoNormalizador.normalizarEspacos(documento);
        if (normalizado == null) {
            return null;
        }
        return PONTUACAO_MASCARA.matcher(normalizado.toUpperCase(Locale.ROOT)).replaceAll("");
    }

    /**
     * Gera no maximo um erro, na ordem: nao informado, CPF, formato, sequencia repetida, digito verificador.
     *
     * @return o erro do documento, ou {@code null} se for um CNPJ valido
     */
    static ErrorCode classificar(String documento) {
        if (documento == null || documento.isBlank()) {
            return ErrorCode.CADSEC_VAL_0003;
        }
        if (FORMATO_CPF.matcher(documento).matches()) {
            return ErrorCode.CADSEC_VAL_0005;
        }
        if (!FORMATO_CNPJ.matcher(documento).matches()) {
            return ErrorCode.CADSEC_VAL_0004;
        }
        // Sequencias repetidas passam no calculo do DV (ex.: 00000000000000), mas nao sao CNPJs reais
        if (documento.chars().distinct().count() == 1) {
            return ErrorCode.CADSEC_VAL_0007;
        }
        if (!documento.endsWith(digitosVerificadores(documento.substring(0, 12)))) {
            return ErrorCode.CADSEC_VAL_0006;
        }
        return null;
    }

    /**
     * Calcula os 2 digitos verificadores (regra oficial da Receita, valida para CNPJ numerico e alfanumerico):
     * cada caractere vale seu codigo ASCII menos 48 ('0'-'9' = 0-9, 'A'-'Z' = 17-42); modulo 11 com pesos de 2 a 9;
     * resto menor que 2 gera digito 0, senao 11 menos o resto.
     *
     * @param base as 12 primeiras posicoes do CNPJ, ja em maiusculas
     */
    static String digitosVerificadores(String base) {
        var primeiro = digito(base, PESOS_PRIMEIRO_DV);
        var segundo = digito(base + primeiro, PESOS_SEGUNDO_DV);
        return "" + primeiro + segundo;
    }

    private static int digito(String valor, int[] pesos) {
        var soma = 0;
        for (var i = 0; i < pesos.length; i++) {
            soma += (valor.charAt(i) - '0') * pesos[i];
        }
        var resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
