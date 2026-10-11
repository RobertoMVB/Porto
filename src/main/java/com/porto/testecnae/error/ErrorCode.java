package com.porto.testecnae.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Catalogo unico de codigos de erro da aplicacao.
 * <p>
 * Formato: {@code <DOMINIO>-<CATEGORIA>-<NNNN>}
 * <ul>
 *     <li>DOMINIO: {@code GEN} (generico), {@code CNAE}, ...</li>
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
    GEN_INT_0001("GEN-INT-0001", HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado."),

    CNAE_VAL_0001("CNAE-VAL-0001", HttpStatus.BAD_REQUEST, "O termo de busca deve ser informado."),
    CNAE_VAL_0002("CNAE-VAL-0002", HttpStatus.BAD_REQUEST, "O termo de busca nao pode ser vazio."),
    CNAE_VAL_0003("CNAE-VAL-0003", HttpStatus.BAD_REQUEST, "O termo de busca excede o tamanho maximo permitido.");

    private final String codigo;
    private final HttpStatus status;
    private final String mensagem;
}
