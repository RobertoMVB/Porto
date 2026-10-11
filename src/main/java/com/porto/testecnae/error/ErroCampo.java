package com.porto.testecnae.error;

/**
 * Erro de validacao de um campo do corpo da requisicao. Item da lista {@code errors} do ProblemDetail.
 */
public record ErroCampo(
        String campo,
        String code,
        String mensagem
) {

    public static ErroCampo of(String campo, ErrorCode errorCode) {
        return new ErroCampo(campo, errorCode.getCodigo(), errorCode.getMensagem());
    }
}
