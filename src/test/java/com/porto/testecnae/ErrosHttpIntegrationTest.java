package com.porto.testecnae;

import com.porto.testecnae.repository.CadastroSecundarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Item 8: erros HTTP genericos (404, 405, 406, 415) em todos os endpoints. Antes saiam sem {@code code} e sem log.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ErrosHttpIntegrationTest {

    private static final String CADASTRO_VALIDO =
            "{\"nomeFantasia\":\"Tech Porto\",\"documento\":\"12345678000195\",\"codigoCnae\":\"6201-5/01\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CadastroSecundarioRepository cadastroRepository;

    @Nested
    @DisplayName("404 GEN-NEG-0001: rota inexistente")
    class RotaInexistente {

        @ParameterizedTest(name = "GET {0}")
        @ValueSource(strings = {"/api/naoexiste", "/api/cnaes/codigo/6201-5", "/api/cnaes/x/y", "/api", "/", "/api/cadastro-secundario"})
        void rotaInexistente(String url) throws Exception {
            mockMvc.perform(get(url))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("GEN-NEG-0001"))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.instance").value(url));
        }

        @Test
        @DisplayName("mensagem tecnica do Spring nao vai para o cliente")
        void semMensagemTecnica() throws Exception {
            mockMvc.perform(get("/api/naoexiste"))
                    .andExpect(jsonPath("$.detail").value("Recurso nao encontrado. Verifique o endereco da requisicao."))
                    .andExpect(content().string(not(containsString("static resource"))));
        }
    }

    @Nested
    @DisplayName("405 GEN-VAL-0006: metodo nao suportado (todos os endpoints)")
    class MetodoNaoSuportado {

        @ParameterizedTest(name = "{0} {1}")
        @CsvSource({
                "POST,   /api/cnaes",
                "PUT,    /api/cnaes",
                "DELETE, /api/cnaes",
                "PATCH,  /api/cnaes",
                "POST,   /api/cnaes/paginado",
                "DELETE, /api/cnaes/paginado",
                "POST,   /api/cnaes/buscar",
                "PUT,    /api/cnaes/buscar",
                "POST,   /api/cnaes/codigo",
                "DELETE, /api/cnaes/codigo",
                "PUT,    /api/cadastros-secundarios",
                "DELETE, /api/cadastros-secundarios",
                "PATCH,  /api/cadastros-secundarios",
                "POST,   /api/cadastros-secundarios/validar-cnae",
                "DELETE, /api/cadastros-secundarios/validar-cnae"
        })
        void metodoNaoSuportado(String metodo, String url) throws Exception {
            mockMvc.perform(request(HttpMethod.valueOf(metodo), url))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("GEN-VAL-0006"))
                    .andExpect(jsonPath("$.status").value(405))
                    .andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")));
        }

        @Test
        @DisplayName("informa os metodos aceitos no detail e no header Allow")
        void metodosAceitos() throws Exception {
            mockMvc.perform(request(HttpMethod.DELETE, "/api/cadastros-secundarios"))
                    .andExpect(header().string(HttpHeaders.ALLOW, containsString("POST")))
                    .andExpect(jsonPath("$.detail").value(
                            "Metodo HTTP nao suportado neste endpoint. Metodos aceitos: GET, POST."));
        }
    }

    @Nested
    @DisplayName("415 GEN-VAL-0007: Content-Type nao suportado")
    class ContentTypeNaoSuportado {

        @ParameterizedTest(name = "Content-Type {0}")
        @ValueSource(strings = {"text/plain", "application/xml", "application/x-www-form-urlencoded", "application/octet-stream"})
        void contentTypeNaoSuportado(String contentType) throws Exception {
            mockMvc.perform(post("/api/cadastros-secundarios").contentType(contentType).content(CADASTRO_VALIDO))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("GEN-VAL-0007"))
                    .andExpect(jsonPath("$.status").value(415));
        }
    }

    @Nested
    @DisplayName("406 GEN-VAL-0008: formato de resposta nao disponivel (todos os endpoints)")
    class FormatoNaoAceito {

        @ParameterizedTest(name = "GET {0}")
        @ValueSource(strings = {
                "/api/cnaes",
                "/api/cnaes/paginado",
                "/api/cnaes/buscar?termo=programas",
                "/api/cnaes/codigo?codigo=6201-5/01",
                "/api/cadastros-secundarios",
                "/api/cadastros-secundarios/validar-cnae?codigoCnae=6201-5/01"
        })
        void acceptXml(String url) throws Exception {
            mockMvc.perform(get(url).accept(MediaType.APPLICATION_XML))
                    .andExpect(status().isNotAcceptable())
                    .andExpect(jsonPath("$.code").value("GEN-VAL-0008"))
                    .andExpect(jsonPath("$.status").value(406));
        }

        @Test
        @DisplayName("POST com Accept: application/xml: 406 e NADA e gravado (antes: gravava e devolvia 406)")
        void postAcceptXmlNaoGrava() throws Exception {
            var antes = cadastroRepository.count();

            mockMvc.perform(post("/api/cadastros-secundarios").contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_XML).content(CADASTRO_VALIDO))
                    .andExpect(status().isNotAcceptable())
                    .andExpect(jsonPath("$.code").value("GEN-VAL-0008"));

            assertThat(cadastroRepository.count()).isEqualTo(antes);
        }

        @ParameterizedTest(name = "Accept {0}")
        @ValueSource(strings = {"application/json", "*/*", "application/*", "application/json, application/xml;q=0.5"})
        @DisplayName("Accept compativel com JSON continua funcionando")
        void acceptCompativel(String accept) throws Exception {
            mockMvc.perform(get("/api/cnaes").header(HttpHeaders.ACCEPT, accept))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("sem header Accept continua funcionando")
        void semAccept() throws Exception {
            mockMvc.perform(get("/api/cnaes")).andExpect(status().isOk());
        }
    }
}
