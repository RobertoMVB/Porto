package com.porto.testecnae.util;

import java.util.regex.Pattern;

/**
 * Normalizacao de espacos usada em toda entrada de texto (termo de busca, codigo CNAE, campos do cadastro).
 * <p>
 * {@link String#strip()} e {@link String#isBlank()} nao consideram o NBSP (U+00A0), comum em texto copiado de
 * paginas web, Excel ou PDF. Sem esta normalizacao, um nome so com NBSP passava pelo {@code @NotBlank}.
 */
public final class TextoNormalizador {

    /** Caracteres de largura zero: invisiveis, nao sao espaco nem conteudo. */
    private static final Pattern INVISIVEIS = Pattern.compile("[\\u200B\\u200C\\u200D\\uFEFF]");

    /** Qualquer sequencia de espacos: ASCII (\s: espaco, tab, quebras de linha) e Unicode (\p{Z}: NBSP, U+2007, U+202F...). */
    private static final Pattern ESPACOS = Pattern.compile("[\\s\\p{Z}]+");

    private TextoNormalizador() {
    }

    /**
     * Remove invisiveis, converte qualquer sequencia de espacos em um espaco comum e remove as pontas.
     *
     * @return o texto normalizado ({@code ""} se so havia espacos), ou {@code null} se a entrada for nula
     */
    public static String normalizarEspacos(String texto) {
        if (texto == null) {
            return null;
        }
        var semInvisiveis = INVISIVEIS.matcher(texto).replaceAll("");
        return ESPACOS.matcher(semInvisiveis).replaceAll(" ").strip();
    }
}
