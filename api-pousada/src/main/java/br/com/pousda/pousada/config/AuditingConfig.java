package br.com.pousda.pousada.config;

import br.com.pousda.pousada.usuarios.domain.Usuario;
import br.com.pousda.pousada.usuarios.infra.UsuarioRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class AuditingConfig {

    @Bean
    public AuditorAware<String> auditorAware(UsuarioRepository usuarioRepository) {
        return () -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()
                    || "anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {
                return Optional.empty();
            }

            String login = auth.getName();
            Usuario u = null;

            if (login != null && login.matches("\\d+")) {
                u = usuarioRepository.findById(Long.parseLong(login)).orElse(null);
            }
            if (u == null) {
                u = usuarioRepository.findByLogin(login).orElse(null);
            }

            // se achou, retorna "Nome | id"
            if (u != null) return Optional.of(u.getNome() + " | " + u.getId());

            Object p = auth.getPrincipal();
            String nomeP = reflectString(p, "getName").orElse(null);
            Long   idP   = reflectLong(p, "getId").orElse(null);
            if (nomeP != null && idP != null) return Optional.of(nomeP + " | " + idP);
            if (nomeP != null) return Optional.of(nomeP);

            return Optional.ofNullable(login);
        };
    }

    private Optional<Long> reflectLong(Object target, String method) {
        try {
            var m = target.getClass().getMethod(method);
            var v = m.invoke(target);
            return (v == null) ? Optional.empty() : Optional.of(Long.valueOf(String.valueOf(v)));
        } catch (Exception ignored) { return Optional.empty(); }
    }

    private Optional<String> reflectString(Object target, String method) {
        try {
            var m = target.getClass().getMethod(method);
            var v = m.invoke(target);
            if (v == null) return Optional.empty();
            var s = String.valueOf(v).trim();
            return s.isEmpty() ? Optional.empty() : Optional.of(s);
        } catch (Exception ignored) { return Optional.empty(); }
    }
}
