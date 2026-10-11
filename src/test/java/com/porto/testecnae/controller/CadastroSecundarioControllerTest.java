package com.porto.testecnae.controller;

import com.porto.testecnae.dto.AtividadeEconomicaCnaeResponse;
import com.porto.testecnae.dto.CadastroSecundarioRequest;
import com.porto.testecnae.dto.CadastroSecundarioResponse;
import com.porto.testecnae.error.ApplicationException;
import com.porto.testecnae.error.ErrorCode;
import com.porto.testecnae.error.ErrorMapper;
import com.porto.testecnae.service.CadastroSecundarioService;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;

import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
                "{\"nomeFantasia\":\"Tech Porto\",\"documento\":\"98765432000198\",\"codigoCnae\":\"6201-5/01\"}";

        @Test
        @DisplayName("201 com o cadastro criado")
        void criado() throws Exception {
            when(service.cadastrar(new CadastroSecundarioRequest("Tech Porto", "98765432000198", "6201-5/01")))
                    .thenReturn(new CadastroSecundarioResponse(1L, "Tech Porto", "98765432000198", CNAE_6201));

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
            when(service.cadastrar(new CadastroSecundarioRequest("Tech Porto", "98765432000198", null)))
                    .thenThrow(new ApplicationException(ErrorCode.CNAE_VAL_0004));

            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nomeFantasia\":\"Tech Porto\",\"documento\":\"98765432000198\"}"))
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

    }

    @Nested
    @DisplayName("POST /api/cadastros-secundarios - validacao dos campos (item 6)")
    class Validacao {

        private static final String URL = "/api/cadastros-secundarios";

        @Test
        @DisplayName("1 campo invalido: code do campo e errors com 1 item")
        void umCampoInvalido() throws Exception {
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                            .content(body("Tech Porto", "12345678909", "6201-5/01")))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("CADSEC-VAL-0005"))
                    .andExpect(jsonPath("$.detail").value(ErrorCode.CADSEC_VAL_0005.getMensagem()))
                    .andExpect(jsonPath("$.errors.length()").value(1))
                    .andExpect(jsonPath("$.errors[0].campo").value("documento"))
                    .andExpect(jsonPath("$.errors[0].code").value("CADSEC-VAL-0005"))
                    .andExpect(jsonPath("$.errors[0].mensagem").value(ErrorCode.CADSEC_VAL_0005.getMensagem()));

            verify(service, never()).cadastrar(any());
        }

        @Test
        @DisplayName("3 campos invalidos: code do primeiro e errors em ordem de declaracao")
        void tresCamposInvalidos() throws Exception {
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                            .content(body(" ", "abc", "6201501")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CADSEC-VAL-0001"))
                    .andExpect(jsonPath("$.detail").value("A requisicao possui 3 campos invalidos."))
                    .andExpect(jsonPath("$.errors.length()").value(3))
                    .andExpect(jsonPath("$.errors[0].campo").value("nomeFantasia"))
                    .andExpect(jsonPath("$.errors[0].code").value("CADSEC-VAL-0001"))
                    .andExpect(jsonPath("$.errors[1].campo").value("documento"))
                    .andExpect(jsonPath("$.errors[1].code").value("CADSEC-VAL-0004"))
                    .andExpect(jsonPath("$.errors[2].campo").value("codigoCnae"))
                    .andExpect(jsonPath("$.errors[2].code").value("CNAE-VAL-0006"));

            verify(service, never()).cadastrar(any());
        }

        @Test
        @DisplayName("corpo {}: os tres obrigatorios em errors")
        void corpoVazioObjeto() throws Exception {
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CADSEC-VAL-0001"))
                    .andExpect(jsonPath("$.errors[*].code")
                            .value(contains("CADSEC-VAL-0001", "CADSEC-VAL-0003", "CNAE-VAL-0004")));
        }

        @Test
        @DisplayName("so codigoCnae ausente: code continua CNAE-VAL-0004 (contrato do item 5 mantido)")
        void soCodigoAusenteMantemContrato() throws Exception {
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nomeFantasia\":\"Tech Porto\",\"documento\":\"12345678000195\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CNAE-VAL-0004"))
                    .andExpect(jsonPath("$.errors[0].campo").value("codigoCnae"));
        }

        @Test
        @DisplayName("nome com 121 caracteres: CADSEC-VAL-0002")
        void nomeLongo() throws Exception {
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                            .content(body("a".repeat(121), "12345678000195", "6201-5/01")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CADSEC-VAL-0002"));
        }

        @Test
        @DisplayName("valores sao normalizados antes de chegar ao servico (strip + maiusculas)")
        void normalizaAntesDoServico() throws Exception {
            when(service.cadastrar(any())).thenReturn(
                    new CadastroSecundarioResponse(1L, "Tech Porto", "12ABC34501DE35", CNAE_6201));

            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                            .content(body("  Tech Porto ", " 12abc34501de35 ", " 6201-5/01 ")))
                    .andExpect(status().isCreated());

            verify(service).cadastrar(new CadastroSecundarioRequest("Tech Porto", "12ABC34501DE35", "6201-5/01"));
        }

        @Test
        @DisplayName("409 CADSEC-NEG-0002 quando o servico detecta CNPJ duplicado (sem errors)")
        void cnpjDuplicado() throws Exception {
            when(service.cadastrar(any())).thenThrow(new ApplicationException(ErrorCode.CADSEC_NEG_0002));

            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                            .content(body("Tech Porto", "12345678000195", "6201-5/01")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CADSEC-NEG-0002"))
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }

        @Test
        @DisplayName("400 GEN-VAL-0005 para JSON malformado, sem chegar ao servico")
        void jsonMalformado() throws Exception {
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{invalido"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("GEN-VAL-0005"))
                    .andExpect(jsonPath("$.detail").value(ErrorCode.GEN_VAL_0005.getMensagem()));

            verify(service, never()).cadastrar(any());
        }

        @Test
        @DisplayName("400 GEN-VAL-0005 para corpo ausente")
        void corpoAusente() throws Exception {
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("GEN-VAL-0005"));
        }

        @Test
        @DisplayName("400 GEN-VAL-0005 para array no lugar de objeto")
        void arrayNoLugarDeObjeto() throws Exception {
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("[]"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("GEN-VAL-0005"));
        }

        private static String body(String nomeFantasia, String documento, String codigoCnae) {
            return "{\"nomeFantasia\":\"%s\",\"documento\":\"%s\",\"codigoCnae\":\"%s\"}"
                    .formatted(nomeFantasia, documento, codigoCnae);
        }
    }
    @Nested
    @DisplayName("GET /api/cadastros-secundarios e falhas de infraestrutura (item 7)")
    class Listar {

        private static final String URL = "/api/cadastros-secundarios";

        @Test
        @DisplayName("200 com a lista e o CNAE de cada cadastro")
        void lista() throws Exception {
            when(service.listarTodos()).thenReturn(List.of(
                    new CadastroSecundarioResponse(1L, "Tech Porto", "12345678000195", CNAE_6201)));

            mockMvc.perform(get(URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].cnae.codigo").value("6201-5/01"))
                    .andExpect(jsonPath("$[0].cnae.descricao").value(CNAE_6201.descricao()));
        }

        @Test
        @DisplayName("200 com lista vazia")
        void listaVazia() throws Exception {
            when(service.listarTodos()).thenReturn(List.of());

            mockMvc.perform(get(URL))
                    .andExpect(status().isOk())
                    .andExpect(content().json("[]"));
        }

        @Test
        @DisplayName("LazyInitializationException: 500 GEN-INT-0002 sem vazar detalhe tecnico")
        void lazyInitialization() throws Exception {
            when(service.listarTodos()).thenThrow(new LazyInitializationException(
                    "Could not initialize proxy [AtividadeEconomicaCnae#5] - no session"));

            mockMvc.perform(get(URL))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("GEN-INT-0002"))
                    .andExpect(jsonPath("$.detail").value(ErrorCode.GEN_INT_0002.getMensagem()))
                    .andExpect(content().string(not(containsString("proxy"))));
        }

        @Test
        @DisplayName("CannotCreateTransactionException: 503 GEN-INT-0003")
        void transacaoNaoAberta() throws Exception {
            when(service.listarTodos()).thenThrow(new CannotCreateTransactionException("Could not open JPA EntityManager"));

            mockMvc.perform(get(URL))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("GEN-INT-0003"))
                    .andExpect(jsonPath("$.detail").value(ErrorCode.GEN_INT_0003.getMensagem()))
                    .andExpect(content().string(not(containsString("EntityManager"))));
        }

        @Test
        @DisplayName("CannotGetJdbcConnectionException (sem conexao com o banco): 503 GEN-INT-0003")
        void semConexao() throws Exception {
            when(service.validarCnae("6201-5/01")).thenThrow(
                    new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection"));

            mockMvc.perform(get(URL + "/validar-cnae").param("codigoCnae", "6201-5/01"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("GEN-INT-0003"));
        }

        @Test
        @DisplayName("outra falha de acesso a dados continua 500 GEN-INT-0001 (nao vira 503)")
        void outraFalhaContinuaGenerica() throws Exception {
            when(service.listarTodos()).thenThrow(new InvalidDataAccessResourceUsageException("SQL grammar"));

            mockMvc.perform(get(URL))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("GEN-INT-0001"));
        }
    }
}
