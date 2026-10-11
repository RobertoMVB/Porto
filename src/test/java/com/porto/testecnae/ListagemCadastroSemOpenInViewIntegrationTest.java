package com.porto.testecnae;

import com.porto.testecnae.repository.CadastroSecundarioRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewFilter;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Item 7 com a configuracao real (open-in-view desligado).
 * <p>
 * <b>Sem {@code @Transactional} de proposito:</b> uma transacao do teste manteria a sessao aberta durante a
 * requisicao e esconderia o acesso lazy, exatamente como o open-in-view fazia. Os dados sao limpos a cada teste.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ListagemCadastroSemOpenInViewIntegrationTest {

    private static final String URL = "/api/cadastros-secundarios";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CadastroSecundarioRepository repository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private ApplicationContext context;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
    }

    @AfterEach
    void tearDown() {
        statistics.setStatisticsEnabled(false);
        repository.deleteAll();
    }

    @Test
    @DisplayName("open-in-view esta desligado no contexto da aplicacao")
    void openInViewDesligado() {
        assertThat(context.getEnvironment().getProperty("spring.jpa.open-in-view")).isEqualTo("false");
        assertThat(context.getBeanNamesForType(OpenEntityManagerInViewInterceptor.class)).isEmpty();
        assertThat(context.getBeanNamesForType(OpenEntityManagerInViewFilter.class)).isEmpty();
    }

    @Test
    @DisplayName("GET com 3 cadastros de CNAEs diferentes: 200, CNAEs completos e 1 unica consulta (antes: 500 sem open-in-view)")
    void listaComCnaesEmUmaConsulta(CapturedOutput output) throws Exception {
        cadastrar("Empresa A", "11222333000181", "6201-5/01");
        cadastrar("Empresa B", "11444777000161", "6622-3/00");
        cadastrar("Empresa C", "12345678000195", "7311-4/00");
        statistics.clear();

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].cnae.codigo", containsInAnyOrder("6201-5/01", "6622-3/00", "7311-4/00")))
                .andExpect(jsonPath("$[*].cnae.descricao", containsInAnyOrder(
                        "Desenvolvimento de programas de computador sob encomenda",
                        "Corretores e agentes de seguros",
                        "Agencias de publicidade")));

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(output).doesNotContain("GEN-INT-0002").doesNotContain("LazyInitializationException");
    }

    @Test
    @DisplayName("GET sem cadastros: 200 com lista vazia")
    void listaVazia() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("POST continua 201 com o CNAE completo na resposta (sem depender de sessao aberta)")
    void postContinuaFuncionando() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(json("Empresa A", "11222333000181", "6201-5/01")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cnae.descricao").value("Desenvolvimento de programas de computador sob encomenda"));
    }

    @Test
    @DisplayName("validar-cnae e busca de CNAE continuam funcionando com open-in-view desligado")
    void outrosEndpointsDeLeitura() throws Exception {
        mockMvc.perform(get(URL + "/validar-cnae").param("codigoCnae", "6201-5/01")).andExpect(status().isOk());
        mockMvc.perform(get("/api/cnaes/codigo").param("codigo", "6201-5/01")).andExpect(status().isOk());
        mockMvc.perform(get("/api/cnaes/buscar").param("termo", "programas")).andExpect(status().isOk());
        mockMvc.perform(get("/api/cnaes/paginado")).andExpect(status().isOk());
        mockMvc.perform(get("/api/cnaes")).andExpect(status().isOk());
    }

    private void cadastrar(String nome, String documento, String codigoCnae) throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json(nome, documento, codigoCnae)))
                .andExpect(status().isCreated());
    }

    private static String json(String nome, String documento, String codigoCnae) {
        return "{\"nomeFantasia\":\"%s\",\"documento\":\"%s\",\"codigoCnae\":\"%s\"}".formatted(nome, documento, codigoCnae);
    }
}
