package com.porto.testecnae.service.impl;

import com.porto.testecnae.domain.AtividadeEconomicaCnae;
import com.porto.testecnae.domain.CadastroSecundario;
import com.porto.testecnae.dto.CadastroSecundarioRequest;
import com.porto.testecnae.error.ApplicationException;
import com.porto.testecnae.error.ErrorCode;
import com.porto.testecnae.repository.CadastroSecundarioRepository;
import com.porto.testecnae.service.BuscadorCnae;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CadastroSecundarioServiceImplTest {

    private static final AtividadeEconomicaCnae CNAE_6201 = AtividadeEconomicaCnae.builder()
            .id(5L).codigo("6201-5/01").descricao("Desenvolvimento de programas de computador sob encomenda")
            .secao("Tecnologia").build();

    @Mock
    private CadastroSecundarioRepository repository;

    @Mock
    private BuscadorCnae buscadorCnae;

    @InjectMocks
    private CadastroSecundarioServiceImpl service;

    @Nested
    @DisplayName("validarCnae (item 4)")
    class ValidarCnae {

        @Test
        @DisplayName("CNAE existente e devolvido como DTO")
        void existente() {
            when(buscadorCnae.buscarPorCodigoOuFalhar("6201-5/01")).thenReturn(CNAE_6201);

            var resultado = service.validarCnae("6201-5/01");

            assertThat(resultado.codigo()).isEqualTo("6201-5/01");
            assertThat(resultado.id()).isEqualTo(5L);
        }

        @Test
        @DisplayName("CNAE inexistente: CNAE-NEG-0001 propagado (antes devolvia o 1o CNAE)")
        void inexistente() {
            when(buscadorCnae.buscarPorCodigoOuFalhar("9999-9/99"))
                    .thenThrow(new ApplicationException(ErrorCode.CNAE_NEG_0001));

            assertThatThrownBy(() -> service.validarCnae("9999-9/99"))
                    .isInstanceOf(ApplicationException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CNAE_NEG_0001);
        }

        @Test
        @DisplayName("erro de validacao do codigo e propagado")
        void validacaoPropagada() {
            when(buscadorCnae.buscarPorCodigoOuFalhar(""))
                    .thenThrow(new ApplicationException(ErrorCode.CNAE_VAL_0005));

            assertThatThrownBy(() -> service.validarCnae(""))
                    .isInstanceOf(ApplicationException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CNAE_VAL_0005);
        }
    }

    @Nested
    @DisplayName("cadastrar (item 5) - cenarios positivos")
    class CadastrarPositivo {

        @Test
        @DisplayName("CNAE existente: grava o cadastro vinculado ao CNAE e devolve o DTO")
        void cnaeExistenteGrava() {
            when(buscadorCnae.buscarPorCodigo("6201-5/01")).thenReturn(Optional.of(CNAE_6201));
            when(repository.save(any(CadastroSecundario.class))).thenAnswer(inv -> {
                CadastroSecundario c = inv.getArgument(0);
                c.setId(1L);
                return c;
            });

            var resultado = service.cadastrar(new CadastroSecundarioRequest("Tech Porto", "12345678000199", "6201-5/01"));

            var captor = ArgumentCaptor.forClass(CadastroSecundario.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getNomeFantasia()).isEqualTo("Tech Porto");
            assertThat(captor.getValue().getDocumento()).isEqualTo("12345678000199");
            assertThat(captor.getValue().getCnae()).isSameAs(CNAE_6201);

            assertThat(resultado.id()).isEqualTo(1L);
            assertThat(resultado.nomeFantasia()).isEqualTo("Tech Porto");
            assertThat(resultado.cnae().codigo()).isEqualTo("6201-5/01");
        }

        @Test
        @DisplayName("codigo com espacos nas pontas e normalizado antes da busca")
        void codigoNormalizado() {
            when(buscadorCnae.buscarPorCodigo("6201-5/01")).thenReturn(Optional.of(CNAE_6201));
            when(repository.save(any(CadastroSecundario.class))).thenAnswer(inv -> inv.getArgument(0));

            service.cadastrar(new CadastroSecundarioRequest("Tech Porto", "12345678000199", "  6201-5/01 "));

            verify(buscadorCnae).buscarPorCodigo("6201-5/01");
        }
    }

    @Nested
    @DisplayName("cadastrar (item 5) - cenarios negativos")
    class CadastrarNegativo {

        @Test
        @DisplayName("CNAE inexistente: CADSEC-NEG-0001 e nada e gravado (antes gravava ligado ao 1o CNAE)")
        void cnaeInexistenteNaoGrava() {
            when(buscadorCnae.buscarPorCodigo("9999-9/99")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cadastrar(
                    new CadastroSecundarioRequest("Fantasma", "00000000000000", "9999-9/99")))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessage("Cadastro rejeitado: o CNAE '9999-9/99' nao existe.")
                    .extracting("errorCode").isEqualTo(ErrorCode.CADSEC_NEG_0001);

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("codigoCnae ausente no JSON (nulo): CNAE-VAL-0004, sem consultar nem gravar")
        void codigoNulo() {
            assertErroSemGravar(new CadastroSecundarioRequest("Tech Porto", "12345678000199", null),
                    ErrorCode.CNAE_VAL_0004);
        }

        @ParameterizedTest(name = "codigo [{0}]")
        @ValueSource(strings = {"", "   ", "\t"})
        @DisplayName("codigoCnae vazio: CNAE-VAL-0005, sem consultar nem gravar")
        void codigoVazio(String codigo) {
            assertErroSemGravar(new CadastroSecundarioRequest("Tech Porto", "12345678000199", codigo),
                    ErrorCode.CNAE_VAL_0005);
        }

        @ParameterizedTest(name = "codigo [{0}]")
        @ValueSource(strings = {"6201501", "6201-5/1", "abc", "%"})
        @DisplayName("codigoCnae malformado: CNAE-VAL-0006, sem consultar nem gravar")
        void codigoMalformado(String codigo) {
            assertErroSemGravar(new CadastroSecundarioRequest("Tech Porto", "12345678000199", codigo),
                    ErrorCode.CNAE_VAL_0006);
        }

        @Test
        @DisplayName("FORA DO ESCOPO (item 6): nomeFantasia nulo com CNAE valido ainda chega ao save")
        void item6NomeFantasiaNuloAindaNaoValidado() {
            when(buscadorCnae.buscarPorCodigo("6201-5/01")).thenReturn(Optional.of(CNAE_6201));
            when(repository.save(any(CadastroSecundario.class))).thenAnswer(inv -> inv.getArgument(0));

            service.cadastrar(new CadastroSecundarioRequest(null, "12345678000199", "6201-5/01"));

            verify(repository).save(any(CadastroSecundario.class));
        }

        private void assertErroSemGravar(CadastroSecundarioRequest request, ErrorCode esperado) {
            assertThatThrownBy(() -> service.cadastrar(request))
                    .isInstanceOf(ApplicationException.class)
                    .extracting("errorCode").isEqualTo(esperado);

            verifyNoInteractions(buscadorCnae, repository);
        }
    }

    @Nested
    @DisplayName("listarTodos (inalterado)")
    class ListarTodos {

        @Test
        @DisplayName("converte os cadastros em DTO")
        void converteEmDto() {
            var cadastro = CadastroSecundario.builder()
                    .id(1L).nomeFantasia("Tech Porto").documento("12345678000199").cnae(CNAE_6201).build();
            when(repository.findAll()).thenReturn(List.of(cadastro));

            assertThat(service.listarTodos())
                    .singleElement()
                    .satisfies(r -> {
                        assertThat(r.nomeFantasia()).isEqualTo("Tech Porto");
                        assertThat(r.cnae().codigo()).isEqualTo("6201-5/01");
                    });
        }
    }
}
