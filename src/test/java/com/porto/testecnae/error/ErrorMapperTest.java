package com.porto.testecnae.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.URI;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorMapperTest {

    private final ErrorMapper mapper = new ErrorMapper();

    @ParameterizedTest(name = "{0}")
    @EnumSource(ErrorCode.class)
    @DisplayName("cada ErrorCode gera ProblemDetail com status, titulo, detalhe, instance e code")
    void geraProblemDetail(ErrorCode errorCode) {
        var problem = mapper.toProblemDetail(errorCode, "detalhe qualquer", "/api/teste");

        assertThat(problem.getStatus()).isEqualTo(errorCode.getStatus().value());
        assertThat(problem.getTitle()).isEqualTo(errorCode.getStatus().getReasonPhrase());
        assertThat(problem.getDetail()).isEqualTo("detalhe qualquer");
        assertThat(problem.getInstance()).isEqualTo(URI.create("/api/teste"));
        assertThat(problem.getProperties()).containsEntry(ErrorMapper.PROPRIEDADE_CODIGO, errorCode.getCodigo());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ErrorCode.class)
    @DisplayName("codigo segue o formato DOMINIO-CATEGORIA-NNNN e corresponde ao nome da constante")
    void formatoDoCodigo(ErrorCode errorCode) {
        assertThat(errorCode.getCodigo()).matches("[A-Z]+-(VAL|NEG|INT)-\\d{4}");
        assertThat(errorCode.getCodigo()).isEqualTo(errorCode.name().replace('_', '-'));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ErrorCode.class)
    @DisplayName("categoria compativel com o status HTTP")
    void categoriaCompativelComStatus(ErrorCode errorCode) {
        var categoria = errorCode.getCodigo().split("-")[1];
        switch (categoria) {
            case "VAL", "NEG" -> assertThat(errorCode.getStatus().is4xxClientError()).isTrue();
            case "INT" -> assertThat(errorCode.getStatus().is5xxServerError()).isTrue();
            default -> throw new AssertionError("Categoria desconhecida: " + categoria);
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ErrorCode.class)
    @DisplayName("todo codigo tem mensagem padrao")
    void mensagemPadrao(ErrorCode errorCode) {
        assertThat(errorCode.getMensagem()).isNotBlank();
    }

    @Test
    @DisplayName("codigos sao unicos")
    void codigosUnicos() {
        var codigos = Arrays.stream(ErrorCode.values()).map(ErrorCode::getCodigo).toList();

        assertThat(codigos).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("ApplicationException usa a mensagem padrao do codigo quando nenhum detalhe e informado")
    void applicationExceptionMensagemPadrao() {
        var ex = new ApplicationException(ErrorCode.CNAE_VAL_0001);

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CNAE_VAL_0001);
        assertThat(ex.getMessage()).isEqualTo(ErrorCode.CNAE_VAL_0001.getMensagem());
    }
}
