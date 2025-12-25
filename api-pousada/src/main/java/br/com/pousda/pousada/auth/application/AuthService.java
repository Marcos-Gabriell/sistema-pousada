package br.com.pousda.pousada.auth.application;

import br.com.pousda.pousada.usuarios.domain.Usuario;
import br.com.pousda.pousada.usuarios.infra.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UsuarioRepository repo;
    private final BCryptPasswordEncoder encoder;

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_TIME = Duration.ofMinutes(10);
    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    private static final Pattern ONLY_DIGITS = Pattern.compile("^\\d+$");
    private static final int MAX_ID_LEN = 120;
    private static final int MAX_PWD_LEN = 72;

    public static final class Attempt {
        int count;
        Instant lockUntil;
    }

    public Usuario authenticate(String login, String rawPassword, String clientIp, String userAgent) {
        if (isBlank(login)) throw new IllegalArgumentException("Informe username, e-mail ou número.");
        if (isBlank(rawPassword)) throw new IllegalArgumentException("Senha obrigatória.");
        login = login.trim();
        if (login.length() > MAX_ID_LEN) throw new IllegalArgumentException("Identificador muito longo.");
        if (rawPassword.length() > MAX_PWD_LEN) throw new IllegalArgumentException("Senha muito longa.");

        String key = login.toLowerCase();
        var a = attempts.computeIfAbsent(key, k -> new Attempt());
        if (a.lockUntil != null && a.lockUntil.isAfter(Instant.now())) {
            long rest = Duration.between(Instant.now(), a.lockUntil).toSeconds();
            throw new IllegalStateException("Muitas tentativas. Tente em " + rest + "s.");
        }

        Usuario u = findBySmartLogin(login).orElse(null);

        if (u == null || !encoder.matches(rawPassword, u.getPassword())) {
            a.count++;
            if (a.count >= MAX_ATTEMPTS) {
                a.lockUntil = Instant.now().plus(LOCK_TIME);
                a.count = 0;
                log.warn("Tentativas esgotadas para o login: {}", login);
            }
            throw new IllegalArgumentException("Usuário ou senha inválidos."); // Mensagem genérica
        }

        if (!u.isAtivo()) {
            log.warn("Tentativa de login bloqueada para usuário inativo: {}", u.getEmail());
            throw new IllegalStateException("Sua conta está inativa. Entre em contato com o administrador.");
        }

        // Limpa contador de tentativas falhas
        attempts.remove(key);

        // ===== Auditoria de login / acesso =====
        LocalDateTime agora = LocalDateTime.now();

        // Login (momento em que autenticou)
        u.setUltimoLoginEm(agora);
        u.setUltimoLoginIp(clientIp);

        // Último acesso (p/ online/offline)
        u.setUltimoAcessoEm(agora);
        u.setUltimoAcessoIp(clientIp);

        repo.save(u);

        log.info("[LOGIN] userId={} email={} ip={} ua='{}'",
                u.getId(),
                u.getEmail(),
                clientIp,
                userAgent
        );

        return u;
    }

    public void resetOwnPassword(Usuario me, String senhaAtual, String novaSenha, String confirmar) {
        if (me == null) throw new IllegalStateException("Não autenticado.");

        if (isBlank(novaSenha))  throw new IllegalArgumentException("Nova senha obrigatória.");
        if (isBlank(confirmar))  throw new IllegalArgumentException("Confirmação obrigatória.");
        if (!novaSenha.equals(confirmar)) throw new IllegalArgumentException("Confirmação não confere.");
        if (!isSimplePassword(novaSenha))
            throw new IllegalArgumentException("A nova senha deve ter pelo menos 5 caracteres e conter ao menos 1 número.");
        if (novaSenha.length() > MAX_PWD_LEN) throw new IllegalArgumentException("Nova senha muito longa.");

        boolean exigeSenhaAtual = !Boolean.TRUE.equals(me.isMustChangePassword());
        if (exigeSenhaAtual) {
            if (isBlank(senhaAtual)) throw new IllegalArgumentException("Senha atual obrigatória.");
            if (!encoder.matches(senhaAtual, me.getPassword())) throw new IllegalArgumentException("Senha atual inválida.");
        }

        if (encoder.matches(novaSenha, me.getPassword()))
            throw new IllegalArgumentException("A nova senha não pode ser igual à atual.");

        me.setPassword(encoder.encode(novaSenha));
        me.setMustChangePassword(false);
        me.setPwdChangeReason(null);
        me.setLastPasswordChangeAt(LocalDateTime.now());
        repo.save(me);
    }

    public String resolvePrincipalId(Usuario u) {
        return String.valueOf(u.getId());
    }

    private Optional<Usuario> findBySmartLogin(String login) {
        Optional<Usuario> u;
        if (login.contains("@")) {
            u = repo.findByEmail(login);
            if (u.isPresent()) return u;
            u = repo.findByUsername(login);
            if (u.isPresent()) return u;
            return repo.findByNumero(login);
        }
        if (ONLY_DIGITS.matcher(login).matches()) {
            u = repo.findByNumero(login);
            if (u.isPresent()) return u;
            u = repo.findByUsername(login);
            if (u.isPresent()) return u;
            u = repo.findByEmail(login);
            if (u.isPresent()) return u;
            return repo.findByNumero(login);
        }
        u = repo.findByUsername(login);
        if (u.isPresent()) return u;
        u = repo.findByEmail(login);
        if (u.isPresent()) return u;
        return repo.findByNumero(login);
    }

    private boolean isSimplePassword(String s) {
        if (s == null || s.length() < 3) return false;
        for (char c : s.toCharArray()) if (Character.isDigit(c)) return true;
        return false;
    }

    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
}
