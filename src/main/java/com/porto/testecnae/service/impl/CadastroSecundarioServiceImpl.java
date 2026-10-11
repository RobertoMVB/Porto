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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CadastroSecundarioServiceImpl implements CadastroSecundarioService {

    private final CadastroSecundarioRepository repository;
    private final BuscadorCnae buscadorCnae;

    // Sem @Transactional de proposito: apos a violacao da constraint unique, a sessao do Hibernate fica
    // inutilizavel e a confirmacao de duplicidade em salvar() falharia na mesma transacao
    @Override
    public CadastroSecundarioResponse cadastrar(CadastroSecundarioRequest request) {
        var cnae = buscarCnaeParaCadastro(request.codigoCnae());
        if (repository.existsByDocumento(request.documento())) {
            throw new ApplicationException(ErrorCode.CADSEC_NEG_0002);
        }

        var cadastro = CadastroSecundario.builder()
                .nomeFantasia(request.nomeFantasia())
                .documento(request.documento())
                .cnae(cnae)
                .build();

        return CadastroSecundarioResponse.fromEntity(salvar(cadastro));
    }

    @Override
    @Transactional(readOnly = true)
    public AtividadeEconomicaCnaeResponse validarCnae(String codigoCnae) {
        return AtividadeEconomicaCnaeResponse.fromEntity(buscadorCnae.buscarPorCodigoOuFalhar(codigoCnae));
    }

    // DTO montado dentro da transacao: nao depende do open-in-view (desligado)
    @Override
    @Transactional(readOnly = true)
    public List<CadastroSecundarioResponse> listarTodos() {
        return repository.listarComCnae()
                .stream()
                .map(CadastroSecundarioResponse::fromEntity)
                .toList();
    }

    /**
     * Duas requisicoes simultaneas com o mesmo CNPJ podem passar pelo {@code existsByDocumento}; a constraint
     * unique do banco barra a segunda. Traduz para o mesmo 409, sem mascarar outras violacoes de integridade.
     */
    private CadastroSecundario salvar(CadastroSecundario cadastro) {
        try {
            return repository.save(cadastro);
        } catch (DataIntegrityViolationException ex) {
            if (repository.existsByDocumento(cadastro.getDocumento())) {
                throw new ApplicationException(ErrorCode.CADSEC_NEG_0002);
            }
            throw ex;
        }
    }

    // 422 e nao 404: o recurso /cadastros-secundarios existe; o que falha e a referencia no corpo da requisicao
    private AtividadeEconomicaCnae buscarCnaeParaCadastro(String codigoCnae) {
        var codigo = BuscadorCnae.normalizarCodigo(codigoCnae);
        return buscadorCnae.buscarPorCodigo(codigo)
                .orElseThrow(() -> new ApplicationException(ErrorCode.CADSEC_NEG_0001,
                        "Cadastro rejeitado: o CNAE '%s' nao existe.".formatted(codigo)));
    }
}
