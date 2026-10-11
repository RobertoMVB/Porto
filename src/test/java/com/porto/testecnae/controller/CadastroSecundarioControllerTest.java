package com.porto.testecnae.controller;

import com.porto.testecnae.dto.AtividadeEconomicaCnaeResponse;
import com.porto.testecnae.dto.CadastroSecundarioRequest;
import com.porto.testecnae.dto.CadastroSecundarioResponse;
import com.porto.testecnae.error.ApplicationException;
import com.porto.testecnae.error.ErrorCode;
import com.porto.testecnae.error.ErrorMapper;
import com.porto.testecnae.service.CadastroSecundarioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CadastroSecundarioController.class)
@Import(ErrorMapper.class)
class CadastroSecundarioControllerTest {

    private static final AtividadeEconomicaCnaeResponse CNAE_6201 = new AtividadeEconomicaCnaeResponse(
            5L, "6201-5/01", "Desenvolvimento de programas de computador sob encomenda", "Tecnologia");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CadastroSecundarioService service;

    @Nested
    @DisplayName("GET /api/cadastros-secundarios/validar-cnae (item 4)")
    class ValidarCnae {

        private static final String URL = "/api/cadastros-secundarios/validar-cnae";

        @Test
        @DisplayName("200 com o CNAE quando o codigo existe")
        void existente() throws Exception {
            when(service.validarCnae("6201-5/01")).thenReturn(CNAE_6201);

            mockMvc.perform(get(URL).param("codigoCnae", "6201-5/01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.codigo").value("6201-5/01"));
        }

        @Test
        @DisplayName("404 CNAE-NEG-0001 quando o codigo nao existe")
        void inexistente() throws Exception {
            when(service.validarCnae("9999-9/99")).thenThrow(
                    new ApplicationException(ErrorCode.CNAE_NEG_0001, "CNAE '9999-9/99' nao encontrado."));

            mockMvc.perform(get(URL).param("codigoCnae", "9999-9/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("CNAE-NEG-0001"))
                    .andExpect(jsonPath("$.instance").value(URL));
        }

        @Test
        @DisplayName("400 CNAE-VAL-0006 quando o formato e invalido")
        void formatoInvalido() throws Exception {
            when(service.validarCnae("abc")).thenThrow(new ApplicationException(ErrorCode.CNAE_VAL_0006));

            mockMvc.perform(get(URL).param("codigoCnae", "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CNAE-VAL-0006"));
        }

        @Test
        @DisplayName("400 GEN-VAL-0001 quando o parametro 'codigoCnae' nao e enviado")
        void parametroAusente() throws Exception {
            mockMvc.perform(get(URL))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("GEN-VAL-0001"))
                    .andExpect(jsonPath("$.detail").value("Parametro obrigatorio 'codigoCnae' nao informado."));

            verify(service, never()).validarCnae(anyString());
        }
    }

    @Nested
    @DisplayName("POST /api/cadastros-secundarios (item 5)")
    class Cadastrar {

        private static final String URL = "/api/cadastros-secundarios";
        private static final String BODY_VALIDO =
                "{\"nomeFantasia\":\"Tech Porto\",\"documento\":\"12345678000199\",\"codigoCnae\":\"6201-5/01\"}";

        @Test
        @DisplayName("201 com o cadastro criado")
        void criado() throws Exception {
            when(service.cadastrar(new CadastroSecundarioRequest("Tech Porto", "12345678000199", "6201-5/01")))
                    .thenReturn(new CadastroSecundarioResponse(1L, "Tech Porto", "12345678000199", CNAE_6201));

            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.cnae.codigo").value("6201-5/01"));
        }

        @Test
        @DisplayName("422 CADSEC-NEG-0001 quando o CNAE nao existe")
        void cnaeInexistente() throws Exception {
            when(service.cadastrar(any())).thenThrow(new ApplicationException(ErrorCode.CADSEC_NEG_0001,
                    "Cadastro rejeitado: o CNAE '9999-9/99' nao existe."));

            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                            .content(BODY_VALIDO.replace("6201-5/01", "9999-9/99")))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("CADSEC-NEG-0001"))
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.detail").value("Cadastro rejeitado: o CNAE '9999-9/99' nao existe."))
                    .andExpect(jsonPath("$.instance").value(URL));
        }

        @Test
        @DisplayName("400 CNAE-VAL-0004 quando codigoCnae nao vem no JSON")
        void codigoAusente() throws Exception {
            when(service.cadastrar(new CadastroSecundarioRequest("Tech Porto", "12345678000199", null)))
                    .thenThrow(new ApplicationException(ErrorCode.CNAE_VAL_0004));

            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nomeFantasia\":\"Tech Porto\",\"documento\":\"12345678000199\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CNAE-VAL-0004"));
        }

        @Test
        @DisplayName("400 CNAE-VAL-0006 quando codigoCnae e malformado")
        void codigoMalformado() throws Exception {
            when(service.cadastrar(any())).thenThrow(new ApplicationException(ErrorCode.CNAE_VAL_0006));

            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                            .content(BODY_VALIDO.replace("6201-5/01", "6201501")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CNAE-VAL-0006"));
        }

        @Test
        @DisplayName("400 (sem code) para JSON malformado: tratado pelo Spring, nao chega ao servico")
        void jsonMalformado() throws Exception {
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{invalido"))
                    .andExpect(status().isBadRequest());

            verify(service, never()).cadastrar(any());
        }
    }
}
