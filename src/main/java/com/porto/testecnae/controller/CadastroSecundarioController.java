package com.porto.testecnae.controller;

import com.porto.testecnae.dto.CadastroSecundarioRequest;
import com.porto.testecnae.dto.CadastroSecundarioResponse;
import com.porto.testecnae.dto.AtividadeEconomicaCnaeResponse;
import com.porto.testecnae.service.CadastroSecundarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cadastros-secundarios")
public class CadastroSecundarioController {

    private final CadastroSecundarioService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CadastroSecundarioResponse cadastrar(@Valid @RequestBody CadastroSecundarioRequest request) {
        return service.cadastrar(request);
    }

    @GetMapping("/validar-cnae")
    public AtividadeEconomicaCnaeResponse validarCnae(@RequestParam String codigoCnae) {
        return service.validarCnae(codigoCnae);
    }

    @GetMapping
    public List<CadastroSecundarioResponse> listarTodos() {
        return service.listarTodos();
    }
}
