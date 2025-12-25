package br.com.pousda.pousada.usuarios.infra;

import br.com.pousda.pousada.usuarios.domain.UsuarioAcessoLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UsuarioAcessoLogRepository extends JpaRepository<UsuarioAcessoLog, Long> {

    List<UsuarioAcessoLog> findTop10ByUsuarioIdOrderByAcessoEmDesc(Long usuarioId);
}
