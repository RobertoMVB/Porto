package com.porto.testecnae.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * Ponto unico de traducao de um {@link ErrorCode} para resposta HTTP (RFC 9457) e para log.
 * <p>
 * O log usa formato chave=valor ({@code errorCode=... status=...}) para facilitar busca e extracao de metricas.
 */
@Slf4j
@Component
public class ErrorMapper {

    public static final String PROPRIEDADE_CODIGO = "code";

    public ProblemDetail toProblemDetail(ErrorCode errorCode, String detalhe, String path) {
        var problem = ProblemDetail.forStatusAndDetail(errorCode.getStatus(), detalhe);
        problem.setTitle(errorCode.getStatus().getReasonPhrase());
        problem.setInstance(URI.create(path));
        problem.setProperty(PROPRIEDADE_CODIGO, errorCode.getCodigo());
        return problem;
    }

    public void log(ErrorCode errorCode, String detalhe, String path, Throwable causa) {
        if (errorCode.getStatus().is5xxServerError()) {
            log.error("errorCode={} status={} path={} detail=\"{}\"",
                    errorCode.getCodigo(), errorCode.getStatus().value(), path, detalhe, causa);
        } else {
            log.warn("errorCode={} status={} path={} detail=\"{}\"",
                    errorCode.getCodigo(), errorCode.getStatus().value(), path, detalhe);
        }
    }
}
