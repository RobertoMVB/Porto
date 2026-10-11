package com.porto.testecnae;

import com.porto.testecnae.domain.AtividadeEconomicaCnae;
import com.porto.testecnae.repository.AtividadeEconomicaCnaeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Item 8: GET /api/cnaes e GET /api/cnaes/paginado contra o H2 real (massa do data.sql: 8 CNAEs).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CnaeListagemIntegrationTest {

    private static final String[] CODIGOS_ORDENADOS = {
            "0111-3/01", "0112-1/01", "1011-2/01", "4711-3/02", "6201-5/01", "6202-3/00", "6622-3/00", "7311-4/00"};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AtividadeEconomicaCnaeRepository repository;

    @Nested
    @DisplayName("GET /api/cnaes")
    class ListarTodas {

        @Test
        @DisplayName("devolve os 8 CNAEs do data.sql (sem ordem garantida: findAll nao ordena)")
        void todos() throws Exception {
            mockMvc.perform(get("/api/cnaes"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(8))
                    .andExpect(jsonPath("$[*].codigo", containsInAnyOrder(CODIGOS_ORDENADOS)));
        }

        @Test
        @DisplayName("cada CNAE traz id, codigo, descricao e secao")
        void todosOsCampos() throws Exception {
            var cnae6201 = "$[?(@.codigo == '6201-5/01')]";
            mockMvc.perform(get("/api/cnaes"))
                    .andExpect(jsonPath(cnae6201 + ".id").isNotEmpty())
                    .andExpect(jsonPath(cnae6201 + ".descricao").value("Desenvolvimento de programas de computador sob encomenda"))
                    .andExpect(jsonPath(cnae6201 + ".secao").value("Tecnologia"));
        }

        @Test
        @DisplayName("reflete CNAEs incluidos depois da carga inicial")
        void refleteInclusao() throws Exception {
            salvarCnae("0001-0/01", "Atividade de teste");

            mockMvc.perform(get("/api/cnaes"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(9));
        }
    }

    @Nested
    @DisplayName("GET /api/cnaes/paginado - cenarios positivos")
    class PaginadoPositivo {

        @Test
        @DisplayName("valores padrao: pagina 0, tamanho 20, 8 itens em 1 pagina")
        void padrao() throws Exception {
            mockMvc.perform(get("/api/cnaes/paginado"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pagina").value(0))
                    .andExpect(jsonPath("$.tamanho").value(20))
                    .andExpect(jsonPath("$.totalElementos").value(8))
                    .andExpect(jsonPath("$.totalPaginas").value(1))
                    .andExpect(jsonPath("$.conteudo[*].codigo", contains(CODIGOS_ORDENADOS)));
        }

        @ParameterizedTest(name = "page={0} size={1}")
        @CsvSource({
                "0, 3, '0111-3/01,0112-1/01,1011-2/01'",
                "1, 3, '4711-3/02,6201-5/01,6202-3/00'",
                "2, 3, '6622-3/00,7311-4/00'"
        })
        @DisplayName("paginas de 3: conteudo na ordem do codigo, ultima pagina parcial")
        void paginasDeTres(int page, int size, String esperados) throws Exception {
            mockMvc.perform(get("/api/cnaes/paginado").param("page", String.valueOf(page)).param("size", String.valueOf(size)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pagina").value(page))
                    .andExpect(jsonPath("$.tamanho").value(size))
                    .andExpect(jsonPath("$.totalElementos").value(8))
                    .andExpect(jsonPath("$.totalPaginas").value(3))
                    .andExpect(jsonPath("$.conteudo[*].codigo", contains(esperados.split(","))));
        }

        @Test
        @DisplayName("ordena por codigo, nao pela ordem de insercao")
        void ordenaPorCodigo() throws Exception {
            // inserido por ultimo, mas com o menor codigo: deve abrir a primeira pagina
            salvarCnae("0001-0/01", "Atividade de teste");

            mockMvc.perform(get("/api/cnaes/paginado").param("size", "2"))
                    .andExpect(jsonPath("$.conteudo[*].codigo", contains("0001-0/01", "0111-3/01")))
                    .andExpect(jsonPath("$.totalElementos").value(9));
        }

        @Test
        @DisplayName("size no limite maximo (100)")
        void tamanhoMaximo() throws Exception {
            mockMvc.perform(get("/api/cnaes/paginado").param("size", "100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tamanho").value(100))
                    .andExpect(jsonPath("$.conteudo.length()").value(8));
        }

        @Test
        @DisplayName("size 1: uma pagina por CNAE")
        void tamanhoUm() throws Exception {
            mockMvc.perform(get("/api/cnaes/paginado").param("page", "7").param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalPaginas").value(8))
                    .andExpect(jsonPath("$.conteudo[*].codigo", contains("7311-4/00")));
        }

        @Test
        @DisplayName("pagina alem do fim: 200 com conteudo vazio e totais corretos (nao e erro)")
        void paginaAlemDoFim() throws Exception {
            mockMvc.perform(get("/api/cnaes/paginado").param("page", "10").param("size", "3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.conteudo").isEmpty())
                    .andExpect(jsonPath("$.pagina").value(10))
                    .andExpect(jsonPath("$.totalElementos").value(8))
                    .andExpect(jsonPath("$.totalPaginas").value(3));
        }
    }

    @Nested
    @DisplayName("GET /api/cnaes/paginado - cenarios negativos")
    class PaginadoNegativo {

        @ParameterizedTest(name = "page={0} size={1} -> {2}")
        @CsvSource({
                "-1,  20,  GEN-VAL-0003",
                "0,   0,   GEN-VAL-0004",
                "0,   -5,  GEN-VAL-0004",
                "0,   101, GEN-VAL-0004",
                "abc, 20,  GEN-VAL-0002",
                "0,   1.5, GEN-VAL-0002"
        })
        void parametrosInvalidos(String page, String size, String codigo) throws Exception {
            mockMvc.perform(get("/api/cnaes/paginado").param("page", page).param("size", size))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(codigo));
        }
    }

    private void salvarCnae(String codigo, String descricao) {
        repository.saveAndFlush(AtividadeEconomicaCnae.builder().codigo(codigo).descricao(descricao).secao("Teste").build());
    }
}
