package com.porto.testecnae.repository;

import com.porto.testecnae.domain.AtividadeEconomicaCnae;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AtividadeEconomicaCnaeRepository extends JpaRepository<AtividadeEconomicaCnae, Long> {

    char CARACTERE_ESCAPE_LIKE = '\\';

    Optional<AtividadeEconomicaCnae> findByCodigo(String codigo);

    /**
     * Busca CNAEs cuja descricao contenha o termo em qualquer posicao, ignorando maiusculas e minusculas.
     * Os curingas do LIKE ({@code %} e {@code _}) informados no termo sao tratados como texto literal.
     */
    default List<AtividadeEconomicaCnae> buscarPorDescricaoContendo(String termo) {
        return buscarPorDescricao(escaparCuringasLike(termo));
    }

    /**
     * Espera o termo ja escapado. Prefira {@link #buscarPorDescricaoContendo(String)}.
     */
    @Query("""
            select c
            from AtividadeEconomicaCnae c
            where lower(c.descricao) like lower(concat('%', :termo, '%')) escape '\\'
            order by c.codigo
            """)
    List<AtividadeEconomicaCnae> buscarPorDescricao(@Param("termo") String termo);

    private static String escaparCuringasLike(String termo) {
        var escape = String.valueOf(CARACTERE_ESCAPE_LIKE);
        return termo
                .replace(escape, escape + escape)
                .replace("%", escape + "%")
                .replace("_", escape + "_");
    }
}
