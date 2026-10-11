package com.porto.testecnae.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.net.URI;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ponto unico de traducao de um {@link ErrorCode} para resposta HTTP (RFC 9457) e para log.
 * <p>
 * O log usa formato chave=valor ({@code errorCode=... status=...}) para facilitar busca e extracao de metricas.
 */
@Slf4j
@Component
public class ErrorMapper {

    public static final String PROPRIEDADE_CODIGO = "code";
    public static final String PROPRIEDADE_ERROS = "errors";

    public ProblemDetail toProblemDetail(ErrorCode errorCode, String detalhe, String path) {
        var problem = ProblemDetail.forStatusAndDetail(errorCode.getStatus(), detalhe);
        problem.setTitle(errorCode.getStatus().getReasonPhrase());
        problem.setInstance(URI.create(path));
        problem.setProperty(PROPRIEDADE_CODIGO, errorCode.getCodigo());
        return problem;
    }

    /**
     * ProblemDetail de validacao: {@code code} e o do primeiro erro, {@code errors} traz de 1 a N erros de campo.
     */
    public ProblemDetail toProblemDetail(List<ErroCampo> erros, String path) {
        var primeiro = ErrorCode.porCodigo(erros.getFirst().code());
        var detalhe = erros.size() == 1
                ? erros.getFirst().mensagem()
                : "A requisicao possui %d campos invalidos.".formatted(erros.size());
        var problem = toProblemDetail(primeiro, detalhe, path);
        problem.setProperty(PROPRIEDADE_ERROS, erros);
        return problem;
    }

    /**
     * Converte as violacoes de Bean Validation em {@link ErroCampo}. A mensagem de cada constraint deve ser o nome
     * de um {@link ErrorCode}. Mantem um erro por campo (o de menor ordem no catalogo, ex.: "obrigatorio" antes de
     * "tamanho") e ordena os campos pela ordem de declaracao no objeto validado.
     */
    public List<ErroCampo> toErrosCampo(BindingResult bindingResult) {
        var ordemCampos = ordemDeclaracao(bindingResult.getTarget());

        Map<String, ErrorCode> erroPorCampo = bindingResult.getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, ErrorMapper::errorCodeDaViolacao,
                        (a, b) -> a.ordinal() <= b.ordinal() ? a : b));

        return erroPorCampo.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> ordemCampos.getOrDefault(e.getKey(), Integer.MAX_VALUE)))
                .map(e -> ErroCampo.of(e.getKey(), e.getValue()))
                .toList();
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

    /**
     * Log de validacao: registra todos os codigos (para metricas por campo), nunca os valores enviados.
     */
    public void log(List<ErroCampo> erros, String path) {
        var primeiro = ErrorCode.porCodigo(erros.getFirst().code());
        log.warn("errorCode={} status={} path={} detail=\"{}\" errors={}",
                primeiro.getCodigo(), primeiro.getStatus().value(), path, primeiro.getMensagem(),
                erros.stream().map(ErroCampo::code).collect(Collectors.joining(",")));
    }

    // Mensagem fora do catalogo (constraint sem codigo) cai em erro generico de formato, em vez de quebrar
    private static ErrorCode errorCodeDaViolacao(FieldError fieldError) {
        try {
            return ErrorCode.valueOf(fieldError.getDefaultMessage());
        } catch (IllegalArgumentException | NullPointerException ex) {
            return ErrorCode.GEN_VAL_0002;
        }
    }

    private static Map<String, Integer> ordemDeclaracao(Object alvo) {
        if (alvo == null) {
            return Map.of();
        }
        var nomes = alvo.getClass().isRecord()
                ? Arrays.stream(alvo.getClass().getRecordComponents()).map(c -> c.getName()).toList()
                : Arrays.stream(alvo.getClass().getDeclaredFields()).map(f -> f.getName()).toList();
        return nomes.stream().collect(Collectors.toMap(Function.identity(), nomes::indexOf));
    }
}
