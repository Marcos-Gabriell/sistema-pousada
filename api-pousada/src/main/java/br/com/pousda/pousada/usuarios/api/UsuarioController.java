package br.com.pousda.pousada.usuarios.api;

import br.com.pousda.pousada.usuarios.aplication.UsuarioService;
import br.com.pousda.pousada.usuarios.domain.Usuario;
import br.com.pousda.pousada.usuarios.dtos.*;
import br.com.pousda.pousada.usuarios.infra.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.net.URI;
import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService service;
    private final UsuarioRepository usuarioRepository;

    private static final Logger log = LoggerFactory.getLogger(UsuarioController.class);

    private Usuario resolveByPrincipal(Principal principal) {
        if (principal == null) return null;
        String sub = principal.getName();

        Usuario me = null;

        // tenta como ID
        try {
            Long id = Long.parseLong(sub);
            me = usuarioRepository.findById(id).orElse(null);
        } catch (NumberFormatException ignored) {}

        // tenta username/email/numero
        if (me == null) {
            me = usuarioRepository.findByUsername(sub)
                    .orElseGet(() -> usuarioRepository.findByEmail(sub)
                            .orElseGet(() -> usuarioRepository.findByNumero(sub).orElse(null)));
        }
        return me;
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) return xf.split(",")[0].trim();

        String xr = request.getHeader("X-Real-IP");
        if (xr != null && !xr.isBlank()) return xr.trim();

        return request.getRemoteAddr();
    }

    // ✅ PRESENÇA: mantém usuário online (sem security)
    @PostMapping("/me/ping")
    public ResponseEntity<?> ping(Principal principal, HttpServletRequest request) {
        Usuario me = resolveByPrincipal(principal);
        if (me == null) return ResponseEntity.status(401).body(Map.of("error", "Sessão inválida."));

        String ip = getClientIp(request);
        String ua = request.getHeader("User-Agent");

        service.registrarAcesso(me, ip, ua);

        return ResponseEntity.ok(Map.of(
                "ok", true,
                "online", true
        ));
    }

    @GetMapping
    public ResponseEntity<?> listar(@RequestParam(defaultValue = "1") int pagina,
                                    @RequestParam(defaultValue = "10") int tamanho,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String perfil) {
        return ResponseEntity.ok(service.listar(pagina, tamanho, q, status, perfil));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obterPorId(Principal principal, @PathVariable Long id) {
        Usuario ator = resolveByPrincipal(principal);
        if (ator == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Sessão inválida."));
        }
        UsuarioResponseDTO dto = service.buscarPorIdDTO(id, ator);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/perfis")
    public ResponseEntity<?> perfis(Principal principal) {
        Usuario ator = resolveByPrincipal(principal);
        if (ator == null) return ResponseEntity.status(401).body(Map.of("error", "Sessão inválida."));
        return ResponseEntity.ok(service.listarPerfis(ator));
    }

    @PostMapping({"", "/", "/cadastrar"})
    public ResponseEntity<UsuarioCriadoResponse> criar(Principal principal,
                                                       @RequestBody @Valid CriarUsuarioDTO dto) {
        Usuario criador = resolveByPrincipal(principal);
        if (criador == null) return ResponseEntity.status(401).build();

        var r = service.criar(criador, dto);

        var body = new UsuarioCriadoResponse(
                ((Number) r.get("id")).longValue(),
                (String) r.get("nome"),
                (String) r.get("username"),
                (String) r.get("email"),
                (String) r.get("numero"),
                (String) r.get("role"),
                (Boolean) r.get("ativo"),
                (String) r.getOrDefault("mensagem", "Usuário cadastrado com sucesso!"),
                (String) r.get("senhaTemporaria")
        );

        return ResponseEntity.created(URI.create("/api/usuarios/" + body.getId())).body(body);
    }

    @PostMapping("/dev")
    public ResponseEntity<?> criarComoDev(Principal principal,
                                          @RequestBody @Valid CriarUsuarioDTO dto) {
        Usuario criador = resolveByPrincipal(principal);
        if (criador == null) return ResponseEntity.status(401).body(Map.of("error", "Sessão inválida."));

        var r = service.criar(criador, dto);

        var body = new UsuarioCriadoResponse(
                ((Number) r.get("id")).longValue(),
                (String) r.get("nome"),
                (String) r.get("username"),
                (String) r.get("email"),
                (String) r.get("numero"),
                (String) r.get("role"),
                (Boolean) r.get("ativo"),
                (String) r.getOrDefault("mensagem", "Usuário cadastrado com sucesso!"),
                (String) r.get("senhaTemporaria")
        );
        return ResponseEntity.created(URI.create("/api/usuarios/" + body.getId())).body(body);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> atualizar(Principal principal,
                                       @PathVariable Long id,
                                       @RequestBody @Valid AtualizarUsuarioDTO dto) {
        Usuario ator = resolveByPrincipal(principal);
        if (ator == null) return ResponseEntity.status(401).body(Map.of("error", "Sessão inválida."));
        return ResponseEntity.ok(service.atualizar(ator, id, dto));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> alterarStatus(Principal principal,
                                           @PathVariable Long id,
                                           @RequestBody @Valid StatusDTO dto) {
        Usuario ator = resolveByPrincipal(principal);
        if (ator == null) return ResponseEntity.status(401).body(Map.of("error", "Sessão inválida."));

        UsuarioResponseDTO alvo = service.alterarStatus(ator, id, dto);

        if (!alvo.isAtivo()) {
            return ResponseEntity.ok(Map.of(
                    "mensagem", "Status alterado para **Inativo**. O usuário será desconectado e obrigado a realizar novo login.",
                    "forceLogout", true,
                    "alvoId", alvo.getId()
            ));
        }

        return ResponseEntity.ok(Map.of("mensagem", "Status alterado para **Ativo**."));
    }

    @PostMapping("/{id}/reset-senha")
    public ResponseEntity<?> resetSenha(Principal principal,
                                        @PathVariable Long id) {
        Usuario ator = resolveByPrincipal(principal);
        if (ator == null) return ResponseEntity.status(401).body(Map.of("error", "Sessão inválida."));

        Map<String, Object> resultado = service.resetSenha(ator, id);
        Map<String, Object> respostaFinal = new java.util.HashMap<>(resultado);
        respostaFinal.put("forceLogout", true);
        respostaFinal.put("mensagem", "Senha redefinida com sucesso! O usuário será desconectado e obrigado a trocar a senha temporária.");
        return ResponseEntity.ok(respostaFinal);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(Principal principal,
                                        @PathVariable Long id) {
        Usuario ator = resolveByPrincipal(principal);
        if (ator == null) return ResponseEntity.status(401).build();
        service.excluir(ator, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Principal principal) {
        try {
            Usuario me = resolveByPrincipal(principal);
            if (me == null) return ResponseEntity.status(401).body(Map.of("error", "Sessão inválida."));

            me.setDiasParaExclusao(service.diasParaExclusaoDo(me));
            UsuarioResponseDTO resp = service.toDTO(me, me);
            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            log.error("[ME] erro ao carregar usuário", e);
            return ResponseEntity.status(500).body(Map.of("error", "Erro ao carregar usuário."));
        }
    }

    @PutMapping("/me")
    public ResponseEntity<?> atualizarMe(Principal principal,
                                         @RequestBody @Valid AtualizarProprioUsuarioDTO dto) {

        Usuario me = resolveByPrincipal(principal);
        if (me == null) return ResponseEntity.status(401).body(Map.of("error", "Sessão inválida."));

        UsuarioResponseDTO atualizado = service.atualizarProprio(me, dto);
        return ResponseEntity.ok(atualizado);
    }

    @PatchMapping("/me/trocar-senha")
    public ResponseEntity<?> trocarSenha(Principal principal,
                                         @RequestBody @Valid TrocarSenhaDTO dto) {
        Usuario me = resolveByPrincipal(principal);
        if (me == null) return ResponseEntity.status(401).body(Map.of("error", "Sessão inválida."));
        try {
            var resp = service.trocarSenha(me, dto.getSenhaAtual(), dto.getNovaSenha());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    // ===== AVATAR: FOTO BASE64 =====
    @PostMapping("/{id}/avatar/base64")
    public ResponseEntity<?> enviarAvatarBase64(Principal principal,
                                                @PathVariable Long id,
                                                @RequestBody @Valid AvatarBase64DTO body) {
        long t0 = System.currentTimeMillis();
        Usuario ator = resolveByPrincipal(principal);
        if (ator == null) {
            log.warn("[AVATAR/POST] principal nulo (targetId={})", id);
            return ResponseEntity.status(401).body(Map.of("error", "Sessão inválida."));
        }

        final int len = body.getDataUrl() != null ? body.getDataUrl().length() : 0;
        final String prefix = (len >= 30 ? body.getDataUrl().substring(0, 30) : body.getDataUrl());
        log.info("[AVATAR/POST] actorId={} targetId={} dataUrl.len={} dataUrl.prefix='{}...'",
                ator.getId(), id, len, prefix);

        try {
            Usuario salvo = service.atualizarAvatarPhotoBase64(ator, id, body.getDataUrl());
            long took = System.currentTimeMillis() - t0;
            log.info("[AVATAR/POST] OK actorId={} targetId={} mode={} version={} tookMs={}",
                    ator.getId(), id, salvo.getAvatarMode(), salvo.getAvatarVersion(), took);
            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "mode", salvo.getAvatarMode().name(),
                    "version", salvo.getAvatarVersion()
            ));
        } catch (IllegalArgumentException e) {
            long took = System.currentTimeMillis() - t0;
            log.warn("[AVATAR/POST] FAIL_VALIDATION actorId={} targetId={} msg='{}' tookMs={}",
                    ator.getId(), id, e.getMessage(), took);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            long took = System.currentTimeMillis() - t0;
            log.error("[AVATAR/POST] FAIL actorId={} targetId={} tookMs={}", ator.getId(), id, took, e);
            return ResponseEntity.status(500).body(Map.of("error", "Falha ao processar imagem."));
        }
    }

    @PostMapping("/me/avatar/base64")
    public ResponseEntity<?> enviarMeuAvatarBase64(Principal principal,
                                                   @RequestBody @Valid AvatarBase64DTO body) {
        long t0 = System.currentTimeMillis();
        Usuario me = resolveByPrincipal(principal);
        if (me == null) {
            log.warn("[AVATAR/POST:ME] principal nulo");
            return ResponseEntity.status(401).body(Map.of("error", "Sessão inválida."));
        }

        final int len = body.getDataUrl() != null ? body.getDataUrl().length() : 0;
        final String prefix = (len >= 30 ? body.getDataUrl().substring(0, 30) : body.getDataUrl());
        log.info("[AVATAR/POST:ME] userId={} dataUrl.len={} dataUrl.prefix='{}...'", me.getId(), len, prefix);

        try {
            Usuario salvo = service.atualizarAvatarPhotoBase64(me, me.getId(), body.getDataUrl());
            long took = System.currentTimeMillis() - t0;
            log.info("[AVATAR/POST:ME] OK userId={} mode={} version={} tookMs={}",
                    me.getId(), salvo.getAvatarMode(), salvo.getAvatarVersion(), took);
            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "mode", salvo.getAvatarMode().name(),
                    "version", salvo.getAvatarVersion()
            ));
        } catch (IllegalArgumentException e) {
            long took = System.currentTimeMillis() - t0;
            log.warn("[AVATAR/POST:ME] FAIL_VALIDATION userId={} msg='{}' tookMs={}", me.getId(), e.getMessage(), took);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            long took = System.currentTimeMillis() - t0;
            log.error("[AVATAR/POST:ME] FAIL userId={} tookMs={}", me.getId(), took, e);
            return ResponseEntity.status(500).body(Map.of("error", "Falha ao processar imagem."));
        }
    }

    @GetMapping("/{id}/avatar")
    public ResponseEntity<?> obterAvatar(Principal principal, @PathVariable Long id) {
        Usuario ator = resolveByPrincipal(principal);
        if (ator == null) {
            log.warn("[AVATAR/GET] principal nulo (targetId={})", id);
            return ResponseEntity.status(401).body(Map.of("error", "Sessão inválida."));
        }
        log.debug("[AVATAR/GET] actorId={} targetId={}", ator.getId(), id);
        AvatarViewDTO view = service.obterAvatarDe(ator, id);
        return ResponseEntity.ok(view);
    }

    @GetMapping("/me/avatar")
    public ResponseEntity<?> meuAvatar(Principal principal) {
        Usuario me = resolveByPrincipal(principal);
        if (me == null) {
            log.warn("[AVATAR/GET:ME] principal nulo");
            return ResponseEntity.status(401).body(Map.of("error", "Sessão inválida."));
        }
        log.debug("[AVATAR/GET:ME] userId={}", me.getId());
        AvatarViewDTO view = service.obterAvatarDe(me, me.getId());
        return ResponseEntity.ok(view);
    }

    @PatchMapping("/me/avatar/prefs")
    public ResponseEntity<?> atualizarAvatarPrefs(Principal principal,
                                                  @RequestBody Map<String, Object> body) {
        Usuario me = resolveByPrincipal(principal);
        if (me == null) {
            log.warn("[AVATAR/PREFS:ME] principal nulo");
            return ResponseEntity.status(401).body(Map.of("error", "Sessão inválida."));
        }

        try {
            String mode = (String) body.get("avatarMode"); // "COLOR" | "PHOTO"
            String color = (String) body.getOrDefault("avatarColor", null);
            String gradient = (String) body.getOrDefault("avatarGradient", null);

            Usuario salvo = service.atualizarAvatarPrefs(me, mode, color, gradient);
            log.info("[AVATAR/PREFS:ME] OK userId={} mode={} color={} gradient={}",
                    me.getId(), salvo.getAvatarMode(), salvo.getAvatarColor(), salvo.getAvatarGradient());

            Map<String, Object> resp = new java.util.HashMap<>();
            resp.put("ok", true);
            resp.put("mode", String.valueOf(salvo.getAvatarMode()));
            if (salvo.getAvatarColor() != null) resp.put("color", salvo.getAvatarColor());
            if (salvo.getAvatarGradient() != null) resp.put("gradient", salvo.getAvatarGradient());
            if (salvo.getAvatarVersion() != null) resp.put("version", salvo.getAvatarVersion());

            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException e) {
            log.warn("[AVATAR/PREFS:ME] VALIDATION userId={} msg={}", me.getId(), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[AVATAR/PREFS:ME] FAIL userId={} msg={}", me.getId(), e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Falha ao aplicar preferências de avatar."));
        }
    }

    @PatchMapping("/{id}/tema")
    public ResponseEntity<UsuarioDTO> atualizarTema(@PathVariable Long id,
                                                    @RequestBody String tema) {

        Usuario usuario = service.atualizarTema(id, tema);

        UsuarioDTO resp = new UsuarioDTO(
                usuario.getId(),
                usuario.getNome(),
                usuario.getUsername(),
                usuario.getEmail(),
                usuario.getNumero(),
                usuario.getRole(),
                usuario.isAtivo(),
                usuario.getTema()
        );

        return ResponseEntity.ok(resp);
    }

    @PatchMapping("/me/tema")
    public ResponseEntity<?> atualizarMeuTema(Principal principal,
                                              @RequestBody Map<String, String> body) {
        Usuario me = resolveByPrincipal(principal);
        if (me == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Sessão inválida."));
        }

        String tema = body.get("tema");
        Usuario atualizado = service.atualizarTema(me.getId(), tema);

        return ResponseEntity.ok(Map.of(
                "id", atualizado.getId(),
                "tema", atualizado.getTema()
        ));
    }
}
