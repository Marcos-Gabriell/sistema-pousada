package br.com.pousda.pousada.security;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@UtilityClass
@Slf4j
public class SecurityUtils {

    public static AuthPrincipal getCurrentAuthPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();

            if (principal instanceof AuthPrincipal) {
                return (AuthPrincipal) principal;
            } else {
                log.warn("Tipo de principal desconhecido: {}",
                        principal != null ? principal.getClass().getName() : "null");
            }
        }

        throw new IllegalStateException("Não foi possível obter AuthPrincipal - usuário não autenticado");
    }

    public static Long getCurrentUserId() {
        return getCurrentAuthPrincipal().getId();
    }

    public static String getCurrentUsername() {
        return getCurrentAuthPrincipal().getName();
    }

    public static String getCurrentUserRole() {
        return getCurrentAuthPrincipal().getRole();
    }

    // Método seguro que não lança exceção
    public static String getCurrentUsernameSafe() {
        try {
            return getCurrentUsername();
        } catch (Exception e) {
            log.warn("Não foi possível obter usuário logado: {}", e.getMessage());
            return "Sistema";
        }
    }
}