package com.porto.testecnae.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * Normalizacao e constraints do request com o {@link Validator} puro (sem Spring).
 * A mensagem de cada violacao e o nome do ErrorCode.
 */
class CadastroSecundarioRequestValidationTest {

    private static final String NOME = "Tech Porto";
    private static final String CNPJ = "12345678000195";
    private static final String CNAE = "6201-5/01";

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Nested
    @DisplayName("normalizacao no construtor")
    class Normalizacao {

        @Test
        @DisplayName("remove espacos das pontas dos tres campos")
        void removeEspacos() {
            var request = new CadastroSecundarioRequest("  Tech Porto \t", " 12345678000195 ", " 6201-5/01 ");

            assertThat(request.nomeFantasia()).isEqualTo("Tech Porto");
            assertThat(request.documento()).isEqualTo("12345678000195");
            assertThat(request.codigoCnae()).isEqualTo("6201-5/01");
        }

        @Test
        @DisplayName("mantem espacos internos do nome")
        void mantemEspacosInternos() {
            assertThat(new CadastroSecundarioRequest("Tech  Porto", CNPJ, CNAE).nomeFantasia()).isEqualTo("Tech  Porto");
        }

        @Test
        @DisplayName("coloca o CNPJ alfanumerico em maiusculas")
        void maiusculas() {
            assertThat(new CadastroSecundarioRequest(NOME, "12abc34501de35", CNAE).documento()).isEqualTo("12ABC34501DE35");
        }

        @Test
        @DisplayName("nulos continuam nulos")
        void nulos() {
            var request = new CadastroSecundarioRequest(null, null, null);

            assertThat(request.nomeFantasia()).isNull();
            assertThat(request.documento()).isNull();
            assertThat(request.codigoCnae()).isNull();
        }
    }

    @Nested
    @DisplayName("cenarios positivos")
    class Positivo {

        @Test
        @DisplayName("request valido nao gera violacoes")
        void valido() {
            assertThat(validar(new CadastroSecundarioRequest(NOME, CNPJ, CNAE))).isEmpty();
        }

        @ParameterizedTest(name = "documento [{0}]")
        @ValueSource(strings = {"12345678000195", "12345678000199", "12ABC34501DE35", "12abc34501de35", " 12ABC34501DE35 "})
        @DisplayName("CNPJ numerico ou alfanumerico (minusculo e com espacos e normalizado)")
        void cnpjValido(String documento) {
            assertThat(validar(new CadastroSecundarioRequest(NOME, documento, CNAE))).isEmpty();
        }

        @Test
        @DisplayName("nome com exatamente 120 caracteres (limite)")
        void nomeNoLimite() {
            assertThat(validar(new CadastroSecundarioRequest("a".repeat(120), CNPJ, CNAE))).isEmpty();
        }

        @Test
        @DisplayName("nome com 120 caracteres + espacos nas pontas e aceito (limite conta apos strip)")
        void nomeNoLimiteComEspacos() {
            assertThat(validar(new CadastroSecundarioRequest("  " + "a".repeat(120) + "  ", CNPJ, CNAE))).isEmpty();
        }
    }

    @Nested
    @DisplayName("cenarios negativos")
    class Negativo {

        @Test
        @DisplayName("nomeFantasia nulo: CADSEC_VAL_0001")
        void nomeNulo() {
            assertThat(validar(new CadastroSecundarioRequest(null, CNPJ, CNAE)))
                    .containsExactly(entry("nomeFantasia", "CADSEC_VAL_0001"));
        }

        @ParameterizedTest(name = "nome [{0}]")
        @ValueSource(strings = {"", " ", "   ", "\t\n"})
        @DisplayName("nomeFantasia vazio ou so espacos: CADSEC_VAL_0001")
        void nomeVazio(String nome) {
            assertThat(validar(new CadastroSecundarioRequest(nome, CNPJ, CNAE)))
                    .containsExactly(entry("nomeFantasia", "CADSEC_VAL_0001"));
        }

        @Test
        @DisplayName("nomeFantasia com 121 caracteres: CADSEC_VAL_0002")
        void nomeLongo() {
            assertThat(validar(new CadastroSecundarioRequest("a".repeat(121), CNPJ, CNAE)))
                    .containsExactly(entry("nomeFantasia", "CADSEC_VAL_0002"));
        }

