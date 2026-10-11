package com.porto.testecnae.service.impl;

import com.porto.testecnae.dto.AtividadeEconomicaCnaeResponse;
import com.porto.testecnae.dto.PaginaResponse;
import com.porto.testecnae.error.ApplicationException;
import com.porto.testecnae.error.ErrorCode;
import com.porto.testecnae.repository.AtividadeEconomicaCnaeRepository;
import com.porto.testecnae.service.AtividadeEconomicaCnaeService;
import com.porto.testecnae.service.BuscadorCnae;
import com.porto.testecnae.util.TextoNormalizador;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AtividadeEconomicaCnaeServiceImpl implements AtividadeEconomicaCnaeService {

    /** Mesmo tamanho da coluna {@code descricao}: um termo maior nunca encontraria resultado. */
    static final int TAMANHO_MAXIMO_TERMO = 180;
    static final int TAMANHO_MAXIMO_PAGINA = 100;

    private final AtividadeEconomicaCnaeRepository repository;
    private final BuscadorCnae buscadorCnae;

    @Override
    public List<AtividadeEconomicaCnaeResponse> listarTodas() {
        return repository.findAll()
                .stream()
                .map(AtividadeEconomicaCnaeResponse::fromEntity)
                .toList();
    }

    @Override
    public PaginaResponse<AtividadeEconomicaCnaeResponse> listarPaginado(int pagina, int tamanho) {
        if (pagina < 0) {
            throw new ApplicationException(ErrorCode.GEN_VAL_0003,
                    "O numero da pagina deve ser maior ou igual a 0.");
        }
        if (tamanho < 1 || tamanho > TAMANHO_MAXIMO_PAGINA) {
            throw new ApplicationException(ErrorCode.GEN_VAL_0004,
                    "O tamanho da pagina deve estar entre 1 e %d.".formatted(TAMANHO_MAXIMO_PAGINA));
        }

        var page = repository.findAll(PageRequest.of(pagina, tamanho, Sort.by("codigo")));
        return PaginaResponse.fromPage(page, AtividadeEconomicaCnaeResponse::fromEntity);
    }

    @Override
    public List<AtividadeEconomicaCnaeResponse> buscarPorDescricao(String termo) {
        var termoNormalizado = normalizarTermo(termo);

        return repository.buscarPorDescricaoContendo(termoNormalizado)
                .stream()
                .map(AtividadeEconomicaCnaeResponse::fromEntity)
                .toList();
    }

    @Override
    public AtividadeEconomicaCnaeResponse buscarPorCodigo(String codigo) {
        return AtividadeEconomicaCnaeResponse.fromEntity(buscadorCnae.buscarPorCodigoOuFalhar(codigo));
    }

    /**
     * Normaliza os espacos ({@link TextoNormalizador}: NBSP e tabs viram espaco, sequencias viram um, pontas
     * removidas) e valida o termo. Sem isso, " Cultivo", "de  programas" ou um NBSP copiado da web deixariam
     * de encontrar CNAEs. O limite de tamanho vale para o termo ja normalizado.
     */
    private static String normalizarTermo(String termo) {
        if (termo == null) {
            throw new ApplicationException(ErrorCode.CNAE_VAL_0001);
        }
        var termoNormalizado = TextoNormalizador.normalizarEspacos(termo);
        if (termoNormalizado.isEmpty()) {
            throw new ApplicationException(ErrorCode.CNAE_VAL_0002);
        }
        if (termoNormalizado.length() > TAMANHO_MAXIMO_TERMO) {
            throw new ApplicationException(ErrorCode.CNAE_VAL_0003,
                    "O termo de busca deve ter no maximo %d caracteres.".formatted(TAMANHO_MAXIMO_TERMO));
        }
        return termoNormalizado;
    }
}
