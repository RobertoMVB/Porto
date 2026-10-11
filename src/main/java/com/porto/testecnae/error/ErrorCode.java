package com.porto.testecnae.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Catalogo unico de codigos de erro da aplicacao.
 * <p>
 * Formato: {@code <DOMINIO>-<CATEGORIA>-<NNNN>}
 * <ul>
 *     <li>DOMINIO: {@code GEN} (generico), {@code CNAE}, {@code CADSEC} (cadastro secundario), ...</li>
 *     <li>CATEGORIA: {@code VAL} (validacao de entrada), {@code NEG} (regra de negocio), {@code INT} (falha interna/integracao)</li>
 * </ul>
 * Documentacao de cada codigo em {@code docs/error-codes.md}. Codigos publicados nao devem ser reaproveitados.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    GEN_VAL_0001("GEN-VAL-0001", HttpStatus.BAD_REQUEST, "Parametro obrigatorio nao informado."),
    GEN_VAL_0002("GEN-VAL-0002", HttpStatus.BAD_REQUEST, "Parametro com tipo ou formato invalido."),
    GEN_VAL_0003("GEN-VAL-0003", HttpStatus.BAD_REQUEST, "Numero da pagina invalido."),
    GEN_VAL_0004("GEN-VAL-0004", HttpStatus.BAD_REQUEST, "Tamanho da pagina invalido."),
    GEN_VAL_0005("GEN-VAL-0005", HttpStatus.BAD_REQUEST, "Corpo da requisicao ausente ou malformado."),
    GEN_VAL_0006("GEN-VAL-0006", HttpStatus.METHOD_NOT_ALLOWED, "Metodo HTTP nao suportado neste endpoint."),
    GEN_VAL_0007("GEN-VAL-0007", HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "Content-Type nao suportado. Envie o corpo como application/json."),
    GEN_VAL_0008("GEN-VAL-0008", HttpStatus.NOT_ACCEPTABLE,
            "Formato de resposta nao disponivel. A API responde em application/json."),
    /** Rede de seguranca para erros 4xx do Spring sem codigo especifico; o status HTTP original e preservado. */
    GEN_VAL_0009("GEN-VAL-0009", HttpStatus.BAD_REQUEST, "Requisicao invalida."),
    GEN_NEG_0001("GEN-NEG-0001", HttpStatus.NOT_FOUND, "Recurso nao encontrado. Verifique o endereco da requisicao."),
    GEN_INT_0001("GEN-INT-0001", HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado."),
    GEN_INT_0002("GEN-INT-0002", HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno ao carregar dados relacionados."),
    GEN_INT_0003("GEN-INT-0003", HttpStatus.SERVICE_UNAVAILABLE,
            "Servico temporariamente indisponivel. Tente novamente em instantes."),

    CNAE_VAL_0001("CNAE-VAL-0001", HttpStatus.BAD_REQUEST, "O termo de busca deve ser informado."),
    CNAE_VAL_0002("CNAE-VAL-0002", HttpStatus.BAD_REQUEST, "O termo de busca nao pode ser vazio."),
    CNAE_VAL_0003("CNAE-VAL-0003", HttpStatus.BAD_REQUEST, "O termo de busca excede o tamanho maximo permitido."),
    CNAE_VAL_0004("CNAE-VAL-0004", HttpStatus.BAD_REQUEST, "O codigo CNAE deve ser informado."),
    CNAE_VAL_0005("CNAE-VAL-0005", HttpStatus.BAD_REQUEST, "O codigo CNAE nao pode ser vazio."),
    CNAE_VAL_0006("CNAE-VAL-0006", HttpStatus.BAD_REQUEST, "O codigo CNAE deve seguir o formato NNNN-N/NN."),
    CNAE_NEG_0001("CNAE-NEG-0001", HttpStatus.NOT_FOUND, "CNAE nao encontrado."),

    CADSEC_VAL_0001("CADSEC-VAL-0001", HttpStatus.BAD_REQUEST, "O nome fantasia deve ser informado."),
    CADSEC_VAL_0002("CADSEC-VAL-0002", HttpStatus.BAD_REQUEST, "O nome fantasia deve ter no maximo 120 caracteres."),
    CADSEC_VAL_0003("CADSEC-VAL-0003", HttpStatus.BAD_REQUEST, "O documento (CNPJ) deve ser informado."),
    CADSEC_VAL_0004("CADSEC-VAL-0004", HttpStatus.BAD_REQUEST,
            "O documento deve ser um CNPJ com 14 caracteres, com ou sem mascara (12 letras ou numeros e 2 digitos verificadores)."),
    CADSEC_VAL_0005("CADSEC-VAL-0005", HttpStatus.BAD_REQUEST,
            "O documento informado e um CPF. O cadastro secundario aceita apenas CNPJ."),
    CADSEC_VAL_0006("CADSEC-VAL-0006", HttpStatus.BAD_REQUEST, "CNPJ invalido: digito verificador nao confere."),
    CADSEC_VAL_0007("CADSEC-VAL-0007", HttpStatus.BAD_REQUEST, "CNPJ invalido: sequencia de caracteres repetidos."),
    CADSEC_NEG_0001("CADSEC-NEG-0001", HttpStatus.UNPROCESSABLE_CONTENT,
            "Cadastro secundario rejeitado: o CNAE informado nao existe."),
    CADSEC_NEG_0002("CADSEC-NEG-0002", HttpStatus.CONFLICT,
            "Ja existe cadastro secundario para o CNPJ informado.");

    private static final Map<String, ErrorCode> POR_CODIGO = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(ErrorCode::getCodigo, Function.identity()));

    private final String codigo;
    private final HttpStatus status;
    private final String mensagem;

    /**
     * @param codigo codigo publicado, ex.: {@code CADSEC-VAL-0001}
     * @throws IllegalArgumentException se o codigo nao existir no catalogo
     */
    public static ErrorCode porCodigo(String codigo) {
        var errorCode = POR_CODIGO.get(codigo);
        if (errorCode == null) {
            throw new IllegalArgumentException("Codigo de erro desconhecido: " + codigo);
        }
        return errorCode;
    }
}
