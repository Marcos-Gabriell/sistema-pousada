package br.com.pousda.pousada.config;

import br.com.pousda.pousada.usuarios.domain.Usuario;
import br.com.pousda.pousada.usuarios.infra.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class BootstrapAdminConfig {

    private final UsuarioRepository repo;
    private final PasswordEncoder encoder;
    private final Environment env;

    private static final String DEV_ADMIN_USER_DEFAULT = "admin";
    private static final String DEV_ADMIN_PASS_DEFAULT = "admin123";

    @Bean
    @Transactional
    public ApplicationRunner bootstrapUsersRunner() {
        return args -> {
            final String appEnv = opt("APP_ENV", "dev").toLowerCase();

            if (!adminExists()) {
                final String username = opt("ADMIN_INIT_USERNAME", DEV_ADMIN_USER_DEFAULT);
                final String email    = opt("ADMIN_INIT_EMAIL", "admin@local");
                final String numero   = opt("ADMIN_INIT_NUMERO", "55988888888");

                final String bcrypt = opt("ADMIN_INIT_PASSWORD_BCRYPT", null);
                final String raw    = opt("ADMIN_INIT_PASSWORD", DEV_ADMIN_PASS_DEFAULT);

                if ("prod".equals(appEnv) && !isBcrypt(bcrypt)) {
                    throw new IllegalStateException("Em produção, defina ADMIN_INIT_PASSWORD_BCRYPT (hash bcrypt).");
                }

                final String pwdHash = isBcrypt(bcrypt) ? bcrypt : encoder.encode(raw);

                if (repo.findByUsername(username).isPresent() || repo.findByEmail(email).isPresent()) {
                    throw new IllegalStateException("ADMIN já está em uso (username/email).");
                }

                Usuario admin = new Usuario();
                admin.setUsername(username);
                admin.setEmail(email);
                admin.setNumero(numero);
                admin.setNome("ADMIN");
                admin.setPassword(pwdHash);

                admin.setRole("ADMIN");

                admin.setAtivo(true);
                admin.setBootstrapAdmin(true);

                admin.setMustChangePassword(false);
                admin.setPwdChangeReason(null);
                admin.setLastPasswordChangeAt(LocalDateTime.now());
                admin.setInativadoEm(null);

                repo.save(admin);
                log.info("=== ADMIN criado: {} (env: {}) ===", username, appEnv);
            } else {
                log.debug("Bootstrap ADMIN não necessário (já existe).");
            }
        };
    }

    private boolean adminExists() {
        Set<String> roles = new HashSet<>();
        roles.add("ADMIN");
        roles.add("ROLE_ADMIN");

        try {
            var ids = repo.findIdsByRoleInAndAtivoTrue(roles);
            if (ids != null && !ids.isEmpty()) return true;
        } catch (Exception ignored) {}

        try {
            var like = repo.findIdsByRoleContainsAndAtivoTrue("ADMIN");
            return like != null && !like.isEmpty();
        } catch (Exception ignored) {}

        return false;
    }

    private String opt(String key, String def) {
        String v = System.getenv(key);
        if (v != null && !v.trim().isEmpty()) return v.trim();

        v = env.getProperty(key);
        if (v != null && !v.trim().isEmpty()) return v.trim();

        v = System.getProperty(key);
        if (v != null && !v.trim().isEmpty()) return v.trim();

        return def;
    }

    private static boolean isBcrypt(String s) {
        return s != null && s.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$");
    }
}
