package com.porto.testecnae.service;

import com.porto.testecnae.domain.AtividadeEconomicaCnae;
import com.porto.testecnae.error.ApplicationException;
import com.porto.testecnae.error.ErrorCode;
import com.porto.testecnae.repository.AtividadeEconomicaCnaeRepository;
import com.porto.testecnae.util.TextoNormalizador;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Ponto unico de busca de CNAE por codigo: normaliza, valida o formato e consulta o repositorio.
 * Reutilizado pela consulta por codigo, pela validacao e pelo cadastro secundario.
 */
@Component
@RequiredArgsConstructor
public class BuscadorCnae {

    /** Formato da subclasse CNAE, ex.: {@code 6201-5/01}. {@code \d} casa apenas digitos ASCII. */
    static final Pattern FORMATO_CODIGO = Pattern.compile("\\d{4}-\\d/\\d{2}");

    private final AtividadeEconomicaCnaeRepository repository;

    /**
     * @return o CNAE, ou vazio se o codigo e valido mas nao esta cadastrado
     * @throws ApplicationException {@code CNAE-VAL-0004/0005/0006} se o codigo for nulo, vazio ou malformado
     */
    public Optional<AtividadeEconomicaCnae> buscarPorCodigo(String codigo) {
        return repository.findByCodigo(normalizarCodigo(codigo));
    }

    /**
     * @throws ApplicationException {@code CNAE-NEG-0001} se o codigo nao estiver cadastrado,
     *                              alem dos erros de validacao de {@link #buscarPorCodigo(String)}
     */
    public AtividadeEconomicaCnae buscarPorCodigoOuFalhar(String codigo) {
        var codigoNormalizado = normalizarCodigo(codigo);
        return repository.findByCodigo(codigoNormalizado)
                .orElseThrow(() -> new ApplicationException(ErrorCode.CNAE_NEG_0001,
                        "CNAE '%s' nao encontrado.".formatted(codigoNormalizado)));
    }

    /**
     * Normaliza os espacos ({@link TextoNormalizador}, mesmo criterio da busca por descricao) e valida o formato.
     * Codigo sem pontuacao (ex.: {@code 6201501}) nao e completado: cai em formato invalido.
     */
    public static String normalizarCodigo(String codigo) {
        if (codigo == null) {
            throw new ApplicationException(ErrorCode.CNAE_VAL_0004);
        }
        var codigoNormalizado = TextoNormalizador.normalizarEspacos(codigo);
        if (codigoNormalizado.isEmpty()) {
            throw new ApplicationException(ErrorCode.CNAE_VAL_0005);
        }
        // O valor malformado nao e ecoado: e entrada livre (tamanho e caracteres arbitrarios)
        if (!FORMATO_CODIGO.matcher(codigoNormalizado).matches()) {
            throw new ApplicationException(ErrorCode.CNAE_VAL_0006);
        }
        return codigoNormalizado;
    }
}
