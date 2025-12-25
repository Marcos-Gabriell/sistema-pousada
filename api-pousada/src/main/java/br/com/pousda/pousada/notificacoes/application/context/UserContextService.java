package br.com.pousda.pousada.notificacoes.application.context;

import br.com.pousda.pousada.notificacoes.application.UsersQueryPort;
import br.com.pousda.pousada.security.AuthPrincipal;
import br.com.pousda.pousada.usuarios.domain.Usuario;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserContextService {

    private static final Logger log = LoggerFactory.getLogger(UserContextService.class);

    private final UsersQueryPort users;
    private final ObjectMapper mapper;

    public Long getCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthPrincipal) {
                Long userId = ((AuthPrincipal) auth.getPrincipal()).getId();
                log.debug("[USER-CTX] User ID from AuthPrincipal: {}", userId);
                return userId;
            }
        } catch (Exception e) {
            log.warn("[USER-CTX] Error getting user ID from security context: {}", e.getMessage());
        }
        Long fallbackId = users.currentUserId();
        log.debug("[USER-CTX] Using fallback user ID: {}", fallbackId);
        return fallbackId;
    }

    public String getCurrentUserName() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthPrincipal) {
                AuthPrincipal principal = (AuthPrincipal) auth.getPrincipal();
                String name = principal.getUsername();
                if (name == null || name.trim().isEmpty()) name = principal.getEmail();
                if (name != null && name.contains("@")) name = name.substring(0, name.indexOf('@'));
                if (name != null && !name.trim().isEmpty()) {
                    return name.trim();
                }
            }
        } catch (Exception e) {
            log.warn("[USER-CTX] Error getting user name from security context: {}", e.getMessage());
        }
        return "Usuário";
    }

    public String getCurrentUserJson() {
        Long id = getCurrentUserId();
        String nome = getCurrentUserName();
        if (id == null && (nome == null || nome.trim().isEmpty())) {
            return "{}";
        }
        try {
            Map<String, Object> map = new HashMap<>();
            if (id != null) map.put("id", id);
            if (nome != null) map.put("nome", nome);
            return mapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{\"id\":" + id + ",\"nome\":\"" + safe(nome) + "\"}";
        }
    }

    public String getUserJson(Usuario usuario) {
        if (usuario == null) return getCurrentUserJson();
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("id", usuario.getId());
            map.put("nome", usuario.getNome());
            return mapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("Erro ao serializar JSON do autor, usando fallback: {}", e.getMessage());
            return "{\"id\":" + usuario.getId() + ",\"nome\":\"" + safe(usuario.getNome()) + "\"}";
        }
    }

    private String safe(String s) {
        return (s == null) ? "-" : s;
    }
}