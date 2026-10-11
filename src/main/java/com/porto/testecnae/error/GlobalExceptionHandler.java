package com.porto.testecnae.error;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduz excecoes em respostas {@link ProblemDetail} com o codigo do {@link ErrorCode}.
 * <p>
 * Estende {@link ResponseEntityExceptionHandler} para manter o status correto das demais excecoes
 * padrao do Spring MVC (JSON invalido, metodo nao suportado, rota inexistente, ...).
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final ErrorMapper errorMapper;

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<Object> handleApplicationException(ApplicationException ex, HttpServletRequest request) {
        // O detalhe pode conter valores enviados pelo cliente; no log vai so a mensagem fixa do catalogo
        var errorCode = ex.getErrorCode();
        return responder(errorCode, ex.getMessage(), errorCode.getMensagem(), request.getRequestURI(), ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedException(Exception ex, HttpServletRequest request) {
        // O detalhe tecnico vai apenas para o log, nunca para o cliente.
        var errorCode = ErrorCode.GEN_INT_0001;
        return responder(errorCode, errorCode.getMensagem(), ex.getMessage(), request.getRequestURI(), ex);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var detalhe = "Parametro obrigatorio '%s' nao informado.".formatted(ex.getParameterName());
        return responder(ErrorCode.GEN_VAL_0001, detalhe, detalhe, path(request), ex);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var detalhe = "Parametro '%s' com valor invalido.".formatted(ex.getPropertyName());
        return responder(ErrorCode.GEN_VAL_0002, detalhe, detalhe, path(request), ex);
    }

    private ResponseEntity<Object> responder(
            ErrorCode errorCode, String detalheCliente, String detalheLog, String path, Throwable causa) {
        errorMapper.log(errorCode, detalheLog, path, causa);
        return ResponseEntity.status(errorCode.getStatus())
                .body(errorMapper.toProblemDetail(errorCode, detalheCliente, path));
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI()
                : request.getDescription(false);
    }
}
