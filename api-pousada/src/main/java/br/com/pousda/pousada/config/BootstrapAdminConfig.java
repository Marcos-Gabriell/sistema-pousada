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
import java.util.*;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class BootstrapAdminConfig {

    private final UsuarioRepository repo;
    private final PasswordEncoder encoder;   // pode ser BCryptPasswordEncoder
    private final Environment env;

    private static final String DEV_LOGIN_DEFAULT = "Pousadabrejo@DEV2025";

    @Bean
    @Transactional
    public ApplicationRunner bootstrapUsersRunner() {
        return args -> {
            final String appEnv = opt("APP_ENV", "dev").toLowerCase();

            if (!devExists()) {
                final String devLogin  = opt("DEV_INIT_LOGIN", DEV_LOGIN_DEFAULT);
                final String devNumero = requireNumero(appEnv, "DEV_INIT_NUMERO", "55988888888");
                final String bcrypt    = opt("DEV_INIT_PASSWORD_BCRYPT", null);
                final String raw       = opt("DEV_INIT_PASSWORD", null);

                if ("prod".equals(appEnv) && !isBcrypt(bcrypt)) {
                    throw new IllegalStateException("Em produção, defina DEV_INIT_PASSWORD_BCRYPT (hash bcrypt).");
                }

                final String devPwdHash =
                        isBcrypt(bcrypt) ? bcrypt :
                                (raw != null && !raw.trim().isEmpty() ? encoder.encode(raw.trim()) : null);

                if (devPwdHash == null) {
                    throw new IllegalStateException("Defina DEV_INIT_PASSWORD_BCRYPT (hash) ou DEV_INIT_PASSWORD (texto).");
                }

                if (repo.findByUsername(devLogin).isPresent() || repo.findByEmail(devLogin).isPresent()) {
                    throw new IllegalStateException("Login do DEV já está em uso: " + devLogin);
                }

                Usuario dev = new Usuario();
                dev.setUsername(devLogin);
                dev.setEmail(devLogin);
                dev.setNumero(devNumero);
                dev.setNome("DEV");
                dev.setPassword(devPwdHash);
                dev.setRole("DEV");
                dev.setAtivo(true);
                dev.setBootstrapAdmin(false);

                // senha permanente (não força troca no primeiro login)
                dev.setMustChangePassword(false);
                dev.setPwdChangeReason(null);
                dev.setLastPasswordChangeAt(LocalDateTime.now());

                dev.setInativadoEm(null);

                repo.save(dev);
                log.info("=== DEV criado com login {} (env: {}) ===", devLogin, appEnv);
            } else {
                log.debug("Bootstrap DEV não necessário (já existe pelo menos 1 DEV).");
            }
        };
    }

    /** Verifica existência de pelo menos um usuário DEV usando os métodos disponíveis no repositório. */
    private boolean devExists() {
        // tenta papéis exatos: "DEV" e "ROLE_DEV"
        Set<String> roles = new HashSet<String>();
        roles.add("DEV");
        roles.add("ROLE_DEV");

        try {
            java.util.List<Long> ids = repo.findIdsByRoleInAndAtivoTrue(roles);
            if (ids != null && !ids.isEmpty()) return true;
        } catch (Exception e) {
            log.debug("findIdsByRoleInAndAtivoTrue falhou/indisponível: {}", e.getMessage());
        }

        // fallback: LIKE '%DEV%'
        try {
            java.util.List<Long> like = repo.findIdsByRoleContainsAndAtivoTrue("DEV");
            return like != null && !like.isEmpty();
        } catch (Exception e) {
            log.debug("findIdsByRoleContainsAndAtivoTrue falhou/indisponível: {}", e.getMessage());
        }

        return false;
    }

    // ================= util =================

    private String opt(String key, String def) {
        String v = System.getenv(key);
        if (v != null && !v.trim().isEmpty()) return v.trim();

        v = env.getProperty(key);
        if (v != null && !v.trim().isEmpty()) return v.trim();

        v = System.getProperty(key);
        if (v != null && !v.trim().isEmpty()) return v.trim();

        return def;
    }

    private String requireNumero(String appEnv, String key, String devDefault) {
        String v = opt(key, null);
        if (v != null && !v.trim().isEmpty()) return v.trim();
        if (!"prod".equals(appEnv)) return devDefault;
        throw new IllegalStateException("Defina " + key + " em PRODUÇÃO.");
    }

    private static boolean isBcrypt(String s) {
        return s != null && s.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$");
    }
}
