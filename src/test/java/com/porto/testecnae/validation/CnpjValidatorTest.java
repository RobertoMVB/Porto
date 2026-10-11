package com.porto.testecnae.validation;

import com.porto.testecnae.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regras do documento: normalizacao (mascara, espacos, maiusculas), classificacao e digito verificador.
 * {@code classificar} recebe o valor ja normalizado, como acontece no {@code CadastroSecundarioRequest}.
 */
class CnpjValidatorTest {

    @Nested
    @DisplayName("normalizar: aceita com e sem mascara")
    class Normalizar {

        @ParameterizedTest(name = "[{0}] -> {1}")
        @CsvSource(delimiter = '|', textBlock = """
                12345678000195       | 12345678000195
                12.345.678/0001-95   | 12345678000195
                12345678/0001-95     | 12345678000195
                12.345.678000195     | 12345678000195
                12 345 678 0001 95   | 12345678000195
                12-345-678-0001-95   | 12345678000195
                12.ABC.345/01DE-35   | 12ABC34501DE35
                12.abc.345/01de-35   | 12ABC34501DE35
                123.456.789-09       | 12345678909
                """)
        void removeMascara(String entrada, String esperado) {
            assertThat(CnpjValidator.normalizar(entrada)).isEqualTo(esperado);
        }

        @Test
        @DisplayName("NBSP e espacos nas pontas sao removidos")
        void espacosUnicode() {
            assertThat(CnpjValidator.normalizar(" 12.345.678/0001-95 ")).isEqualTo("12345678000195");
            assertThat(CnpjValidator.normalizar("\t12345678000195\n")).isEqualTo("12345678000195");
        }

        @Test
        @DisplayName("nulo continua nulo; so espacos vira vazio")
        void nuloEVazio() {
            assertThat(CnpjValidator.normalizar(null)).isNull();
            assertThat(CnpjValidator.normalizar("   ")).isEmpty();
        }

        @Test
        @DisplayName("outros caracteres nao sao removidos (caem em formato invalido)")
        void outrosCaracteresMantidos() {
            assertThat(CnpjValidator.normalizar("12,345,678|0001_95")).isEqualTo("12,345,678|0001_95");
        }
    }

