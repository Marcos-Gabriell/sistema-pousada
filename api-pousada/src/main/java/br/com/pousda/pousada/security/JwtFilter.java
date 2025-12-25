package br.com.pousda.pousada.security;

import io.jsonwebtoken.Claims;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtTokenService jwt;

    private static final String SSE_PREFIX = "/api/notificacoes/stream";

    public JwtFilter(JwtTokenService jwt) {
        this.jwt = jwt;
    }

    public Authentication resolveAuthentication(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        try {
            Claims c = jwt.parse(token);

            Long uid = extractUid(c);
            String subject = c.getSubject();
            String role = extractRole(c);
            String email = extractEmail(c);
            List<SimpleGrantedAuthority> authorities = extractAuthorities(c);

            // 🔍 NORMALIZAR ROLE
            if (role != null) {
                role = role.toUpperCase();
                if (!role.startsWith("ROLE_")) {
                    role = "ROLE_" + role;
                }
            }

            log.debug("🔐 Token extraído - UID: {}, User: {}, Role: {}, Authorities: {}",
                    uid, subject, role, authorities);

            if (uid != null && subject != null) {
                AuthPrincipal principal = new AuthPrincipal(uid, subject, email, role, true);
                return new UsernamePasswordAuthenticationToken(principal, null, authorities);
            }
        } catch (Exception e) {
            log.error("❌ Erro ao resolver autenticação do token", e);
        }
        return null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = resolveToken(request);

            if (token != null) {
                log.debug("🔍 Token encontrado para: {}", request.getServletPath());
                Authentication authentication = resolveAuthentication(token);

                if (authentication != null) {
                    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
                    log.info("👤 Usuário autenticado: {} | Role: {} | Path: {}",
                            principal.getUsername(),
                            principal.getRole(),
                            request.getServletPath());

                    UsernamePasswordAuthenticationToken auth = (UsernamePasswordAuthenticationToken) authentication;
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } else {
                    log.warn("⚠️ Falha na autenticação com token para: {}", request.getServletPath());
                }
            }
        }
        chain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String hdr = request.getHeader("Authorization");
        if (hdr != null && hdr.startsWith("Bearer ")) {
            return sanitize(hdr.substring(7));
        }

        String path = request.getServletPath();
        if (path != null && path.startsWith(SSE_PREFIX)) {
            String q = request.getParameter("token");
            if (q == null || q.trim().isEmpty()) q = request.getParameter("access_token");
            if (q != null && !q.trim().isEmpty()) return sanitize(q);
        }
        return null;
    }

    private String sanitize(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        if (t.startsWith("\"") && t.endsWith("\"")) t = t.substring(1, t.length() - 1);
        t = t.replaceFirst("(?i)^Bearer\\s+", "").trim();
        return t;
    }

    private Long extractUid(Claims c) {
        Object raw = c.get("uid");
        if (raw == null) return null;
        if (raw instanceof Number) return ((Number) raw).longValue();
        try { return Long.parseLong(raw.toString()); } catch (NumberFormatException e) { return null; }
    }

    private String extractRole(Claims c) {
        Object role = c.get("role");
        return role != null ? role.toString() : null;
    }

    private String extractEmail(Claims c) {
        Object email = c.get("email");
        return email != null ? email.toString() : null;
    }

    private List<SimpleGrantedAuthority> extractAuthorities(Claims c) {
        Object a = c.get("authorities");
        if (a instanceof List<?>) {
            List<SimpleGrantedAuthority> authoritiesList = ((List<?>) a).stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
            log.debug("Authorities extraídas do claim 'authorities': {}", authoritiesList);
            return authoritiesList;
        }
        String role = c.get("role", String.class);
        if (role != null) {
            if (!role.startsWith("ROLE_")) {
                role = "ROLE_" + role.toUpperCase();
            }
            log.debug("Authority extraída do claim 'role': {}", role);
            return Collections.singletonList(new SimpleGrantedAuthority(role));
        }
        return Collections.emptyList();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = request.getServletPath();
        return "OPTIONS".equalsIgnoreCase(request.getMethod()) ||
                "/api/auth/login".equals(p) ||
                ("GET".equalsIgnoreCase(request.getMethod()) &&
                        ("/".equals(p) || p.startsWith("/assets/") || "/index.html".equals(p)));
    }

}