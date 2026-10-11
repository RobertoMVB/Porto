package com.porto.testecnae.error;

import lombok.Getter;

/**
 * Excecao de aplicacao identificada por um {@link ErrorCode}.
 */
@Getter
public class ApplicationException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApplicationException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMensagem());
    }

    public ApplicationException(ErrorCode errorCode, String detalhe) {
        super(detalhe);
        this.errorCode = errorCode;
    }
}
