package com.porto.testecnae.repository;

import com.porto.testecnae.domain.CadastroSecundario;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CadastroSecundarioRepository extends JpaRepository<CadastroSecundario, Long> {

    boolean existsByDocumento(String documento);
}