        @Test
        @DisplayName("documento nulo: CADSEC_VAL_0003")
        void documentoNulo() {
            assertThat(validar(new CadastroSecundarioRequest(NOME, null, CNAE)))
                    .containsExactly(entry("documento", "CADSEC_VAL_0003"));
        }

        @ParameterizedTest(name = "documento [{0}]")
        @ValueSource(strings = {"", "   "})
        @DisplayName("documento vazio: CADSEC_VAL_0003 (uma unica violacao)")
        void documentoVazio(String documento) {
            assertThat(validar(new CadastroSecundarioRequest(NOME, documento, CNAE)))
                    .containsExactly(entry("documento", "CADSEC_VAL_0003"));
        }

        @ParameterizedTest(name = "documento [{0}]")
        @ValueSource(strings = {"12345678909", "123.456.789-09", " 12345678909 "})
        @DisplayName("documento CPF: CADSEC_VAL_0005")
        void documentoCpf(String documento) {
            assertThat(validar(new CadastroSecundarioRequest(NOME, documento, CNAE)))
                    .containsExactly(entry("documento", "CADSEC_VAL_0005"));
        }

        @ParameterizedTest(name = "documento [{0}]")
        @ValueSource(strings = {"12.345.678/0001-95", "1234567800019", "123456780001951", "abc", "12ABC34501DEAB"})
        @DisplayName("documento fora do formato: CADSEC_VAL_0004")
        void documentoFormatoInvalido(String documento) {
            assertThat(validar(new CadastroSecundarioRequest(NOME, documento, CNAE)))
                    .containsExactly(entry("documento", "CADSEC_VAL_0004"));
        }

        @Test
        @DisplayName("codigoCnae nulo: CNAE_VAL_0004 (mesma regra do BuscadorCnae)")
        void cnaeNulo() {
            assertThat(validar(new CadastroSecundarioRequest(NOME, CNPJ, null)))
                    .containsExactly(entry("codigoCnae", "CNAE_VAL_0004"));
        }

        @Test
        @DisplayName("codigoCnae vazio: CNAE_VAL_0005")
        void cnaeVazio() {
            assertThat(validar(new CadastroSecundarioRequest(NOME, CNPJ, "  ")))
                    .containsExactly(entry("codigoCnae", "CNAE_VAL_0005"));
        }

        @ParameterizedTest(name = "codigo [{0}]")
        @ValueSource(strings = {"6201501", "6201-5/1", "abc", "%"})
        @DisplayName("codigoCnae malformado: CNAE_VAL_0006")
        void cnaeMalformado(String codigo) {
            assertThat(validar(new CadastroSecundarioRequest(NOME, CNPJ, codigo)))
                    .containsExactly(entry("codigoCnae", "CNAE_VAL_0006"));
        }

        @Test
        @DisplayName("todos os campos invalidos: uma violacao por campo")
        void todosInvalidos() {
            assertThat(validar(new CadastroSecundarioRequest(" ", "123.456.789-09", "6201501")))
                    .containsOnly(
                            entry("nomeFantasia", "CADSEC_VAL_0001"),
                            entry("documento", "CADSEC_VAL_0005"),
                            entry("codigoCnae", "CNAE_VAL_0006"));
        }

        @Test
        @DisplayName("corpo vazio ({}): os tres campos obrigatorios sao reportados")
        void corpoVazio() {
            assertThat(validar(new CadastroSecundarioRequest(null, null, null)))
                    .containsOnly(
                            entry("nomeFantasia", "CADSEC_VAL_0001"),
                            entry("documento", "CADSEC_VAL_0003"),
                            entry("codigoCnae", "CNAE_VAL_0004"));
        }
    }

    /** campo -> mensagem (nome do ErrorCode). Falha se um campo tiver mais de uma violacao. */
    private static Map<String, String> validar(CadastroSecundarioRequest request) {
        return validator.validate(request).stream()
                .collect(Collectors.toMap(v -> v.getPropertyPath().toString(), ConstraintViolation::getMessage));
    }
}
