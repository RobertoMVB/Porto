package com.porto.testecnae.service.impl;

import com.porto.testecnae.domain.AtividadeEconomicaCnae;
import com.porto.testecnae.error.ApplicationException;
import com.porto.testecnae.error.ErrorCode;
import com.porto.testecnae.repository.AtividadeEconomicaCnaeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AtividadeEconomicaCnaeServiceImplTest {

    private static final AtividadeEconomicaCnae CNAE_6201 = cnae(5L, "6201-5/01",
            "Desenvolvimento de programas de computador sob encomenda", "Tecnologia");
    private static final AtividadeEconomicaCnae CNAE_6202 = cnae(6L, "6202-3/00",
            "Desenvolvimento e licenciamento de programas customizaveis", "Tecnologia");

    @Mock
    private AtividadeEconomicaCnaeRepository repository;

    @InjectMocks
    private AtividadeEconomicaCnaeServiceImpl service;

    @Nested
    @DisplayName("buscarPorDescricao - cenarios positivos")
    class BuscarPorDescricaoPositivo {

        @Test
        @DisplayName("termo valido retorna os CNAEs encontrados convertidos em DTO")
        void termoValidoRetornaResultados() {
            when(repository.buscarPorDescricaoContendo("programas")).thenReturn(List.of(CNAE_6201, CNAE_6202));

            var resultado = service.buscarPorDescricao("programas");

            assertThat(resultado)
                    .extracting("id", "codigo", "descricao", "secao")
                    .containsExactly(
                            tuple(5L, "6201-5/01", CNAE_6201.getDescricao(), "Tecnologia"),
                            tuple(6L, "6202-3/00", CNAE_6202.getDescricao(), "Tecnologia"));
        }

        @Test
        @DisplayName("termo sem correspondencia retorna lista vazia, nao erro")
        void termoSemResultadoRetornaListaVazia() {
            when(repository.buscarPorDescricaoContendo("inexistente")).thenReturn(List.of());

            assertThat(service.buscarPorDescricao("inexistente")).isEmpty();
        }

        @ParameterizedTest(name = "termo [{0}]")
        @ValueSource(strings = {" programas", "programas ", " programas ", "   programas   ",
                "\tprogramas", "programas\n", "\r\n programas \t", " programas "})
        @DisplayName("espacos nas pontas sao removidos antes de consultar o repositorio")
        void espacosNasPontasSaoRemovidos(String termo) {
            when(repository.buscarPorDescricaoContendo("programas")).thenReturn(List.of(CNAE_6201, CNAE_6202));

            assertThat(service.buscarPorDescricao(termo)).hasSize(2);

            verify(repository).buscarPorDescricaoContendo("programas");
        }

        @Test
        @DisplayName("espacos internos sao mantidos")
        void espacosInternosSaoMantidos() {
            when(repository.buscarPorDescricaoContendo("de  programas")).thenReturn(List.of());

            service.buscarPorDescricao("  de  programas  ");

            verify(repository).buscarPorDescricaoContendo("de  programas");
        }

        @Test
        @DisplayName("termo com exatamente 180 caracteres e aceito (limite)")
        void termoNoLimiteDeTamanhoEhAceito() {
            var termo = "a".repeat(AtividadeEconomicaCnaeServiceImpl.TAMANHO_MAXIMO_TERMO);
            when(repository.buscarPorDescricaoContendo(termo)).thenReturn(List.of());

            assertThat(service.buscarPorDescricao(termo)).isEmpty();
        }

        @Test
        @DisplayName("limite de 180 vale para o termo sem os espacos das pontas")
        void limiteConsideraTermoSemEspacos() {
            var termo = "a".repeat(AtividadeEconomicaCnaeServiceImpl.TAMANHO_MAXIMO_TERMO);
            when(repository.buscarPorDescricaoContendo(termo)).thenReturn(List.of());

            assertThat(service.buscarPorDescricao("   " + termo + "   ")).isEmpty();

            verify(repository).buscarPorDescricaoContendo(termo);
        }

        @Test
        @DisplayName("curinga cercado de espacos tambem e normalizado")
        void curingaComEspacos() {
            when(repository.buscarPorDescricaoContendo("%")).thenReturn(List.of());

            assertThat(service.buscarPorDescricao(" % ")).isEmpty();

            verify(repository).buscarPorDescricaoContendo("%");
        }

        @Test
        @DisplayName("NBSP (U+00A0) nao e removido pelo strip: chega ao repositorio (limitacao conhecida)")
        void nbspNaoEhRemovido() {
            when(repository.buscarPorDescricaoContendo(" programas")).thenReturn(List.of());

            assertThat(service.buscarPorDescricao(" programas")).isEmpty();

            verify(repository).buscarPorDescricaoContendo(" programas");
        }

        @Test
        @DisplayName("termo com curingas do LIKE e repassado ao repositorio, que trata o escape")
        void termoComCuringasEhRepassado() {
            when(repository.buscarPorDescricaoContendo("%")).thenReturn(List.of());

            assertThat(service.buscarPorDescricao("%")).isEmpty();
        }
    }

    @Nested
    @DisplayName("buscarPorDescricao - cenarios negativos")
    class BuscarPorDescricaoNegativo {

        @Test
        @DisplayName("termo nulo lanca CNAE-VAL-0001 e nao consulta o banco")
        void termoNuloLancaErro() {
            assertThatThrownBy(() -> service.buscarPorDescricao(null))
                    .isInstanceOf(ApplicationException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CNAE_VAL_0001);

            verify(repository, never()).buscarPorDescricaoContendo(anyString());
        }

        @ParameterizedTest(name = "termo [{0}]")
        @ValueSource(strings = {"", " ", "   ", "\t", "\n", " \t\n ", " "})
        @DisplayName("termo vazio ou so com espacos lanca CNAE-VAL-0002 e nao consulta o banco")
        void termoEmBrancoLancaErro(String termo) {
            assertThatThrownBy(() -> service.buscarPorDescricao(termo))
                    .isInstanceOf(ApplicationException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CNAE_VAL_0002);

            verify(repository, never()).buscarPorDescricaoContendo(anyString());
        }

        @Test
        @DisplayName("termo acima de 180 caracteres lanca CNAE-VAL-0003 e nao consulta o banco")
        void termoAcimaDoLimiteLancaErro() {
            var termo = "a".repeat(AtividadeEconomicaCnaeServiceImpl.TAMANHO_MAXIMO_TERMO + 1);

            assertThatThrownBy(() -> service.buscarPorDescricao(termo))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining("180")
                    .extracting("errorCode").isEqualTo(ErrorCode.CNAE_VAL_0003);

            verify(repository, never()).buscarPorDescricaoContendo(anyString());
        }

        @Test
        @DisplayName("termo com 181 caracteres uteis continua rejeitado mesmo com espacos nas pontas")
        void termoAcimaDoLimiteComEspacosLancaErro() {
            var termo = "  " + "a".repeat(AtividadeEconomicaCnaeServiceImpl.TAMANHO_MAXIMO_TERMO + 1) + "  ";

            assertThatThrownBy(() -> service.buscarPorDescricao(termo))
                    .isInstanceOf(ApplicationException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CNAE_VAL_0003);

            verify(repository, never()).buscarPorDescricaoContendo(anyString());
        }

        @Test
        @DisplayName("falha do repositorio e propagada sem ser mascarada")
        void falhaDoRepositorioEhPropagada() {
            when(repository.buscarPorDescricaoContendo("programas")).thenThrow(new IllegalStateException("banco fora"));

            assertThatThrownBy(() -> service.buscarPorDescricao("programas"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("banco fora");
        }
    }

    @Nested
    @DisplayName("listarPaginado")
    class ListarPaginado {

        @Test
        @DisplayName("consulta o repositorio ordenando por codigo e monta a pagina")
        void listaPaginaOrdenadaPorCodigo() {
            var pageable = PageRequest.of(1, 2, Sort.by("codigo"));
            when(repository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(CNAE_6201, CNAE_6202), pageable, 8));

            var resultado = service.listarPaginado(1, 2);

            assertThat(resultado.conteudo()).extracting("codigo").containsExactly("6201-5/01", "6202-3/00");
            assertThat(resultado.pagina()).isEqualTo(1);
            assertThat(resultado.tamanho()).isEqualTo(2);
            assertThat(resultado.totalElementos()).isEqualTo(8);
            assertThat(resultado.totalPaginas()).isEqualTo(4);
        }

        @Test
        @DisplayName("tamanho no limite maximo (100) e aceito")
        void tamanhoNoLimiteEhAceito() {
            var pageable = PageRequest.of(0, 100, Sort.by("codigo"));
            when(repository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

            assertThat(service.listarPaginado(0, 100).conteudo()).isEmpty();
        }

        @Test
        @DisplayName("pagina negativa lanca GEN-VAL-0003")
        void paginaNegativaLancaErro() {
            assertThatThrownBy(() -> service.listarPaginado(-1, 20))
                    .isInstanceOf(ApplicationException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.GEN_VAL_0003);

            verify(repository, never()).findAll(any(Pageable.class));
        }

        @ParameterizedTest(name = "tamanho {0}")
        @ValueSource(ints = {-1, 0, 101})
        @DisplayName("tamanho fora de 1..100 lanca GEN-VAL-0004")
        void tamanhoInvalidoLancaErro(int tamanho) {
            assertThatThrownBy(() -> service.listarPaginado(0, tamanho))
                    .isInstanceOf(ApplicationException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.GEN_VAL_0004);

            verify(repository, never()).findAll(any(Pageable.class));
        }
    }

    private static AtividadeEconomicaCnae cnae(Long id, String codigo, String descricao, String secao) {
        return AtividadeEconomicaCnae.builder().id(id).codigo(codigo).descricao(descricao).secao(secao).build();
    }
}
