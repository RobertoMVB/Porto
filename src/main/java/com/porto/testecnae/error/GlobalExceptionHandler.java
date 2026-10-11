package com.porto.testecnae.error;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.hibernate.LazyInitializationException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * Traduz excecoes em respostas {@link ProblemDetail} com o codigo do {@link ErrorCode}.
 * <p>
 * Estende {@link ResponseEntityExceptionHandler} para manter o status correto das excecoes padrao do Spring MVC.
 * Regra: <b>toda resposta de erro tem {@code code}</b> (ver {@link #handleExceptionInternal}).
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

    /** Relacionamento lazy acessado fora de transacao: bug de codigo (falta join fetch ou @Transactional). */
    @ExceptionHandler(LazyInitializationException.class)
    public ResponseEntity<Object> handleLazyInitialization(LazyInitializationException ex, HttpServletRequest request) {
        var errorCode = ErrorCode.GEN_INT_0002;
        return responder(errorCode, errorCode.getMensagem(), ex.getMessage(), request.getRequestURI(), ex);
    }

    /** Banco indisponivel: falha ao abrir transacao ou obter conexao. Infraestrutura, o cliente pode tentar de novo. */
    @ExceptionHandler({CannotCreateTransactionException.class, DataAccessResourceFailureException.class})
    public ResponseEntity<Object> handleBancoIndisponivel(Exception ex, HttpServletRequest request) {
        var errorCode = ErrorCode.GEN_INT_0003;
        return responder(errorCode, errorCode.getMensagem(), ex.getMessage(), request.getRequestURI(), ex);
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

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var erros = errorMapper.toErrosCampo(ex.getBindingResult());
        var path = path(request);
        errorMapper.log(erros, path);
        var problem = errorMapper.toProblemDetail(erros, path);
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // A mensagem do Jackson pode conter trechos do corpo enviado: nao vai para a resposta nem para o log
        var errorCode = ErrorCode.GEN_VAL_0005;
        return responder(errorCode, errorCode.getMensagem(), errorCode.getMensagem(), path(request), ex);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return responderComCatalogo(ErrorCode.GEN_NEG_0001, headers, request, ex);
    }

    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(
            NoHandlerFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return responderComCatalogo(ErrorCode.GEN_NEG_0001, headers, request, ex);
    }

    /** Mantem o header {@code Allow} e informa os metodos aceitos (definidos pelo codigo, nao pelo cliente). */
    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var errorCode = ErrorCode.GEN_VAL_0006;
        var aceitos = ex.getSupportedHttpMethods() == null ? "" : ex.getSupportedHttpMethods().stream()
                .map(HttpMethod::name).sorted().collect(Collectors.joining(", "));
        var detalhe = aceitos.isEmpty() ? errorCode.getMensagem() : errorCode.getMensagem() + " Metodos aceitos: " + aceitos + ".";
        return responder(errorCode, detalhe, errorCode.getMensagem(), path(request), ex, headers);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return responderComCatalogo(ErrorCode.GEN_VAL_0007, headers, request, ex);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotAcceptable(
            HttpMediaTypeNotAcceptableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return responderComCatalogo(ErrorCode.GEN_VAL_0008, headers, request, ex);
    }

    /**
     * Rede de seguranca: toda excecao padrao do Spring MVC sem tratamento especifico passa por aqui.
     * Garante que nenhuma resposta de erro saia sem {@code code}: 4xx vira {@code GEN-VAL-0009} e 5xx vira
     * {@code GEN-INT-0001}, preservando o status HTTP original. A mensagem tecnica do Spring nao vai para o cliente.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem && problem.getProperties() != null
                && problem.getProperties().containsKey(ErrorMapper.PROPRIEDADE_CODIGO)) {
            return super.handleExceptionInternal(ex, body, headers, statusCode, request);
        }
        var errorCode = statusCode.is5xxServerError() ? ErrorCode.GEN_INT_0001 : ErrorCode.GEN_VAL_0009;
        var path = path(request);
        // No log vai o tipo da excecao (identifica o caso sem expor valores enviados pelo cliente)
        errorMapper.log(errorCode, statusCode.value(), ex.getClass().getSimpleName(), path, ex);
        var problem = errorMapper.toProblemDetail(errorCode, errorCode.getMensagem(), path);
        problem.setStatus(statusCode.value());
        var httpStatus = HttpStatus.resolve(statusCode.value());
        problem.setTitle(httpStatus == null ? null : httpStatus.getReasonPhrase());
        return ResponseEntity.status(statusCode).headers(headers).body(problem);
    }

    private ResponseEntity<Object> responderComCatalogo(
            ErrorCode errorCode, HttpHeaders headers, WebRequest request, Throwable causa) {
        return responder(errorCode, errorCode.getMensagem(), errorCode.getMensagem(), path(request), causa, headers);
    }

    private ResponseEntity<Object> responder(
            ErrorCode errorCode, String detalheCliente, String detalheLog, String path, Throwable causa) {
        return responder(errorCode, detalheCliente, detalheLog, path, causa, HttpHeaders.EMPTY);
    }

    private ResponseEntity<Object> responder(ErrorCode errorCode, String detalheCliente, String detalheLog,
                                             String path, Throwable causa, HttpHeaders headers) {
        errorMapper.log(errorCode, detalheLog, path, causa);
        return ResponseEntity.status(errorCode.getStatus())
                .headers(headers)
                .body(errorMapper.toProblemDetail(errorCode, detalheCliente, path));
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI()
                : request.getDescription(false);
    }
}
