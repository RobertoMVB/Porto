package com.porto.testecnae.validation;

import com.porto.testecnae.error.ApplicationException;
import com.porto.testecnae.service.BuscadorCnae;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CodigoCnaeValidator implements ConstraintValidator<CodigoCnae, String> {

    @Override
    public boolean isValid(String codigo, ConstraintValidatorContext context) {
        try {
            BuscadorCnae.normalizarCodigo(codigo);
            return true;
        } catch (ApplicationException ex) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(ex.getErrorCode().name()).addConstraintViolation();
            return false;
        }
    }
}
