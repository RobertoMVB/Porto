package com.porto.testecnae.repository;

import com.porto.testecnae.domain.AtividadeEconomicaCnae;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Executa a query real contra o H2, com a massa do {@code data.sql}.
 * E aqui que a correcao do LIKE e comprovada: um mock do repositorio nao exercitaria a JPQL.
 */
@DataJpaTest
class AtividadeEconomicaCnaeRepositoryTest {

    @Autowired
    private AtividadeEconomicaCnaeRepository repository;

    @Nested
    @DisplayName("termo em qualquer posicao da descricao")
    class Posicao {

        @Test
        @DisplayName("termo no meio da descricao (bug original: retornava vazio)")
        void termoNoMeio() {
            assertThat(repository.buscarPorDescricaoContendo("programas"))
                    .extracting(AtividadeEconomicaCnae::getCodigo)
                    .containsExactly("6201-5/01", "6202-3/00");
        }

        @Test
        @DisplayName("termo no inicio da descricao")
        void termoNoInicio() {
            assertThat(repository.buscarPorDescricaoContendo("Cultivo"))
                    .extracting(AtividadeEconomicaCnae::getCodigo)
                    .containsExactly("0111-3/01", "0112-1/01");
        }

        @Test
        @DisplayName("termo no fim da descricao")
        void termoNoFim() {
            assertThat(repository.buscarPorDescricaoContendo("encomenda"))
                    .extracting(AtividadeEconomicaCnae::getCodigo)
                    .containsExactly("6201-5/01");
        }

        @Test
        @DisplayName("descricao inteira como termo")
        void descricaoInteira() {
            assertThat(repository.buscarPorDescricaoContendo("Agencias de publicidade"))
                    .extracting(AtividadeEconomicaCnae::getCodigo)
                    .containsExactly("7311-4/00");
        }
    }

    @Nested
    @DisplayName("maiusculas, minusculas e ordenacao")
    class CaixaEOrdenacao {

        @ParameterizedTest(name = "termo [{0}]")
        @ValueSource(strings = {"programas", "PROGRAMAS", "Programas", "pRoGrAmAs"})
        @DisplayName("ignora maiusculas e minusculas")
        void ignoraCaixa(String termo) {
            assertThat(repository.buscarPorDescricaoContendo(termo))
                    .extracting(AtividadeEconomicaCnae::getCodigo)
                    .containsExactly("6201-5/01", "6202-3/00");
        }

        @Test
        @DisplayName("resultado ordenado por codigo")
        void ordenadoPorCodigo() {
            // "de" aparece em descricoes de varias secoes
            assertThat(repository.buscarPorDescricaoContendo("de"))
                    .extracting(AtividadeEconomicaCnae::getCodigo)
                    .isSorted()
                    .hasSizeGreaterThan(1);
        }
    }

    @Nested
    @DisplayName("sem resultado")
    class SemResultado {

        @Test
        @DisplayName("termo inexistente retorna lista vazia")
        void termoInexistente() {
            assertThat(repository.buscarPorDescricaoContendo("blockchain")).isEmpty();
        }

        @Test
        @DisplayName("termo com acento nao casa com descricao sem acento (comportamento atual)")
        void acentoNaoEhNormalizado() {
            assertThat(repository.buscarPorDescricaoContendo("algodão")).isEmpty();
        }
    }

    @Nested
    @DisplayName("curingas do LIKE tratados como texto literal")
    class Curingas {

        @ParameterizedTest(name = "termo [{0}]")
        @ValueSource(strings = {"%", "_", "%%", "__", "\\", "prog%mas", "prog_amas"})
        @DisplayName("curingas nao casam com descricoes que nao os contem")
        void curingasNaoRetornamTudo(String termo) {
            assertThat(repository.buscarPorDescricaoContendo(termo)).isEmpty();
        }

        @Test
        @DisplayName("'%' literal encontra descricao que contem '%'")
        void percentualLiteral() {
            salvar("9999-0/01", "Desconto de 100% em seguros");
            salvar("9999-0/02", "Desconto de 1000 em seguros");

            assertThat(repository.buscarPorDescricaoContendo("100%"))
                    .extracting(AtividadeEconomicaCnae::getCodigo)
                    .containsExactly("9999-0/01");
        }

        @Test
        @DisplayName("'_' literal encontra descricao que contem '_'")
        void sublinhadoLiteral() {
            salvar("9999-0/03", "Servico item_especial");
            salvar("9999-0/04", "Servico itemXespecial");

            assertThat(repository.buscarPorDescricaoContendo("item_especial"))
                    .extracting(AtividadeEconomicaCnae::getCodigo)
                    .containsExactly("9999-0/03");
        }

        @Test
        @DisplayName("'\\' literal encontra descricao que contem '\\'")
        void barraInvertidaLiteral() {
            salvar("9999-0/05", "Importacao\\exportacao");

            assertThat(repository.buscarPorDescricaoContendo("o\\e"))
                    .extracting(AtividadeEconomicaCnae::getCodigo)
                    .containsExactly("9999-0/05");
        }
    }

    /**
     * O termo nao sofre trim (decisao pendente). Estes testes documentam o impacto dos espacos
     * para apoiar a avaliacao futura.
     */
    @Nested
    @DisplayName("impacto de espacos no termo (sem trim)")
    class Espacos {

        @Test
        @DisplayName("espaco antes do termo: casa apenas quando ha palavra antes")
        void espacoAntes() {
            assertThat(repository.buscarPorDescricaoContendo(" programas"))
                    .extracting(AtividadeEconomicaCnae::getCodigo)
                    .containsExactly("6201-5/01", "6202-3/00");
        }

        @Test
        @DisplayName("espaco depois do termo: perde o CNAE em que o termo termina a descricao")
        void espacoDepois() {
            // "encomenda" e a ultima palavra da descricao do 6201-5/01; "programas" sempre tem palavra depois
            assertThat(repository.buscarPorDescricaoContendo("encomenda ")).isEmpty();
            assertThat(repository.buscarPorDescricaoContendo("programas "))
                    .extracting(AtividadeEconomicaCnae::getCodigo)
                    .containsExactly("6201-5/01", "6202-3/00");
        }

        @Test
        @DisplayName("espaco antes do termo: perde o CNAE em que o termo inicia a descricao")
        void espacoAntesNoInicio() {
            assertThat(repository.buscarPorDescricaoContendo(" Cultivo")).isEmpty();
        }

        @Test
        @DisplayName("espaco duplo: nao casa com descricoes de espaco simples")
        void espacoDuplo() {
            assertThat(repository.buscarPorDescricaoContendo("de  programas")).isEmpty();
        }
    }

    private void salvar(String codigo, String descricao) {
        repository.saveAndFlush(AtividadeEconomicaCnae.builder()
                .codigo(codigo)
                .descricao(descricao)
                .secao("Teste")
                .build());
    }
}
