package com.porto.testecnae.dto;

import com.porto.testecnae.util.TextoNormalizador;
import com.porto.testecnae.validation.Cnpj;
import com.porto.testecnae.validation.CnpjValidator;
import com.porto.testecnae.validation.CodigoCnae;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo do {@code POST /api/cadastros-secundarios}.
 * <p>
 * A mensagem de cada constraint e o nome do {@link com.porto.testecnae.error.ErrorCode} correspondente.
 * A ordem dos componentes define a ordem da lista {@code errors} na resposta de validacao.
 */
public record CadastroSecundarioRequest(
        @NotBlank(message = "CADSEC_VAL_0001")
        @Size(max = 120, message = "CADSEC_VAL_0002")
        String nomeFantasia,

        @Cnpj
        String documento,

        @CodigoCnae
        String codigoCnae
) {

    /**
     * Normaliza antes da validacao: espacos ({@link TextoNormalizador}: NBSP, tabs e sequencias viram um espaco,
     * pontas removidas) e documento via {@link CnpjValidator#normalizar(String)} (sem mascara, em maiusculas).
     * O valor validado e o mesmo comparado na duplicidade e gravado.
     */
    public CadastroSecundarioRequest {
        nomeFantasia = TextoNormalizador.normalizarEspacos(nomeFantasia);
        documento = CnpjValidator.normalizar(documento);
        codigoCnae = TextoNormalizador.normalizarEspacos(codigoCnae);
    }
}
