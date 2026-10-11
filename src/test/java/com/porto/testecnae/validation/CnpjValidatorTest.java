package com.porto.testecnae.validation;

import com.porto.testecnae.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regras do documento. O valor chega ja normalizado pelo {@code CadastroSecundarioRequest} (strip + maiusculas).
 */
class CnpjValidatorTest {

    @Nested
    @DisplayName("CNPJ valido (somente formato, sem digito verificador)")
    class Valido {

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {
                "12345678000195",   // numerico com DV correto
                "12345678000199",   // numerico com DV incorreto (exemplo do README): aceito, DV nao e validado
                "00000000000000",   // formato valido
                "12ABC34501DE35",   // alfanumerico (exemplo da Receita)
                "ABCDEFGHIJKL00",   // 12 letras + 2 digitos
                "1A2B3C4D5E6F78"
        })
        void aceito(String documento) {
            assertThat(CnpjValidator.classificar(documento)).isNull();
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
        @ValueSource(strings = {"1234567890", "123456789012", "123.456.78909", "1234567890A"})
        @DisplayName("parecido com CPF, mas fora do padrao: e formato invalido, nao CPF")
        void quaseCpfEhFormatoInvalido(String documento) {
            assertThat(CnpjValidator.classificar(documento)).isEqualTo(ErrorCode.CADSEC_VAL_0004);
        }
    }

    @Nested
    @DisplayName("CADSEC-VAL-0004: formato invalido")
    class FormatoInvalido {

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {
                "12.345.678/0001-95",   // com mascara (rejeitado, nao e removida)
                "12.ABC.345/01DE-35",   // alfanumerico com mascara
                "1234567800019",        // 13 caracteres
                "123456780001951",      // 15 caracteres
                "12ABC34501DEAB",       // letras nos digitos verificadores
                "12abc34501de35",       // minusculas (a normalizacao do request converte antes)
                "12345678 000195",      // espaco interno
                "1234567800019%",       // curinga
                "12345678-000195",
                "abc"
        })
        void rejeitado(String documento) {
            assertThat(CnpjValidator.classificar(documento)).isEqualTo(ErrorCode.CADSEC_VAL_0004);
        }
    }
}
