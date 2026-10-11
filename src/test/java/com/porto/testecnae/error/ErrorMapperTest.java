package com.porto.testecnae.error;

import com.porto.testecnae.dto.CadastroSecundarioRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@ExtendWith(OutputCaptureExtension.class)
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
    @Nested
    @DisplayName("erros de validacao por campo")
    class ErrosCampo {

        private final CadastroSecundarioRequest alvo = new CadastroSecundarioRequest("x", "y", "z");

        @Test
        @DisplayName("ordena pela ordem de declaracao dos campos, nao pela ordem das violacoes")
        void ordenaPelaDeclaracao() {
            var binding = binding(
                    erro("codigoCnae", "CNAE_VAL_0006"),
                    erro("nomeFantasia", "CADSEC_VAL_0001"),
                    erro("documento", "CADSEC_VAL_0004"));

            assertThat(mapper.toErrosCampo(binding))
                    .extracting(ErroCampo::campo, ErroCampo::code)
                    .containsExactly(
                            tuple("nomeFantasia", "CADSEC-VAL-0001"),
                            tuple("documento", "CADSEC-VAL-0004"),
                            tuple("codigoCnae", "CNAE-VAL-0006"));
        }

        @Test
        @DisplayName("mantem um erro por campo: o de menor ordem no catalogo")
        void umErroPorCampo() {
            var binding = binding(
                    erro("nomeFantasia", "CADSEC_VAL_0002"),
                    erro("nomeFantasia", "CADSEC_VAL_0001"));

            assertThat(mapper.toErrosCampo(binding))
                    .singleElement()
                    .extracting(ErroCampo::code).isEqualTo("CADSEC-VAL-0001");
        }

        @Test
        @DisplayName("mensagem fora do catalogo vira GEN-VAL-0002, sem quebrar")
        void mensagemForaDoCatalogo() {
            assertThat(mapper.toErrosCampo(binding(erro("documento", "must not be blank"))))
                    .singleElement()
                    .extracting(ErroCampo::code).isEqualTo("GEN-VAL-0002");
        }

        @Test
        @DisplayName("ErroCampo traz a mensagem do catalogo")
        void mensagemDoCatalogo() {
            assertThat(ErroCampo.of("documento", ErrorCode.CADSEC_VAL_0005))
                    .isEqualTo(new ErroCampo("documento", "CADSEC-VAL-0005", ErrorCode.CADSEC_VAL_0005.getMensagem()));
        }

        @Test
        @DisplayName("1 erro: code e detail do proprio campo, errors com 1 item")
        void problemDetailUmErro() {
            var erros = List.of(ErroCampo.of("documento", ErrorCode.CADSEC_VAL_0005));

            var problem = mapper.toProblemDetail(erros, "/api/cadastros-secundarios");

            assertThat(problem.getStatus()).isEqualTo(400);
            assertThat(problem.getDetail()).isEqualTo(ErrorCode.CADSEC_VAL_0005.getMensagem());
            assertThat(problem.getProperties())
                    .containsEntry(ErrorMapper.PROPRIEDADE_CODIGO, "CADSEC-VAL-0005")
                    .containsEntry(ErrorMapper.PROPRIEDADE_ERROS, erros);
        }

        @Test
        @DisplayName("N erros: code do primeiro, detail com a quantidade, errors com N itens")
        void problemDetailVariosErros() {
            var erros = List.of(
                    ErroCampo.of("nomeFantasia", ErrorCode.CADSEC_VAL_0001),
                    ErroCampo.of("codigoCnae", ErrorCode.CNAE_VAL_0006));

            var problem = mapper.toProblemDetail(erros, "/api/cadastros-secundarios");

            assertThat(problem.getDetail()).isEqualTo("A requisicao possui 2 campos invalidos.");
            assertThat(problem.getProperties())
                    .containsEntry(ErrorMapper.PROPRIEDADE_CODIGO, "CADSEC-VAL-0001")
                    .containsEntry(ErrorMapper.PROPRIEDADE_ERROS, erros);
        }

        @Test
        @DisplayName("log registra todos os codigos")
        void logRegistraCodigos(CapturedOutput output) {
            mapper.log(List.of(
                    ErroCampo.of("nomeFantasia", ErrorCode.CADSEC_VAL_0001),
                    ErroCampo.of("documento", ErrorCode.CADSEC_VAL_0005)), "/api/cadastros-secundarios");

            assertThat(output).contains(
                    "errorCode=CADSEC-VAL-0001 status=400 path=/api/cadastros-secundarios",
                    "errors=CADSEC-VAL-0001,CADSEC-VAL-0005");
        }

        private BeanPropertyBindingResult binding(FieldError... erros) {
            var binding = new BeanPropertyBindingResult(alvo, "request");
            for (var erro : erros) {
                binding.addError(erro);
            }
            return binding;
        }

        private static FieldError erro(String campo, String mensagem) {
            return new FieldError("request", campo, mensagem);
        }
    }

    @Test
    @DisplayName("porCodigo encontra o ErrorCode pelo codigo publicado")
    void porCodigo() {
        assertThat(ErrorCode.porCodigo("CADSEC-NEG-0002")).isEqualTo(ErrorCode.CADSEC_NEG_0002);
    }

    @Test
    @DisplayName("porCodigo com codigo inexistente lanca IllegalArgumentException")
    void porCodigoInexistente() {
        assertThatThrownBy(() -> ErrorCode.porCodigo("XYZ-VAL-9999")).isInstanceOf(IllegalArgumentException.class);
    }
}
