package com.porto.testecnae;

import com.jayway.jsonpath.JsonPath;
import com.porto.testecnae.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Contrato de erro (item 8): para <b>qualquer</b> requisicao invalida, em qualquer endpoint, a resposta deve:
 * <ul>
 *     <li>ser {@code application/problem+json};</li>
 *     <li>ter {@code code} no formato {@code DOMINIO-CATEGORIA-NNNN} e existente no catalogo;</li>
 *     <li>ter {@code status} igual ao status HTTP;</li>
 *     <li>ter o mesmo codigo registrado no log ({@code errorCode=...}).</li>
 * </ul>
 * Um erro novo que escape do mapeamento quebra este teste.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class ContratoErroIntegrationTest {

    private static final String CNPJ_EXISTENTE = "11222333000181";

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void cadastroExistente() throws Exception {
        mockMvc.perform(post("/api/cadastros-secundarios").contentType(MediaType.APPLICATION_JSON)
                .content(corpo("Existente", CNPJ_EXISTENTE, "6201-5/01")));
    }

    static Stream<Arguments> requisicoesInvalidas() {
        return Stream.of(
                // GET /api/cnaes
                arguments("cnaes: metodo nao suportado", request(HttpMethod.DELETE, "/api/cnaes"), 405, "GEN-VAL-0006"),
                arguments("cnaes: Accept XML", get("/api/cnaes").accept(MediaType.APPLICATION_XML), 406, "GEN-VAL-0008"),
                // GET /api/cnaes/paginado
                arguments("paginado: page negativa", get("/api/cnaes/paginado").param("page", "-1"), 400, "GEN-VAL-0003"),
                arguments("paginado: size 0", get("/api/cnaes/paginado").param("size", "0"), 400, "GEN-VAL-0004"),
                arguments("paginado: size 101", get("/api/cnaes/paginado").param("size", "101"), 400, "GEN-VAL-0004"),
                arguments("paginado: page nao numerica", get("/api/cnaes/paginado").param("page", "abc"), 400, "GEN-VAL-0002"),
                // GET /api/cnaes/buscar
                arguments("buscar: sem termo", get("/api/cnaes/buscar"), 400, "GEN-VAL-0001"),
                arguments("buscar: termo vazio", get("/api/cnaes/buscar").param("termo", " "), 400, "CNAE-VAL-0002"),
                arguments("buscar: termo longo", get("/api/cnaes/buscar").param("termo", "a".repeat(181)), 400, "CNAE-VAL-0003"),
                arguments("buscar: metodo nao suportado", post("/api/cnaes/buscar"), 405, "GEN-VAL-0006"),
                // GET /api/cnaes/codigo
                arguments("codigo: sem codigo", get("/api/cnaes/codigo"), 400, "GEN-VAL-0001"),
                arguments("codigo: vazio", get("/api/cnaes/codigo").param("codigo", ""), 400, "CNAE-VAL-0005"),
                arguments("codigo: formato invalido", get("/api/cnaes/codigo").param("codigo", "6201501"), 400, "CNAE-VAL-0006"),
                arguments("codigo: inexistente", get("/api/cnaes/codigo").param("codigo", "9999-9/99"), 404, "CNAE-NEG-0001"),
                // GET /api/cadastros-secundarios/validar-cnae
                arguments("validar: sem codigo", get("/api/cadastros-secundarios/validar-cnae"), 400, "GEN-VAL-0001"),
                arguments("validar: formato invalido", get("/api/cadastros-secundarios/validar-cnae").param("codigoCnae", "abc"), 400, "CNAE-VAL-0006"),
                arguments("validar: inexistente", get("/api/cadastros-secundarios/validar-cnae").param("codigoCnae", "9999-9/99"), 404, "CNAE-NEG-0001"),
                // GET /api/cadastros-secundarios
                arguments("cadastros: metodo nao suportado", request(HttpMethod.PUT, "/api/cadastros-secundarios"), 405, "GEN-VAL-0006"),
                arguments("cadastros: Accept XML", get("/api/cadastros-secundarios").accept(MediaType.APPLICATION_XML), 406, "GEN-VAL-0008"),
                // POST /api/cadastros-secundarios
                arguments("post: JSON malformado", postJson("{invalido"), 400, "GEN-VAL-0005"),
                arguments("post: sem corpo", post("/api/cadastros-secundarios").contentType(MediaType.APPLICATION_JSON), 400, "GEN-VAL-0005"),
                arguments("post: Content-Type texto", post("/api/cadastros-secundarios").contentType(MediaType.TEXT_PLAIN).content("x"), 415, "GEN-VAL-0007"),
                arguments("post: sem nome", postJson(corpo(null, "12345678000195", "6201-5/01")), 400, "CADSEC-VAL-0001"),
                arguments("post: nome longo", postJson(corpo("a".repeat(121), "12345678000195", "6201-5/01")), 400, "CADSEC-VAL-0002"),
                arguments("post: sem documento", postJson(corpo("X", null, "6201-5/01")), 400, "CADSEC-VAL-0003"),
                arguments("post: documento invalido", postJson(corpo("X", "abc", "6201-5/01")), 400, "CADSEC-VAL-0004"),
                arguments("post: documento CPF", postJson(corpo("X", "12345678909", "6201-5/01")), 400, "CADSEC-VAL-0005"),
                arguments("post: sem codigoCnae", postJson(corpo("X", "12345678000195", null)), 400, "CNAE-VAL-0004"),
                arguments("post: codigoCnae vazio", postJson(corpo("X", "12345678000195", " ")), 400, "CNAE-VAL-0005"),
                arguments("post: codigoCnae malformado", postJson(corpo("X", "12345678000195", "6201501")), 400, "CNAE-VAL-0006"),
                arguments("post: CNAE inexistente", postJson(corpo("X", "12345678000195", "9999-9/99")), 422, "CADSEC-NEG-0001"),
                arguments("post: CNPJ duplicado", postJson(corpo("Outra", CNPJ_EXISTENTE, "6201-5/01")), 409, "CADSEC-NEG-0002"),
                // rotas inexistentes
                arguments("rota inexistente", get("/api/naoexiste"), 404, "GEN-NEG-0001"),
                arguments("rota inexistente com metodo POST", post("/api/naoexiste"), 404, "GEN-NEG-0001"),
                arguments("query string com caracteres especiais", get(URI.create("/api/cnaes/codigo?codigo=%3Cscript%3E")), 400, "CNAE-VAL-0006")
        );
    }

    @ParameterizedTest(name = "{0} -> {2} {3}")
    @MethodSource("requisicoesInvalidas")
    void todaRespostaDeErroSegueOContrato(String cenario, MockHttpServletRequestBuilder requisicao,
                                          int statusEsperado, String codigoEsperado, CapturedOutput output) throws Exception {
        var resposta = mockMvc.perform(requisicao).andReturn().getResponse();
        var corpo = resposta.getContentAsString();

        assertThat(resposta.getStatus()).as("status HTTP").isEqualTo(statusEsperado);
        assertThat(MediaType.parseMediaType(resposta.getContentType()))
                .as("Content-Type").matches(MediaType.APPLICATION_PROBLEM_JSON::isCompatibleWith);

        String code = JsonPath.read(corpo, "$.code");
        Integer status = JsonPath.read(corpo, "$.status");
        assertThat(code).as("code").isEqualTo(codigoEsperado).matches("[A-Z]+-(VAL|NEG|INT)-\\d{4}");
        assertThat(ErrorCode.porCodigo(code)).as("code existe no catalogo").isNotNull();
        assertThat(status).as("status no corpo").isEqualTo(statusEsperado);
        assertThat(output).as("codigo registrado no log").contains("errorCode=" + code + " status=" + statusEsperado);
    }

    private static MockHttpServletRequestBuilder postJson(String corpo) {
        return post("/api/cadastros-secundarios").contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    private static String corpo(String nome, String documento, String codigoCnae) {
        return "{%s,%s,%s}".formatted(campo("nomeFantasia", nome), campo("documento", documento), campo("codigoCnae", codigoCnae));
    }

    private static String campo(String nome, String valor) {
        return valor == null ? "\"%s\":null".formatted(nome) : "\"%s\":\"%s\"".formatted(nome, valor);
    }
}
