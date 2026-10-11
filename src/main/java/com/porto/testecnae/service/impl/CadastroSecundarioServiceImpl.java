package com.porto.testecnae.service.impl;

import com.porto.testecnae.domain.CadastroSecundario;
import com.porto.testecnae.domain.AtividadeEconomicaCnae;
import com.porto.testecnae.dto.AtividadeEconomicaCnaeResponse;
import com.porto.testecnae.dto.CadastroSecundarioRequest;
import com.porto.testecnae.dto.CadastroSecundarioResponse;
import com.porto.testecnae.error.ApplicationException;
import com.porto.testecnae.error.ErrorCode;
import com.porto.testecnae.repository.CadastroSecundarioRepository;
import com.porto.testecnae.service.BuscadorCnae;
import com.porto.testecnae.service.CadastroSecundarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CadastroSecundarioServiceImpl implements CadastroSecundarioService {

    private final CadastroSecundarioRepository repository;
    private final BuscadorCnae buscadorCnae;

    @Override
    public CadastroSecundarioResponse cadastrar(CadastroSecundarioRequest request) {
        var cnae = buscarCnaeParaCadastro(request.codigoCnae());

        var cadastro = CadastroSecundario.builder()
                .nomeFantasia(request.nomeFantasia())
                .documento(request.documento())
                .cnae(cnae)
                .build();

        return CadastroSecundarioResponse.fromEntity(repository.save(cadastro));
    }

    @Override
    public AtividadeEconomicaCnaeResponse validarCnae(String codigoCnae) {
        return AtividadeEconomicaCnaeResponse.fromEntity(buscadorCnae.buscarPorCodigoOuFalhar(codigoCnae));
    }

    @Override
    public List<CadastroSecundarioResponse> listarTodos() {
        return repository.findAll()
                .stream()
                .map(CadastroSecundarioResponse::fromEntity)
                .toList();
    }

    // 422 e nao 404: o recurso /cadastros-secundarios existe; o que falha e a referencia no corpo da requisicao
    private AtividadeEconomicaCnae buscarCnaeParaCadastro(String codigoCnae) {
        var codigo = BuscadorCnae.normalizarCodigo(codigoCnae);
        return buscadorCnae.buscarPorCodigo(codigo)
                .orElseThrow(() -> new ApplicationException(ErrorCode.CADSEC_NEG_0001,
                        "Cadastro rejeitado: o CNAE '%s' nao existe.".formatted(codigo)));
    }
}