    @Nested
    @DisplayName("CNPJ valido")
    class Valido {

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {
                "12345678000195",   // exemplo do README (corrigido)
                "11222333000181",
                "11444777000161",
                "98765432000198",
                "12ABC34501DE35",   // alfanumerico: exemplo oficial da Receita
                "ABCDEFGHIJKL80",   // 12 letras
                "1A2B3C4D5E6F34"
        })
        void aceito(String documento) {
            assertThat(CnpjValidator.classificar(documento)).isNull();
        }
    }

    @Nested
    @DisplayName("digitosVerificadores: regra oficial (ASCII - 48, modulo 11)")
    class DigitosVerificadores {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
                "123456780001, 95",
                "112223330001, 81",
                "12ABC34501DE, 35",
                "ABCDEFGHIJKL, 80",
                "987654320001, 98"
        })
        void calcula(String base, String esperado) {
            assertThat(CnpjValidator.digitosVerificadores(base)).isEqualTo(esperado);
        }

        @Test
        @DisplayName("resto menor que 2 gera digito 0")
        void restoMenorQueDois() {
            // base 000000070001: soma do 1o digito = 7*6 + 1*2 = 44, resto 0 -> digito 0
            assertThat(CnpjValidator.digitosVerificadores("000000070001")).isEqualTo("03");
            assertThat(CnpjValidator.classificar("00000007000103")).isNull();
        }

        @Test
        @DisplayName("letra vale ASCII - 48 (A = 17): trocar 0 por A muda os digitos")
        void letraTemValorProprio() {
            assertThat(CnpjValidator.digitosVerificadores("A23456780001"))
                    .isNotEqualTo(CnpjValidator.digitosVerificadores("023456780001"));
        }
    }

    @Nested
    @DisplayName("CADSEC-VAL-0006: digito verificador invalido")
    class DigitoInvalido {

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {
                "12345678000199",   // exemplo antigo do README (correto: ...95)
                "12345678000105",   // 1o digito errado
                "12345678000194",   // 2o digito errado
                "12345678000159",   // digitos invertidos
                "12ABC34501DE36",   // alfanumerico com 2o digito errado
                "12ABC34501DE53",
                "ABCDEFGHIJKL00",
                "12345678000295"    // base alterada (filial), DV antigo
        })
        void rejeitado(String documento) {
            assertThat(CnpjValidator.classificar(documento)).isEqualTo(ErrorCode.CADSEC_VAL_0006);
        }
    }

    @Nested
    @DisplayName("CADSEC-VAL-0007: sequencia repetida")
    class SequenciaRepetida {

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {
                "00000000000000", "11111111111111", "22222222222222", "33333333333333", "44444444444444",
                "55555555555555", "66666666666666", "77777777777777", "88888888888888", "99999999999999"
        })
        @DisplayName("sequencias numericas repetidas (passam no calculo do DV, mas nao sao CNPJs reais)")
        void rejeitada(String documento) {
            assertThat(CnpjValidator.classificar(documento)).isEqualTo(ErrorCode.CADSEC_VAL_0007);
        }

        @Test
        @DisplayName("00000000000000 tem DV 'valido' pelo calculo: so a regra de sequencia o barra")
        void zerosPassamNoCalculo() {
            assertThat(CnpjValidator.digitosVerificadores("000000000000")).isEqualTo("00");
        }
    }

    @Nested
    @DisplayName("CADSEC-VAL-0003: documento nao informado")
    class NaoInformado {

        @Test
        void nulo() {
            assertThat(CnpjValidator.classificar(null)).isEqualTo(ErrorCode.CADSEC_VAL_0003);
        }

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {"", " ", "\t"})
        void vazio(String documento) {
            assertThat(CnpjValidator.classificar(documento)).isEqualTo(ErrorCode.CADSEC_VAL_0003);
        }
    }

    @Nested
    @DisplayName("CADSEC-VAL-0005: documento e um CPF")
    class Cpf {

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {"12345678909", "00000000000", "123.456.789-09"})
        void identificaCpf(String documento) {
            assertThat(CnpjValidator.classificar(documento)).isEqualTo(ErrorCode.CADSEC_VAL_0005);
        }

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {"1234567890", "123456789012", "1234567890A"})
        @DisplayName("parecido com CPF, mas fora do padrao: e formato invalido, nao CPF")
        void quaseCpfEhFormatoInvalido(String documento) {
            assertThat(CnpjValidator.classificar(documento)).isEqualTo(ErrorCode.CADSEC_VAL_0004);
        }
    }

    @Nested
    @DisplayName("CADSEC-VAL-0004: formato invalido (apos normalizar)")
    class FormatoInvalido {

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {
                "1234567800019",        // 13 caracteres
                "123456780001951",      // 15 caracteres
                "12ABC34501DEAB",       // letras nos digitos verificadores
                "12abc34501de35",       // minusculas (normalizar converte antes)
                "1234567800019%",       // curinga
                "12,345,678|0001_95",   // pontuacao que nao e de mascara
                "abc"
        })
        void rejeitado(String documento) {
            assertThat(CnpjValidator.classificar(documento)).isEqualTo(ErrorCode.CADSEC_VAL_0004);
        }

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {"12.345.678/0001-9", "12.345.678/0001-955", "12.345.678/0001"})
        @DisplayName("mascara com digitos faltando ou sobrando: formato invalido apos remover a pontuacao")
        void mascaraIncompleta(String documento) {
            assertThat(CnpjValidator.classificar(CnpjValidator.normalizar(documento))).isEqualTo(ErrorCode.CADSEC_VAL_0004);
        }
    }
}
