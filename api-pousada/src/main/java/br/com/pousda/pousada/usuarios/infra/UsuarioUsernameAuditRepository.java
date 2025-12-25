package br.com.pousda.pousada.usuarios.infra;


import br.com.pousda.pousada.usuarios.domain.UsuarioUsernameAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UsuarioUsernameAuditRepository extends JpaRepository<UsuarioUsernameAudit, Long> {

    long countByUsuarioIdAndChangedAtAfter(Long usuarioId, LocalDateTime after);

    Optional<UsuarioUsernameAudit> findTopByUsuarioIdOrderByChangedAtDesc(Long usuarioId);
}
