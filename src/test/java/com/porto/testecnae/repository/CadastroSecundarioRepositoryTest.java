package com.porto.testecnae.repository;

import com.porto.testecnae.domain.AtividadeEconomicaCnae;
import com.porto.testecnae.domain.CadastroSecundario;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Item 7: {@code listarComCnae} carrega o CNAE junto, em uma unica consulta.
 * As estatisticas do Hibernate sao ligadas so durante o teste para contar os statements.
 */
@DataJpaTest
class CadastroSecundarioRepositoryTest {

    @Autowired
    private CadastroSecundarioRepository repository;

    @Autowired
    private AtividadeEconomicaCnaeRepository cnaeRepository;

    @Autowired
    private EntityManager entityManager;

    private Statistics statistics;

    @BeforeEach
    void ligarEstatisticas() {
        statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
    }

    @AfterEach
    void desligarEstatisticas() {
        statistics.setStatisticsEnabled(false);
    }

    @Test
    @DisplayName("CNAE vem carregado (nao e proxy lazy) mesmo apos limpar a sessao")
    void cnaeCarregado() {
        salvar("Empresa A", "11222333000181", "6201-5/01");
        limparSessao();

        var cadastros = repository.listarComCnae();

        assertThat(cadastros).singleElement().satisfies(c -> {
            assertThat(Hibernate.isInitialized(c.getCnae())).isTrue();
            assertThat(c.getCnae().getCodigo()).isEqualTo("6201-5/01");
        });
    }

    @Test
    @DisplayName("N cadastros com CNAEs diferentes: 1 unica consulta (antes: 1 + N)")
    void umaConsultaParaNCadastros() {
        salvar("Empresa A", "11222333000181", "6201-5/01");
        salvar("Empresa B", "11444777000161", "6622-3/00");
        salvar("Empresa C", "12345678000195", "7311-4/00");
        salvar("Empresa D", "12ABC34501DE35", "0111-3/01");
        limparSessao();

        var cadastros = repository.listarComCnae();
        // acessar o CNAE de todos nao pode disparar nenhuma consulta extra
        cadastros.forEach(c -> c.getCnae().getDescricao());

        assertThat(cadastros).hasSize(4);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("varios cadastros com o mesmo CNAE: join fetch nao duplica linhas")
    void mesmoCnaeSemDuplicar() {
        salvar("Empresa A", "11222333000181", "6201-5/01");
        salvar("Empresa B", "11444777000161", "6201-5/01");
        limparSessao();

        assertThat(repository.listarComCnae())
                .hasSize(2)
                .extracting(c -> c.getCnae().getCodigo())
                .containsOnly("6201-5/01");
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("sem cadastros: lista vazia com 1 consulta")
    void semCadastros() {
        limparSessao();

        assertThat(repository.listarComCnae()).isEmpty();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("contraprova: findAll deixa o CNAE como proxy nao carregado e gera 1 consulta extra por CNAE")
    void findAllEhLazy() {
        salvar("Empresa A", "11222333000181", "6201-5/01");
        salvar("Empresa B", "11444777000161", "6622-3/00");
        limparSessao();

        var cadastros = repository.findAll();

        assertThat(cadastros).allSatisfy(c -> assertThat(Hibernate.isInitialized(c.getCnae())).isFalse());
        cadastros.forEach(c -> c.getCnae().getDescricao());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
    }

    private void salvar(String nome, String documento, String codigoCnae) {
        AtividadeEconomicaCnae cnae = cnaeRepository.findByCodigo(codigoCnae).orElseThrow();
        repository.save(CadastroSecundario.builder().nomeFantasia(nome).documento(documento).cnae(cnae).build());
    }

    private void limparSessao() {
        entityManager.flush();
        entityManager.clear();
        statistics.clear();
    }
}
