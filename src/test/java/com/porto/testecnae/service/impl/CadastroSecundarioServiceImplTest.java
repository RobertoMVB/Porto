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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
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
        @DisplayName("campos sao validados na borda (@Valid no controller): o service nao revalida nomeFantasia")
        void serviceNaoRevalidaCampos() {
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
    @DisplayName("cadastrar (item 6) - CNPJ duplicado")
    class CnpjDuplicado {

        private final CadastroSecundarioRequest request =
                new CadastroSecundarioRequest("Tech Porto", "12345678000195", "6201-5/01");

        @Test
        @DisplayName("CNPJ ainda nao cadastrado: grava normalmente")
        void cnpjNovoGrava() {
            when(buscadorCnae.buscarPorCodigo("6201-5/01")).thenReturn(Optional.of(CNAE_6201));
            when(repository.existsByDocumento("12345678000195")).thenReturn(false);
            when(repository.save(any(CadastroSecundario.class))).thenAnswer(inv -> inv.getArgument(0));

            service.cadastrar(request);

            verify(repository).save(any(CadastroSecundario.class));
        }

        @Test
        @DisplayName("CNPJ ja cadastrado: CADSEC-NEG-0002 e nada e gravado")
        void cnpjJaCadastrado() {
            when(buscadorCnae.buscarPorCodigo("6201-5/01")).thenReturn(Optional.of(CNAE_6201));
            when(repository.existsByDocumento("12345678000195")).thenReturn(true);

            assertThatThrownBy(() -> service.cadastrar(request))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessage(ErrorCode.CADSEC_NEG_0002.getMensagem())
                    .extracting("errorCode").isEqualTo(ErrorCode.CADSEC_NEG_0002);

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("mensagem do 409 nao ecoa o CNPJ")
        void mensagemNaoEcoaCnpj() {
            when(buscadorCnae.buscarPorCodigo("6201-5/01")).thenReturn(Optional.of(CNAE_6201));
            when(repository.existsByDocumento("12345678000195")).thenReturn(true);

            assertThatThrownBy(() -> service.cadastrar(request)).message().doesNotContain("12345678000195");
        }

        @Test
        @DisplayName("CNAE inexistente e verificado antes da duplicidade (422 prevalece)")
        void cnaeInexistenteAntesDaDuplicidade() {
            when(buscadorCnae.buscarPorCodigo("6201-5/01")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cadastrar(request))
                    .extracting("errorCode").isEqualTo(ErrorCode.CADSEC_NEG_0001);

            verify(repository, never()).existsByDocumento(any());
        }

        @Test
        @DisplayName("concorrencia: unique do banco barra o save e vira o mesmo CADSEC-NEG-0002")
        void concorrenciaViraConflito() {
            when(buscadorCnae.buscarPorCodigo("6201-5/01")).thenReturn(Optional.of(CNAE_6201));
            when(repository.existsByDocumento("12345678000195")).thenReturn(false, true);
            when(repository.save(any(CadastroSecundario.class)))
                    .thenThrow(new DataIntegrityViolationException("unique documento"));

            assertThatThrownBy(() -> service.cadastrar(request))
                    .isInstanceOf(ApplicationException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CADSEC_NEG_0002);
        }

        @Test
        @DisplayName("outra violacao de integridade (nao e duplicidade) nao e mascarada")
        void outraViolacaoPropagada() {
            when(buscadorCnae.buscarPorCodigo("6201-5/01")).thenReturn(Optional.of(CNAE_6201));
            when(repository.existsByDocumento("12345678000195")).thenReturn(false, false);
            when(repository.save(any(CadastroSecundario.class)))
                    .thenThrow(new DataIntegrityViolationException("outra constraint"));

            assertThatThrownBy(() -> service.cadastrar(request))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessage("outra constraint");
        }
    }

    @Nested
    @DisplayName("listarTodos (item 7)")
    class ListarTodos {

        @Test
        @DisplayName("usa listarComCnae (join fetch) e converte em DTO")
        void usaJoinFetchEConverte() {
            var cadastro = CadastroSecundario.builder()
                    .id(1L).nomeFantasia("Tech Porto").documento("12345678000199").cnae(CNAE_6201).build();
            when(repository.listarComCnae()).thenReturn(List.of(cadastro));

            assertThat(service.listarTodos())
                    .singleElement()
                    .satisfies(r -> {
                        assertThat(r.nomeFantasia()).isEqualTo("Tech Porto");
                        assertThat(r.cnae().codigo()).isEqualTo("6201-5/01");
                        assertThat(r.cnae().descricao()).isEqualTo(CNAE_6201.getDescricao());
                    });
        }

        @Test
        @DisplayName("nao usa findAll (que carregaria o CNAE de forma lazy, uma consulta por cadastro)")
        void naoUsaFindAll() {
            when(repository.listarComCnae()).thenReturn(List.of());

            service.listarTodos();

            verify(repository, never()).findAll();
        }

        @Test
        @DisplayName("sem cadastros: lista vazia")
        void listaVazia() {
            when(repository.listarComCnae()).thenReturn(List.of());

            assertThat(service.listarTodos()).isEmpty();
        }

        @Test
        @DisplayName("falha do repositorio e propagada sem ser mascarada")
        void falhaPropagada() {
            when(repository.listarComCnae()).thenThrow(new IllegalStateException("banco fora"));

            assertThatThrownBy(() -> service.listarTodos())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("banco fora");
        }
    }

    @Nested
    @DisplayName("configuracao transacional (item 7)")
    class Transacao {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"listarTodos", "validarCnae"})
        @DisplayName("metodos de leitura sao @Transactional(readOnly = true)")
        void leituraReadOnly(String metodo) {
            var transactional = metodoPorNome(metodo).getAnnotation(Transactional.class);

            assertThat(transactional).as("@Transactional em %s", metodo).isNotNull();
            assertThat(transactional.readOnly()).isTrue();
        }

        @Test
        @DisplayName("cadastrar NAO e @Transactional (a confirmacao de duplicidade apos violacao da unique depende disso)")
        void cadastrarSemTransacao() {
            assertThat(metodoPorNome("cadastrar").getAnnotation(Transactional.class)).isNull();
            assertThat(CadastroSecundarioServiceImpl.class.getAnnotation(Transactional.class)).isNull();
        }

        private static Method metodoPorNome(String nome) {
            return Arrays.stream(CadastroSecundarioServiceImpl.class.getDeclaredMethods())
                    .filter(m -> m.getName().equals(nome) && !m.isSynthetic())
                    .findFirst()
                    .orElseThrow();
        }
    }
}
