package com.porto.testecnae;

import com.porto.testecnae.repository.CadastroSecundarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Item 6 de ponta a ponta: HTTP -> @Valid -> service -> H2. {@code @Transactional} desfaz os cadastros de cada teste.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class CadastroSecundarioValidacaoIntegrationTest {

    private static final String URL = "/api/cadastros-secundarios";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CadastroSecundarioRepository repository;

    @Nested
    @DisplayName("cenarios positivos")
    class Positivo {

        @Test
        @DisplayName("CNPJ numerico: 201")
        void cnpjNumerico() throws Exception {
            postar("Tech Porto", "12345678000195", "6201-5/01")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.documento").value("12345678000195"));
        }

        @Test
        @DisplayName("CNPJ alfanumerico em minusculas: 201 e gravado em maiusculas")
        void cnpjAlfanumerico() throws Exception {
            postar("Tech Porto", "12abc34501de35", "6201-5/01")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.documento").value("12ABC34501DE35"));

            assertThat(repository.existsByDocumento("12ABC34501DE35")).isTrue();
        }

        @Test
        @DisplayName("exemplo do README (DV incorreto) continua aceito: DV nao e validado")
        void exemploDoReadme() throws Exception {
            postar("Tech Porto", "12345678000199", "6201-5/01").andExpect(status().isCreated());
        }

        @Test
        @DisplayName("espacos nas pontas sao removidos antes de gravar")
        void gravaSemEspacos() throws Exception {
            postar("  Tech Porto  ", " 12345678000195 ", " 6201-5/01 ")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.nomeFantasia").value("Tech Porto"))
                    .andExpect(jsonPath("$.documento").value("12345678000195"))
                    .andExpect(jsonPath("$.cnae.codigo").value("6201-5/01"));
        }

        @Test
        @DisplayName("nome com 120 caracteres e gravado (antes do item 6, 121 dava 500)")
        void nomeNoLimite() throws Exception {
            postar("a".repeat(120), "12345678000195", "6201-5/01").andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("campos invalidos: 400 e nada e gravado")
    class CamposInvalidos {

        @ParameterizedTest(name = "{3}")
        @CsvSource(delimiter = '|', nullValues = "NULL", textBlock = """
                NULL           | 12345678000195     | 6201-5/01 | CADSEC-VAL-0001 (antes: 500)
                '   '          | 12345678000195     | 6201-5/01 | CADSEC-VAL-0001 (antes: 201 em branco)
                Tech Porto     | NULL               | 6201-5/01 | CADSEC-VAL-0003 (antes: 500)
                Tech Porto     | abc                | 6201-5/01 | CADSEC-VAL-0004 (antes: 201)
                Tech Porto     | 12.345.678/0001-95 | 6201-5/01 | CADSEC-VAL-0004
                Tech Porto     | 123456780001951    | 6201-5/01 | CADSEC-VAL-0004 (antes: 500)
                Tech Porto     | 12345678909        | 6201-5/01 | CADSEC-VAL-0005 (antes: 201)
                Tech Porto     | 123.456.789-09     | 6201-5/01 | CADSEC-VAL-0005
                Tech Porto     | 12345678000195     | NULL      | CNAE-VAL-0004
                Tech Porto     | 12345678000195     | 6201501   | CNAE-VAL-0006
                """)
        void campoInvalido(String nome, String documento, String codigoCnae, String esperado) throws Exception {
            var antes = repository.count();

            postar(nome, documento, codigoCnae)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(esperado.split(" ")[0]))
                    .andExpect(jsonPath("$.errors.length()").value(1));

            assertThat(repository.count()).isEqualTo(antes);
        }

        @Test
        @DisplayName("nome com 121 caracteres: CADSEC-VAL-0002 (antes: 500)")
        void nomeLongo() throws Exception {
            postar("a".repeat(121), "12345678000195", "6201-5/01")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CADSEC-VAL-0002"));
        }

        @Test
        @DisplayName("todos invalidos: errors com 3 itens em ordem")
        void todosInvalidos() throws Exception {
            postar(" ", "12345678909", "abc")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CADSEC-VAL-0001"))
                    .andExpect(jsonPath("$.errors[0].code").value("CADSEC-VAL-0001"))
                    .andExpect(jsonPath("$.errors[1].code").value("CADSEC-VAL-0005"))
                    .andExpect(jsonPath("$.errors[2].code").value("CNAE-VAL-0006"));
        }

        @Test
        @DisplayName("campo invalido + CNAE inexistente: 400 prevalece (CNAE nem e consultado)")
        void validacaoAntesDoCnae() throws Exception {
            postar(" ", "12345678000195", "9999-9/99")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CADSEC-VAL-0001"));
        }

        @Test
        @DisplayName("JSON malformado: GEN-VAL-0005")
        void jsonMalformado() throws Exception {
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"nomeFantasia\":"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("GEN-VAL-0005"));
        }
    }

    @Nested
    @DisplayName("CNPJ duplicado")
    class Duplicado {

        @Test
        @DisplayName("segundo cadastro com o mesmo CNPJ: 409 CADSEC-NEG-0002 e so um e gravado (antes: 201 duas vezes)")
        void mesmoCnpj() throws Exception {
            var antes = repository.count();
            postar("Empresa A", "12345678000195", "6201-5/01").andExpect(status().isCreated());

            postar("Empresa B", "12345678000195", "6622-3/00")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CADSEC-NEG-0002"))
                    .andExpect(jsonPath("$.errors").doesNotExist());

            assertThat(repository.count()).isEqualTo(antes + 1);
        }

        @Test
        @DisplayName("duplicidade considera o CNPJ normalizado (espacos e minusculas)")
        void duplicidadeNormalizada() throws Exception {
            postar("Empresa A", "12ABC34501DE35", "6201-5/01").andExpect(status().isCreated());

            postar("Empresa B", " 12abc34501de35 ", "6201-5/01")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CADSEC-NEG-0002"));
        }

        @Test
        @DisplayName("CNPJ duplicado + CNAE inexistente: 422 prevalece (CNAE e verificado antes)")
        void cnaeAntesDaDuplicidade() throws Exception {
            postar("Empresa A", "12345678000195", "6201-5/01").andExpect(status().isCreated());

            postar("Empresa B", "12345678000195", "9999-9/99")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("CADSEC-NEG-0001"));
        }

        @Test
        @DisplayName("CNPJs diferentes: ambos gravados")
        void cnpjsDiferentes() throws Exception {
            postar("Empresa A", "12345678000195", "6201-5/01").andExpect(status().isCreated());
            postar("Empresa B", "12ABC34501DE35", "6201-5/01").andExpect(status().isCreated());
        }
    }

    @Test
    @DisplayName("log registra os codigos de erro, nunca os valores enviados")
    void logSemValores(CapturedOutput output) throws Exception {
        postar("Nome Secreto", "98765432109", "6201-5/01").andExpect(status().isBadRequest());
        postar("Empresa A", "11222333000181", "6201-5/01").andExpect(status().isCreated());
        postar("Empresa B", "11222333000181", "6201-5/01").andExpect(status().isConflict());

        assertThat(output)
                .contains("errorCode=CADSEC-VAL-0005 status=400 path=/api/cadastros-secundarios")
                .contains("errors=CADSEC-VAL-0005")
                .contains("errorCode=CADSEC-NEG-0002 status=409")
                .doesNotContain("98765432109")
                .doesNotContain("11222333000181")
                .doesNotContain("Nome Secreto");
    }

    private ResultActions postar(String nome, String documento, String codigoCnae) throws Exception {
        return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json(nome, documento, codigoCnae)));
    }

    private static String json(String nome, String documento, String codigoCnae) {
        return "{%s,%s,%s}".formatted(campo("nomeFantasia", nome), campo("documento", documento), campo("codigoCnae", codigoCnae));
    }

    private static String campo(String nome, String valor) {
        return valor == null ? "\"%s\":null".formatted(nome) : "\"%s\":\"%s\"".formatted(nome, valor);
    }
}
