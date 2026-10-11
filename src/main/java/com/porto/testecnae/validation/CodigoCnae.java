package com.porto.testecnae.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Valida o codigo CNAE com as mesmas regras do {@link com.porto.testecnae.service.BuscadorCnae#normalizarCodigo(String)}.
 * A violacao usa como mensagem o nome do {@link com.porto.testecnae.error.ErrorCode} ({@code CNAE_VAL_0004/0005/0006}).
 * Nao verifica se o CNAE existe: isso e responsabilidade do service.
 */
@Documented
@Constraint(validatedBy = CodigoCnaeValidator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CodigoCnae {

    String message() default "CNAE_VAL_0006";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
