package com.porto.testecnae;

import com.porto.testecnae.repository.CadastroSecundarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo completo dos itens 3, 4 e 5: HTTP -> controller -> service -> BuscadorCnae -> H2 (data.sql).
 * {@code @Transactional} desfaz os cadastros criados ao fim de cada teste.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class CnaeInexistenteIntegrationTest {

    private static final String URL_CODIGO = "/api/cnaes/codigo";
    private static final String URL_VALIDAR = "/api/cadastros-secundarios/validar-cnae";
    private static final String URL_CADASTRO = "/api/cadastros-secundarios";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CadastroSecundarioRepository cadastroRepository;

    @Nested
    @DisplayName("item 3 - GET /api/cnaes/codigo")
    class Codigo {

        @ParameterizedTest(name = "codigo {0}")
        @ValueSource(strings = {"0111-3/01", "0112-1/01", "1011-2/01", "4711-3/02",
                "6201-5/01", "6202-3/00", "6622-3/00", "7311-4/00"})
        @DisplayName("todos os CNAEs do data.sql sao encontrados")
        void todosEncontrados(String codigo) throws Exception {
            mockMvc.perform(get(URL_CODIGO).param("codigo", codigo))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.codigo").value(codigo));
        }

        @Test
        @DisplayName("espacos nas pontas sao removidos")
        void espacosRemovidos() throws Exception {
            mockMvc.perform(get(URL_CODIGO).param("codigo", "  6201-5/01 "))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.codigo").value("6201-5/01"));
        }

        @Test
        @DisplayName("barra codificada na URL (%2F) e aceita")
        void barraCodificada() throws Exception {
            mockMvc.perform(get(URI.create(URL_CODIGO + "?codigo=6201-5%2F01")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.codigo").value("6201-5/01"));
        }

        @Test
        @DisplayName("codigo inexistente: 404 CNAE-NEG-0001 (antes: 200 'Cultivo de arroz')")
        void inexistente() throws Exception {
            mockMvc.perform(get(URL_CODIGO).param("codigo", "9999-9/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CNAE-NEG-0001"))
                    .andExpect(jsonPath("$.detail").value("CNAE '9999-9/99' nao encontrado."));
        }

        @ParameterizedTest(name = "codigo [{0}]")
        @ValueSource(strings = {"6201501", "6201-5/1", "abc", "%"})
        @DisplayName("formato invalido: 400 CNAE-VAL-0006")
        void formatoInvalido(String codigo) throws Exception {
            mockMvc.perform(get(URL_CODIGO).param("codigo", codigo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CNAE-VAL-0006"));
        }

        @Test
        @DisplayName("codigo vazio: 400 CNAE-VAL-0005")
        void vazio() throws Exception {
            mockMvc.perform(get(URL_CODIGO).param("codigo", " "))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CNAE-VAL-0005"));
        }
    }

    @Nested
    @DisplayName("item 4 - GET /api/cadastros-secundarios/validar-cnae")
    class Validar {

        @ParameterizedTest(name = "codigo {0}")
        @ValueSource(strings = {"0111-3/01", "6201-5/01", "7311-4/00", " 6622-3/00 "})
        @DisplayName("CNAE existente: 200")
        void existente(String codigo) throws Exception {
            mockMvc.perform(get(URL_VALIDAR).param("codigoCnae", codigo))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.codigo").value(codigo.strip()));
        }

        @Test
        @DisplayName("CNAE inexistente: 404 CNAE-NEG-0001 (antes: 200 'Cultivo de arroz')")
        void inexistente() throws Exception {
            mockMvc.perform(get(URL_VALIDAR).param("codigoCnae", "9999-9/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CNAE-NEG-0001"));
        }

        @Test
        @DisplayName("formato invalido: 400 CNAE-VAL-0006")
        void formatoInvalido() throws Exception {
            mockMvc.perform(get(URL_VALIDAR).param("codigoCnae", "6201501"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CNAE-VAL-0006"));
        }
    }

    @Nested
    @DisplayName("item 5 - POST /api/cadastros-secundarios")
    class Cadastro {

        @Test
        @DisplayName("CNAE existente: 201 e o cadastro aparece na listagem")
        void cnaeExistente() throws Exception {
            var antes = cadastroRepository.count();

            mockMvc.perform(post(URL_CADASTRO).contentType(MediaType.APPLICATION_JSON)
                            .content(body("Tech Porto", "12345678000199", "6201-5/01")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.cnae.codigo").value("6201-5/01"));

            assertThat(cadastroRepository.count()).isEqualTo(antes + 1);
            mockMvc.perform(get(URL_CADASTRO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.nomeFantasia == 'Tech Porto')].cnae.codigo").value("6201-5/01"));
        }

        @Test
        @DisplayName("CNAE inexistente: 422 CADSEC-NEG-0001 e nada e gravado (antes: 201 ligado ao 1o CNAE)")
        void cnaeInexistente() throws Exception {
            var antes = cadastroRepository.count();

            mockMvc.perform(post(URL_CADASTRO).contentType(MediaType.APPLICATION_JSON)
                            .content(body("Fantasma", "00000000000000", "9999-9/99")))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("CADSEC-NEG-0001"))
                    .andExpect(jsonPath("$.detail").value("Cadastro rejeitado: o CNAE '9999-9/99' nao existe."));

            assertThat(cadastroRepository.count()).isEqualTo(antes);
        }

        @Test
        @DisplayName("codigoCnae ausente: 400 CNAE-VAL-0004 e nada e gravado")
        void codigoAusente() throws Exception {
            var antes = cadastroRepository.count();

            mockMvc.perform(post(URL_CADASTRO).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nomeFantasia\":\"Tech Porto\",\"documento\":\"12345678000199\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CNAE-VAL-0004"));

            assertThat(cadastroRepository.count()).isEqualTo(antes);
        }

        @ParameterizedTest(name = "codigo [{0}]")
        @ValueSource(strings = {"", "6201501"})
        @DisplayName("codigoCnae vazio ou malformado: 400 e nada e gravado")
        void codigoInvalido(String codigo) throws Exception {
            var antes = cadastroRepository.count();

            mockMvc.perform(post(URL_CADASTRO).contentType(MediaType.APPLICATION_JSON)
                            .content(body("Tech Porto", "12345678000199", codigo)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(codigo.isEmpty() ? "CNAE-VAL-0005" : "CNAE-VAL-0006"));

            assertThat(cadastroRepository.count()).isEqualTo(antes);
        }

        @Test
        @DisplayName("item 6: CNAE valido sem nomeFantasia agora e 400 CADSEC-VAL-0001 (antes: 500)")
        void item6Corrigido() throws Exception {
            mockMvc.perform(post(URL_CADASTRO).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"documento\":\"12345678000199\",\"codigoCnae\":\"6201-5/01\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CADSEC-VAL-0001"));
        }
    }

    @Nested
    @DisplayName("log")
    class Log {

        @Test
        @DisplayName("registra o codigo de erro, mas nao o valor enviado pelo cliente")
        void naoRegistraValorDoCliente(CapturedOutput output) throws Exception {
            mockMvc.perform(get(URL_CODIGO).param("codigo", "<script>alert(1)</script>"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(post(URL_CADASTRO).contentType(MediaType.APPLICATION_JSON)
                            .content(body("Fantasma", "00000000000000", "9876-5/43")))
                    .andExpect(status().isUnprocessableContent());

            assertThat(output)
                    .contains("errorCode=CNAE-VAL-0006 status=400 path=/api/cnaes/codigo")
                    .contains("errorCode=CADSEC-NEG-0001 status=422 path=/api/cadastros-secundarios")
                    .doesNotContain("<script>")
                    .doesNotContain("9876-5/43");
        }
    }

    private static String body(String nomeFantasia, String documento, String codigoCnae) {
        return "{\"nomeFantasia\":\"%s\",\"documento\":\"%s\",\"codigoCnae\":\"%s\"}"
                .formatted(nomeFantasia, documento, codigoCnae);
    }
}
