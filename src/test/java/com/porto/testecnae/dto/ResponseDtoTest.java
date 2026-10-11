package com.porto.testecnae.dto;

import com.porto.testecnae.domain.AtividadeEconomicaCnae;
import com.porto.testecnae.domain.CadastroSecundario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Conversoes entidade -> DTO (item 8: antes testadas so de forma indireta).
 */
class ResponseDtoTest {

    private static final AtividadeEconomicaCnae CNAE = AtividadeEconomicaCnae.builder()
            .id(5L).codigo("6201-5/01").descricao("Desenvolvimento de programas de computador sob encomenda")
            .secao("Tecnologia").build();
    private static final AtividadeEconomicaCnae CNAE_7311 = AtividadeEconomicaCnae.builder()
            .id(8L).codigo("7311-4/00").descricao("Agencias de publicidade").secao("Comunicacao").build();

    @Nested
    @DisplayName("AtividadeEconomicaCnaeResponse.fromEntity")
    class CnaeResponse {

        @Test
        @DisplayName("copia os 4 campos")
        void copiaCampos() {
            assertThat(AtividadeEconomicaCnaeResponse.fromEntity(CNAE)).isEqualTo(new AtividadeEconomicaCnaeResponse(
                    5L, "6201-5/01", "Desenvolvimento de programas de computador sob encomenda", "Tecnologia"));
        }

        @Test
        @DisplayName("entidade ainda nao persistida: id nulo")
        void idNulo() {
            var novo = AtividadeEconomicaCnae.builder().codigo("0001-0/01").descricao("x").secao("y").build();

            assertThat(AtividadeEconomicaCnaeResponse.fromEntity(novo).id()).isNull();
        }
    }

    @Nested
    @DisplayName("CadastroSecundarioResponse.fromEntity")
    class CadastroResponse {

        @Test
        @DisplayName("copia os campos e converte o CNAE aninhado")
        void copiaCampos() {
            var cadastro = CadastroSecundario.builder()
                    .id(1L).nomeFantasia("Tech Porto").documento("12345678000195").cnae(CNAE).build();

            var response = CadastroSecundarioResponse.fromEntity(cadastro);

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.nomeFantasia()).isEqualTo("Tech Porto");
            assertThat(response.documento()).isEqualTo("12345678000195");
            assertThat(response.cnae()).isEqualTo(AtividadeEconomicaCnaeResponse.fromEntity(CNAE));
        }
    }

    @Nested
    @DisplayName("PaginaResponse.fromPage")
    class Pagina {

        @Test
        @DisplayName("converte o conteudo e copia os metadados da pagina")
        void converte() {
            // ultima pagina de 8 itens em paginas de 3: 2 itens
            Page<AtividadeEconomicaCnae> page = new PageImpl<>(List.of(CNAE, CNAE_7311), PageRequest.of(2, 3), 8);

            var resultado = PaginaResponse.fromPage(page, AtividadeEconomicaCnaeResponse::fromEntity);

            assertThat(resultado.conteudo()).containsExactly(
                    AtividadeEconomicaCnaeResponse.fromEntity(CNAE), AtividadeEconomicaCnaeResponse.fromEntity(CNAE_7311));
            assertThat(resultado.pagina()).isEqualTo(2);
            assertThat(resultado.tamanho()).isEqualTo(3);
            assertThat(resultado.totalElementos()).isEqualTo(8);
            assertThat(resultado.totalPaginas()).isEqualTo(3);
        }

        @Test
        @DisplayName("pagina vazia: conteudo vazio, totais zerados")
        void vazia() {
            Page<AtividadeEconomicaCnae> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

            var resultado = PaginaResponse.fromPage(page, AtividadeEconomicaCnaeResponse::fromEntity);

            assertThat(resultado.conteudo()).isEmpty();
            assertThat(resultado.totalElementos()).isZero();
            assertThat(resultado.totalPaginas()).isZero();
        }

        @Test
        @DisplayName("pagina alem do fim: conteudo vazio, mas totais preservados")
        void alemDoFim() {
            Page<AtividadeEconomicaCnae> page = new PageImpl<>(List.of(), PageRequest.of(10, 3), 8);

            var resultado = PaginaResponse.fromPage(page, AtividadeEconomicaCnaeResponse::fromEntity);

            assertThat(resultado.conteudo()).isEmpty();
            assertThat(resultado.pagina()).isEqualTo(10);
            assertThat(resultado.totalElementos()).isEqualTo(8);
            assertThat(resultado.totalPaginas()).isEqualTo(3);
        }

        @Test
        @DisplayName("aplica o mapper informado a cada item, preservando a ordem")
        void aplicaMapper() {
            Page<String> page = new PageImpl<>(List.of("b", "a"), PageRequest.of(0, 2), 2);

            assertThat(PaginaResponse.fromPage(page, String::toUpperCase).conteudo()).containsExactly("B", "A");
        }
    }
}
