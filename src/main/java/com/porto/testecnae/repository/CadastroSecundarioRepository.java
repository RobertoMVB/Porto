package com.porto.testecnae.repository;

import com.porto.testecnae.domain.CadastroSecundario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CadastroSecundarioRepository extends JpaRepository<CadastroSecundario, Long> {

    boolean existsByDocumento(String documento);

    /**
     * Lista os cadastros com o CNAE ja carregado, em uma unica consulta (evita N+1 e acesso lazy fora de transacao).
     * Sem {@code order by} de proposito: a ordenacao custaria desempenho e nao e requisito.
     */
    @Query("select c from CadastroSecundario c join fetch c.cnae")
    List<CadastroSecundario> listarComCnae();
}
