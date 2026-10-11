package com.porto.testecnae.controller;

import com.porto.testecnae.dto.AtividadeEconomicaCnaeResponse;
import com.porto.testecnae.dto.PaginaResponse;
import com.porto.testecnae.error.ApplicationException;
import com.porto.testecnae.error.ErrorCode;
import com.porto.testecnae.error.ErrorMapper;
import com.porto.testecnae.service.AtividadeEconomicaCnaeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AtividadeEconomicaCnaeController.class)
@Import(ErrorMapper.class)
class AtividadeEconomicaCnaeControllerTest {

    private static final AtividadeEconomicaCnaeResponse CNAE_6201 = new AtividadeEconomicaCnaeResponse(
            5L, "6201-5/01", "Desenvolvimento de programas de computador sob encomenda", "Tecnologia");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AtividadeEconomicaCnaeService service;

    @Nested
    @DisplayName("GET /api/cnaes/buscar")
    class Buscar {

        @Test
        @DisplayName("200 com a lista de CNAEs encontrados")
        void retornaResultados() throws Exception {
            when(service.buscarPorDescricao("programas")).thenReturn(List.of(CNAE_6201));

            mockMvc.perform(get("/api/cnaes/buscar").param("termo", "programas"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].codigo").value("6201-5/01"))
                    .andExpect(jsonPath("$[0].descricao").value(CNAE_6201.descricao()))
                    .andExpect(jsonPath("$[0].secao").value("Tecnologia"));
        }

        @Test
        @DisplayName("200 com lista vazia quando nada e encontrado")
        void retornaListaVazia() throws Exception {
            when(service.buscarPorDescricao("blockchain")).thenReturn(List.of());

            mockMvc.perform(get("/api/cnaes/buscar").param("termo", "blockchain"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("[]"));
        }

        @Test
        @DisplayName("400 GEN-VAL-0001 quando o parametro 'termo' nao e enviado")
        void parametroAusente() throws Exception {
            mockMvc.perform(get("/api/cnaes/buscar"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("GEN-VAL-0001"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.title").value("Bad Request"))
                    .andExpect(jsonPath("$.detail").value("Parametro obrigatorio 'termo' nao informado."))
                    .andExpect(jsonPath("$.instance").value("/api/cnaes/buscar"));

            verify(service, never()).buscarPorDescricao(anyString());
        }

        @Test
        @DisplayName("400 CNAE-VAL-0002 quando o servico rejeita termo em branco")
        void termoEmBranco() throws Exception {
            when(service.buscarPorDescricao(" ")).thenThrow(new ApplicationException(ErrorCode.CNAE_VAL_0002));

            mockMvc.perform(get("/api/cnaes/buscar").param("termo", " "))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("CNAE-VAL-0002"))
                    .andExpect(jsonPath("$.detail").value(ErrorCode.CNAE_VAL_0002.getMensagem()))
                    .andExpect(jsonPath("$.instance").value("/api/cnaes/buscar"));
        }

        @Test
        @DisplayName("termo vazio ('?termo=') chega ao servico e e tratado por ele")
        void termoVazioChegaAoServico() throws Exception {
            when(service.buscarPorDescricao("")).thenThrow(new ApplicationException(ErrorCode.CNAE_VAL_0002));

            mockMvc.perform(get("/api/cnaes/buscar").param("termo", ""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CNAE-VAL-0002"));
        }

        @Test
        @DisplayName("400 CNAE-VAL-0003 quando o servico rejeita termo longo demais")
        void termoLongoDemais() throws Exception {
            var termo = "a".repeat(181);
            when(service.buscarPorDescricao(termo)).thenThrow(new ApplicationException(ErrorCode.CNAE_VAL_0003,
                    "O termo de busca deve ter no maximo 180 caracteres."));

            mockMvc.perform(get("/api/cnaes/buscar").param("termo", termo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CNAE-VAL-0003"))
                    .andExpect(jsonPath("$.detail").value("O termo de busca deve ter no maximo 180 caracteres."));
        }

        @Test
        @DisplayName("500 GEN-INT-0001 em erro inesperado, sem vazar detalhe tecnico")
        void erroInesperado() throws Exception {
            when(service.buscarPorDescricao("programas"))
                    .thenThrow(new IllegalStateException("Connection refused: jdbc:h2:mem:cnaedb"));

            mockMvc.perform(get("/api/cnaes/buscar").param("termo", "programas"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("GEN-INT-0001"))
                    .andExpect(jsonPath("$.detail").value(ErrorCode.GEN_INT_0001.getMensagem()));
        }
    }

    @Nested
    @DisplayName("GET /api/cnaes/codigo (item 3)")
    class Codigo {

        @Test
        @DisplayName("200 com o CNAE quando o codigo existe")
        void encontrado() throws Exception {
            when(service.buscarPorCodigo("6201-5/01")).thenReturn(CNAE_6201);

            mockMvc.perform(get("/api/cnaes/codigo").param("codigo", "6201-5/01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(5))
                    .andExpect(jsonPath("$.codigo").value("6201-5/01"))
                    .andExpect(jsonPath("$.descricao").value(CNAE_6201.descricao()));
        }

        @Test
        @DisplayName("404 CNAE-NEG-0001 quando o codigo nao existe")
        void naoEncontrado() throws Exception {
            when(service.buscarPorCodigo("9999-9/99")).thenThrow(
                    new ApplicationException(ErrorCode.CNAE_NEG_0001, "CNAE '9999-9/99' nao encontrado."));

            mockMvc.perform(get("/api/cnaes/codigo").param("codigo", "9999-9/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("CNAE-NEG-0001"))
                    .andExpect(jsonPath("$.title").value("Not Found"))
                    .andExpect(jsonPath("$.detail").value("CNAE '9999-9/99' nao encontrado."))
                    .andExpect(jsonPath("$.instance").value("/api/cnaes/codigo"));
        }

        @Test
        @DisplayName("400 CNAE-VAL-0005 quando o codigo e vazio")
        void codigoVazio() throws Exception {
            when(service.buscarPorCodigo("")).thenThrow(new ApplicationException(ErrorCode.CNAE_VAL_0005));

            mockMvc.perform(get("/api/cnaes/codigo").param("codigo", ""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CNAE-VAL-0005"));
        }

        @Test
        @DisplayName("400 CNAE-VAL-0006 quando o formato e invalido")
        void formatoInvalido() throws Exception {
            when(service.buscarPorCodigo("6201501")).thenThrow(new ApplicationException(ErrorCode.CNAE_VAL_0006));

            mockMvc.perform(get("/api/cnaes/codigo").param("codigo", "6201501"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CNAE-VAL-0006"))
                    .andExpect(jsonPath("$.detail").value(ErrorCode.CNAE_VAL_0006.getMensagem()));
        }

        @Test
        @DisplayName("400 GEN-VAL-0001 quando o parametro 'codigo' nao e enviado")
        void parametroAusente() throws Exception {
            mockMvc.perform(get("/api/cnaes/codigo"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("GEN-VAL-0001"))
                    .andExpect(jsonPath("$.detail").value("Parametro obrigatorio 'codigo' nao informado."));

            verify(service, never()).buscarPorCodigo(anyString());
        }
    }

    @Nested
    @DisplayName("GET /api/cnaes/paginado")
    class Paginado {

        @Test
        @DisplayName("200 usando page=0 e size=20 por padrao")
        void valoresPadrao() throws Exception {
            when(service.listarPaginado(0, 20)).thenReturn(new PaginaResponse<>(List.of(CNAE_6201), 0, 20, 1, 1));

            mockMvc.perform(get("/api/cnaes/paginado"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.conteudo[0].codigo").value("6201-5/01"))
                    .andExpect(jsonPath("$.pagina").value(0))
                    .andExpect(jsonPath("$.tamanho").value(20))
                    .andExpect(jsonPath("$.totalElementos").value(1))
                    .andExpect(jsonPath("$.totalPaginas").value(1));
        }

        @Test
        @DisplayName("200 repassando page e size informados")
        void valoresInformados() throws Exception {
            when(service.listarPaginado(2, 3)).thenReturn(new PaginaResponse<>(List.of(), 2, 3, 8, 3));

            mockMvc.perform(get("/api/cnaes/paginado").param("page", "2").param("size", "3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.conteudo").isEmpty())
                    .andExpect(jsonPath("$.pagina").value(2));
        }

        @Test
        @DisplayName("400 GEN-VAL-0002 quando page nao e numerico")
        void pageNaoNumerico() throws Exception {
            mockMvc.perform(get("/api/cnaes/paginado").param("page", "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("GEN-VAL-0002"))
                    .andExpect(jsonPath("$.detail").value("Parametro 'page' com valor invalido."));

            verify(service, never()).listarPaginado(anyInt(), anyInt());
        }

        @Test
        @DisplayName("400 GEN-VAL-0004 quando o servico rejeita o tamanho")
        void tamanhoInvalido() throws Exception {
            when(service.listarPaginado(0, 500)).thenThrow(new ApplicationException(ErrorCode.GEN_VAL_0004));

            mockMvc.perform(get("/api/cnaes/paginado").param("size", "500"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("GEN-VAL-0004"));
        }
    }
}
