package com.porto.testecnae.validation;

import com.porto.testecnae.error.ErrorCode;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class CnpjValidator implements ConstraintValidator<Cnpj, String> {

    /**
     * CNPJ numerico ou alfanumerico (vigente desde jul/2026): 12 posicoes [A-Z0-9] + 2 digitos verificadores.
     * Espera o valor ja normalizado (sem espacos nas pontas e em maiusculas).
     */
    static final Pattern FORMATO_CNPJ = Pattern.compile("[A-Z0-9]{12}[0-9]{2}");

    /** CPF sem mascara (11 digitos) ou com a mascara padrao (000.000.000-00). */
    static final Pattern FORMATO_CPF = Pattern.compile("\\d{11}|\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}");

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
     * @return o erro do documento, ou {@code null} se for um CNPJ em formato valido
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
        return null;
    }
}
