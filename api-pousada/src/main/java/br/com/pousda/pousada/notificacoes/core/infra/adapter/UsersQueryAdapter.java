// br/com/pousda/pousada/notificacoes/core/infra/adapter/UsersQueryAdapter.java
package br.com.pousda.pousada.notificacoes.core.infra.adapter;

import br.com.pousda.pousada.notificacoes.application.UsersQueryPort;
import br.com.pousda.pousada.security.AuthPrincipal;
import br.com.pousda.pousada.usuarios.domain.Usuario;
import br.com.pousda.pousada.usuarios.infra.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@Primary // <-- garante que esta é a escolhida
@RequiredArgsConstructor
public class UsersQueryAdapter implements UsersQueryPort {

    private static final Logger log = LoggerFactory.getLogger(UsersQueryAdapter.class);
    private final UsuarioRepository repo;

    @Override
    public long currentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) return 0L;

            Object p = auth.getPrincipal();

            if (p instanceof AuthPrincipal) {
                Long id = ((AuthPrincipal) p).getId();
                return id != null ? id : 0L;
            }
            if (p instanceof Usuario) {
                Usuario u = (Usuario) p;
                return u.getId() != null ? u.getId() : 0L;
            }
            if (p instanceof String) {
                String login = ((String) p).trim();
                if (!login.isEmpty()) {
                    return repo.findByLogin(login).map(Usuario::getId).orElse(0L);
                }
            }
            return 0L;
        } catch (Exception e) {
            log.warn("[UsersQueryAdapter] Falha ao obter currentUserId: {}", e.getMessage());
            return 0L;
        }
    }

    @Override
    public Set<Long> adminIds() { return findByRoleFamily("ADMIN"); }

    @Override
    public Set<Long> devIds() { return findByRoleFamily("DEV"); }

    @Override
    public Set<Long> gerenteIds() { return findByRoleFamily("GERENTE"); }

    /** Aceita 'ROLE_X' e variações; se vazio, tenta like '%X%'. */
    private Set<Long> findByRoleFamily(String roleBaseUpper) {
        String base = (roleBaseUpper == null ? "" : roleBaseUpper.trim().toUpperCase());
        Set<String> roles = new HashSet<String>();
        roles.add(base);
        roles.add("ROLE_" + base);

        Set<Long> ids = new HashSet<Long>(repo.findIdsByRoleInAndAtivoTrue(roles));
        if (ids.isEmpty()) {
            List<Long> like = repo.findIdsByRoleContainsAndAtivoTrue(base);
            ids.addAll(like);
        }
        log.debug("[UsersQueryAdapter] {} -> {}", base, ids);
        return ids;
    }
}
