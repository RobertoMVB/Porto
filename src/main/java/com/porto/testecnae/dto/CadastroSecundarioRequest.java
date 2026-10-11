package com.porto.testecnae.dto;

import com.porto.testecnae.validation.Cnpj;
import com.porto.testecnae.validation.CodigoCnae;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

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
     * Normaliza antes da validacao: remove espacos das pontas ({@link String#strip()}) e
     * coloca o documento em maiusculas (o CNPJ alfanumerico oficial usa apenas maiusculas).
     */
    public CadastroSecundarioRequest {
        nomeFantasia = strip(nomeFantasia);
        documento = documento == null ? null : documento.strip().toUpperCase(Locale.ROOT);
        codigoCnae = strip(codigoCnae);
    }

    private static String strip(String valor) {
        return valor == null ? null : valor.strip();
    }
}
