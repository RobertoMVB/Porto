package com.porto.testecnae.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TextoNormalizadorTest {

    @Nested
    @DisplayName("cenarios positivos")
    class Positivo {

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {
                "Tech Porto",
                " Tech Porto ",
                "Tech  Porto",
                "Tech     Porto",
                "Tech\tPorto",
                "Tech\nPorto",
                "Tech\r\nPorto",
                "Tech Porto",          // NBSP
                "Tech Porto",          // FIGURE SPACE
                "Tech Porto",          // NARROW NO-BREAK SPACE
                "Tech Porto",          // EM SPACE
                "Tech　Porto",          // espaco ideografico
                "Tech Porto",          // separador de linha
                " Tech  \t Porto ",
                "​Tech Porto﻿",   // invisiveis nas pontas
                "Tech​ Porto"          // invisivel junto de espaco
        })
        @DisplayName("qualquer espaco (ASCII ou Unicode) vira um espaco comum, sem pontas")
        void normaliza(String entrada) {
            assertThat(TextoNormalizador.normalizarEspacos(entrada)).isEqualTo("Tech Porto");
        }

        @Test
        @DisplayName("invisivel no meio da palavra e removido, sem criar espaco")
        void invisivelNoMeio() {
            assertThat(TextoNormalizador.normalizarEspacos("Te​ch")).isEqualTo("Tech");
        }

        @Test
        @DisplayName("preserva acentos, maiusculas, minusculas e pontuacao")
        void preservaConteudo() {
            assertThat(TextoNormalizador.normalizarEspacos("  Comércio  VAREJISTA,  5%  ")).isEqualTo("Comércio VAREJISTA, 5%");
        }

        @Test
        @DisplayName("texto ja normalizado nao muda")
        void idempotente() {
            var normalizado = TextoNormalizador.normalizarEspacos(" a   b ");
            assertThat(TextoNormalizador.normalizarEspacos(normalizado)).isEqualTo(normalizado).isEqualTo("a b");
        }
    }

    @Nested
    @DisplayName("cenarios negativos / limites")
    class Negativo {

        @Test
        @DisplayName("nulo continua nulo")
        void nulo() {
            assertThat(TextoNormalizador.normalizarEspacos(null)).isNull();
        }

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {"", " ", "   ", "\t\n", " ", "  ", "  ", "​", "﻿​", "  \t​ "})
        @DisplayName("so espacos, NBSP ou invisiveis vira vazio (e o @NotBlank passa a barrar)")
        void soEspacosViraVazio(String entrada) {
            assertThat(TextoNormalizador.normalizarEspacos(entrada)).isEmpty();
        }
    }
}
