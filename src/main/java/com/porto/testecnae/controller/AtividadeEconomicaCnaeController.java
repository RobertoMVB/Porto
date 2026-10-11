package com.porto.testecnae.controller;

import com.porto.testecnae.dto.AtividadeEconomicaCnaeResponse;
import com.porto.testecnae.dto.PaginaResponse;
import com.porto.testecnae.service.AtividadeEconomicaCnaeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cnaes")
public class AtividadeEconomicaCnaeController {

    private final AtividadeEconomicaCnaeService service;

    @GetMapping
    public List<AtividadeEconomicaCnaeResponse> listarTodas() {
        return service.listarTodas();
    }

    @GetMapping("/paginado")
    public PaginaResponse<AtividadeEconomicaCnaeResponse> listarPaginado(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listarPaginado(page, size);
    }

    @GetMapping("/buscar")
    public List<AtividadeEconomicaCnaeResponse> buscarPorDescricao(@RequestParam String termo) {
        return service.buscarPorDescricao(termo);
    }

    @GetMapping("/codigo")
    public AtividadeEconomicaCnaeResponse buscarPorCodigo(@RequestParam String codigo) {
        return service.buscarPorCodigo(codigo);
    }
}
