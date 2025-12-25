package br.com.pousda.pousada.auth.api;

import br.com.pousda.pousada.usuarios.domain.Usuario;
import br.com.pousda.pousada.usuarios.dtos.AuthRequest;
import br.com.pousda.pousada.usuarios.dtos.AuthResponse;
import br.com.pousda.pousada.usuarios.infra.UsuarioRepository;
import br.com.pousda.pousada.security.JwtUtil;
import br.com.pousda.pousada.security.SecurityUtils;
import br.com.pousda.pousada.auth.application.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UsuarioRepository usuarioRepository;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest req,
                                   HttpServletRequest servletRequest) {
        try {
            if (req == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Body inválido."));
            }
            if (req.getLogin() == null || req.getPassword() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "login e password são obrigatórios."));
            }

            // Descobre IP e User-Agent da requisição
            String clientIp = extractClientIp(servletRequest);
            String userAgent = Optional.ofNullable(servletRequest.getHeader("User-Agent"))
                    .orElse("desconhecido");

            // Autentica + registra login/acesso/IP
            Usuario u = authService.authenticate(
                    req.getLogin().trim(),
                    req.getPassword(),
                    clientIp,
                    userAgent
            );

            // SUBJECT = algo estável para identificar o usuário (email ou username)
            String subject = resolveSubject(u);

            // Gera token com todas as informações necessárias para o AuthPrincipal
            String token = JwtUtil.generateToken(subject, u.getId(), u.getRole(), u.getEmail());

            AuthResponse resp = new AuthResponse(
                    token,
                    u.getRole(),
                    u.getUsername(),
                    u.getEmail(),
                    u.getNumero(),
                    u.getNome(),
                    u.isMustChangePassword(),
                    u.getPwdChangeReason(),
                    u.getTema()
            );

            // Auditoria & presença
            resp.setUserId(u.getId());
            resp.setUltimoLoginEm(u.getUltimoLoginEm());
            resp.setUltimoLoginIp(u.getUltimoLoginIp());
            resp.setIpAtual(clientIp);
            resp.setOnline(true); // acabou de logar => está online

            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            String env = Optional.ofNullable(System.getenv("APP_ENV")).orElse("dev");
            if (!"prod".equalsIgnoreCase(env)) {
                return ResponseEntity.status(500)
                        .body(Map.of("error", e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage())));
            }
            return ResponseEntity.status(500).body(Map.of("error", "Erro inesperado no login."));
        }
    }

    private String resolveSubject(Usuario u) {
        if (u.getEmail() != null && !u.getEmail().isBlank()) return u.getEmail();
        if (u.getUsername() != null && !u.getUsername().isBlank()) return u.getUsername();
        if (u.getNumero() != null && !u.getNumero().isBlank()) return u.getNumero();
        return String.valueOf(u.getId());
    }

    private String extractClientIp(HttpServletRequest request) {
        // 1) Proxy / Load Balancer (prod)
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            int comma = ip.indexOf(',');
            ip = (comma > -1) ? ip.substring(0, comma).trim() : ip.trim();
            if (!ip.isBlank()) {
                return normalizeLoopback(ip);
            }
        }

        // 2) X-Real-IP (alguns proxies)
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) {
            return normalizeLoopback(ip.trim());
        }

        // 3) Direto no servidor (dev / sem proxy)
        return normalizeLoopback(request.getRemoteAddr());
    }

    private String normalizeLoopback(String ip) {
        if (ip == null) return null;
        ip = ip.trim();
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            // loopback IPv6 -> mostra como IPv4 pra ficar mais amigável
            return "127.0.0.1";
        }
        return ip;
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetSelf(@RequestBody Map<String, String> body) {
        try {
            Long userId = SecurityUtils.getCurrentUserId();
            Usuario me = usuarioRepository.findById(userId)
                    .orElseThrow(() -> new IllegalStateException("Usuário não encontrado"));

            String atual = body.get("senhaAtual");
            String nova  = body.get("novaSenha");
            String conf  = body.get("confirmarSenha");

            authService.resetOwnPassword(me, atual, nova, conf);
            return ResponseEntity.ok(Map.of("mensagem", "Senha alterada com sucesso."));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Erro inesperado ao alterar senha."));
        }
    }
}
