package com.porto.testecnae.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Valida o formato de CNPJ numerico ou alfanumerico (sem mascara) e identifica CPF.
 * Gera no maximo uma violacao, com o nome do {@link com.porto.testecnae.error.ErrorCode} como mensagem:
 * <ol>
 *     <li>nulo ou vazio: {@code CADSEC_VAL_0003}</li>
 *     <li>CPF (11 digitos, com ou sem mascara): {@code CADSEC_VAL_0005}</li>
 *     <li>fora do formato: {@code CADSEC_VAL_0004}</li>
 * </ol>
 * O digito verificador nao e validado (decisao de negocio).
 */
@Documented
@Constraint(validatedBy = CnpjValidator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Cnpj {

    String message() default "CADSEC_VAL_0004";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
