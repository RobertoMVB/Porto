package com.porto.testecnae.service;

import com.porto.testecnae.domain.AtividadeEconomicaCnae;
import com.porto.testecnae.error.ApplicationException;
import com.porto.testecnae.error.ErrorCode;
import com.porto.testecnae.repository.AtividadeEconomicaCnaeRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuscadorCnaeTest {

    private static final AtividadeEconomicaCnae CNAE_6201 = AtividadeEconomicaCnae.builder()
            .id(5L).codigo("6201-5/01").descricao("Desenvolvimento de programas de computador sob encomenda")
            .secao("Tecnologia").build();

    @Mock
    private AtividadeEconomicaCnaeRepository repository;

    @InjectMocks
    private BuscadorCnae buscador;

    @Nested
    @DisplayName("cenarios positivos")
    class Positivo {

        @Test
        @DisplayName("buscarPorCodigoOuFalhar devolve o CNAE cadastrado")
        void ouFalharDevolveCnae() {
            when(repository.findByCodigo("6201-5/01")).thenReturn(Optional.of(CNAE_6201));

            assertThat(buscador.buscarPorCodigoOuFalhar("6201-5/01")).isSameAs(CNAE_6201);
        }

        @Test
        @DisplayName("buscarPorCodigo devolve Optional com o CNAE cadastrado")
        void buscarDevolveOptionalPreenchido() {
            when(repository.findByCodigo("6201-5/01")).thenReturn(Optional.of(CNAE_6201));

            assertThat(buscador.buscarPorCodigo("6201-5/01")).containsSame(CNAE_6201);
        }

        @Test
        @DisplayName("buscarPorCodigo devolve Optional vazio para codigo valido nao cadastrado")
        void buscarDevolveOptionalVazio() {
            when(repository.findByCodigo("9999-9/99")).thenReturn(Optional.empty());

            assertThat(buscador.buscarPorCodigo("9999-9/99")).isEmpty();
        }

        @ParameterizedTest(name = "codigo [{0}]")
        @ValueSource(strings = {" 6201-5/01", "6201-5/01 ", "  6201-5/01  ", "\t6201-5/01\n", "\u20036201-5/01\u2003",
                "\u00A06201-5/01\u00A0", "\u200B6201-5/01\uFEFF"})
        @DisplayName("espacos nas pontas sao removidos antes de consultar")
        void espacosNasPontasSaoRemovidos(String codigo) {
            when(repository.findByCodigo("6201-5/01")).thenReturn(Optional.of(CNAE_6201));

            assertThat(buscador.buscarPorCodigoOuFalhar(codigo)).isSameAs(CNAE_6201);

            verify(repository).findByCodigo("6201-5/01");
        }

        @ParameterizedTest(name = "codigo [{0}]")
        @ValueSource(strings = {"0111-3/01", "6202-3/00", "0000-0/00", "9999-9/99"})
        @DisplayName("normalizarCodigo aceita qualquer codigo no formato NNNN-N/NN")
        void formatoValido(String codigo) {
            assertThat(BuscadorCnae.normalizarCodigo(codigo)).isEqualTo(codigo);
        }
    }

    @Nested
    @DisplayName("cenarios negativos")
    class Negativo {

        @Test
        @DisplayName("codigo nulo lanca CNAE-VAL-0004 e nao consulta o banco")
        void codigoNulo() {
            assertErro(() -> buscador.buscarPorCodigoOuFalhar(null), ErrorCode.CNAE_VAL_0004);
            assertErro(() -> buscador.buscarPorCodigo(null), ErrorCode.CNAE_VAL_0004);

            verifyNoInteractions(repository);
        }

        @ParameterizedTest(name = "codigo [{0}]")
        @ValueSource(strings = {"", " ", "   ", "\t", "\n", "\u2003", "\u00A0", "\u200B"})
        @DisplayName("codigo vazio ou so espacos lanca CNAE-VAL-0005 e nao consulta o banco")
        void codigoVazio(String codigo) {
            assertErro(() -> buscador.buscarPorCodigoOuFalhar(codigo), ErrorCode.CNAE_VAL_0005);
            assertErro(() -> buscador.buscarPorCodigo(codigo), ErrorCode.CNAE_VAL_0005);

            verifyNoInteractions(repository);
        }

        @ParameterizedTest(name = "codigo [{0}]")
        @ValueSource(strings = {
                "6201501",          // sem pontuacao (nao e completado)
                "6201-501",         // sem barra
                "62015/01",         // sem hifen
                "6201-5/1",         // digito faltando
                "620-15/01",        // separador fora de posicao
                "62011-5/01",       // digito sobrando
                "6201-5/01/02",     // sufixo extra
                "x6201-5/01",       // prefixo extra
                "6201-5/01x",       // sufixo extra
                "62O1-5/01",        // letra O no lugar de zero
                "6201 5/01",        // espaco no lugar do hifen
                "6201-5/\u00A001",  // NBSP interno (normalizado para espaco: continua invalido)
                "6201-5\\01",       // barra invertida
                "6201-5/0_",        // curinga do LIKE
                "%",                // curinga do LIKE
                "abc",
                "６２０１-５/０１", // digitos de largura total
                "6201-5/01\n6201-5/01" // quebra de linha interna
        })
        @DisplayName("codigo fora do formato NNNN-N/NN lanca CNAE-VAL-0006 e nao consulta o banco")
        void formatoInvalido(String codigo) {
            assertErro(() -> buscador.buscarPorCodigoOuFalhar(codigo), ErrorCode.CNAE_VAL_0006);
            assertErro(() -> buscador.buscarPorCodigo(codigo), ErrorCode.CNAE_VAL_0006);

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("formato invalido nao ecoa o valor recebido na mensagem")
        void formatoInvalidoNaoEcoaValor() {
            assertThatThrownBy(() -> buscador.buscarPorCodigoOuFalhar("<script>"))
                    .hasMessage(ErrorCode.CNAE_VAL_0006.getMensagem());
        }

        @Test
        @DisplayName("codigo valido nao cadastrado lanca CNAE-NEG-0001 com o codigo normalizado na mensagem")
        void naoEncontrado() {
            when(repository.findByCodigo("9999-9/99")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> buscador.buscarPorCodigoOuFalhar(" 9999-9/99 "))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessage("CNAE '9999-9/99' nao encontrado.")
                    .extracting("errorCode").isEqualTo(ErrorCode.CNAE_NEG_0001);
        }

        @Test
        @DisplayName("nao encontrado nunca cai no fallback antigo (findAll().getFirst())")
        void semFallbackParaPrimeiroCnae() {
            when(repository.findByCodigo("9999-9/99")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> buscador.buscarPorCodigoOuFalhar("9999-9/99"))
                    .isInstanceOf(ApplicationException.class);

            verify(repository, never()).findAll();
        }

        @Test
        @DisplayName("tabela vazia resulta em CNAE-NEG-0001, nao em 500 (NoSuchElementException)")
        void tabelaVazia() {
            when(repository.findByCodigo(anyString())).thenReturn(Optional.empty());

            assertErro(() -> buscador.buscarPorCodigoOuFalhar("6201-5/01"), ErrorCode.CNAE_NEG_0001);
        }

        @Test
        @DisplayName("falha do repositorio e propagada sem ser mascarada")
        void falhaDoRepositorio() {
            when(repository.findByCodigo("6201-5/01")).thenThrow(new IllegalStateException("banco fora"));

            assertThatThrownBy(() -> buscador.buscarPorCodigoOuFalhar("6201-5/01"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("banco fora");
        }
    }

    private static void assertErro(ThrowingCallable chamada, ErrorCode esperado) {
        assertThatThrownBy(chamada)
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCode").isEqualTo(esperado);
    }
}
