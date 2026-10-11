package com.porto.testecnae;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo completo da busca: HTTP -> controller -> service (normalizacao/validacao) -> repositorio -> H2 (data.sql).
 */
@SpringBootTest
@AutoConfigureMockMvc
class BuscaCnaePorDescricaoIntegrationTest {

    private static final String URL = "/api/cnaes/buscar";

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "termo [{0}]")
    @ValueSource(strings = {"programas", " programas", "programas ", "  programas  ", "\tprogramas\n"})
    @DisplayName("espacos nas pontas nao alteram o resultado: 2 CNAEs de software")
    void espacosNasPontasNaoAlteramResultado(String termo) throws Exception {
        mockMvc.perform(get(URL).param("termo", termo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigo", contains("6201-5/01", "6202-3/00")));
    }

    @Test
    @DisplayName("espaco antes da primeira palavra da descricao: antes retornava vazio")
    void espacoAntesDaPrimeiraPalavra() throws Exception {
        mockMvc.perform(get(URL).param("termo", " Cultivo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigo", contains("0111-3/01", "0112-1/01")));
    }

    @Test
    @DisplayName("espaco depois da ultima palavra da descricao: antes retornava vazio")
    void espacoDepoisDaUltimaPalavra() throws Exception {
        mockMvc.perform(get(URL).param("termo", "encomenda "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigo", contains("6201-5/01")));
    }

    @Test
    @DisplayName("espaco codificado na URL (%20) tambem e removido")
    void espacoCodificadoNaUrl() throws Exception {
        // URI.create evita que o MockMvc codifique o '%' novamente (trataria a string como template)
        mockMvc.perform(get(URI.create(URL + "?termo=%20encomenda%20")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigo", contains("6201-5/01")));
    }

    @Test
    @DisplayName("espaco interno e mantido: termo com espaco duplo nao encontra nada")
    void espacoInternoMantido() throws Exception {
        mockMvc.perform(get(URL).param("termo", "de  programas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("curinga com espacos continua literal: nao retorna todos")
    void curingaComEspacosContinuaLiteral() throws Exception {
        mockMvc.perform(get(URL).param("termo", " % "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @ParameterizedTest(name = "termo [{0}]")
    @ValueSource(strings = {"", " ", "   ", "\t"})
    @DisplayName("termo so com espacos continua 400 CNAE-VAL-0002")
    void termoSoComEspacos(String termo) throws Exception {
        mockMvc.perform(get(URL).param("termo", termo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CNAE-VAL-0002"));
    }

    @Test
    @DisplayName("NBSP (U+00A0) nao e removido: retorna vazio (limitacao conhecida)")
    void nbspNaoEhRemovido() throws Exception {
        mockMvc.perform(get(URL).param("termo", " programas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
