package com.porto.testecnae.service;

import com.porto.testecnae.dto.AtividadeEconomicaCnaeResponse;
import com.porto.testecnae.dto.PaginaResponse;

import java.util.List;

public interface AtividadeEconomicaCnaeService {

    List<AtividadeEconomicaCnaeResponse> listarTodas();

    PaginaResponse<AtividadeEconomicaCnaeResponse> listarPaginado(int pagina, int tamanho);

    List<AtividadeEconomicaCnaeResponse> buscarPorDescricao(String termo);

    AtividadeEconomicaCnaeResponse buscarPorCodigo(String codigo);
}
