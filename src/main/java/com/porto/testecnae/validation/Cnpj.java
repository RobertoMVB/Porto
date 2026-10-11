package com.porto.testecnae.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Valida CNPJ numerico ou alfanumerico, ja normalizado por {@link CnpjValidator#normalizar(String)}
 * (o request aceita com e sem mascara). Gera no maximo uma violacao, com o nome do
 * {@link com.porto.testecnae.error.ErrorCode} como mensagem:
 * <ol>
 *     <li>nulo ou vazio: {@code CADSEC_VAL_0003}</li>
 *     <li>CPF (11 digitos, com ou sem mascara): {@code CADSEC_VAL_0005}</li>
 *     <li>fora do formato: {@code CADSEC_VAL_0004}</li>
 *     <li>sequencia repetida (ex.: {@code 00000000000000}): {@code CADSEC_VAL_0007}</li>
 *     <li>digito verificador invalido: {@code CADSEC_VAL_0006}</li>
 * </ol>
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
