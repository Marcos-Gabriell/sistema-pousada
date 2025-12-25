package br.com.pousda.pousada.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public final class JwtUtil {

    private static final String SECRET =
            "7HJ3EgiCDOtbmYQULwtwIJ/rVBTmwUTDoN/GfNXR6ePDkkTLATBLp8qJN8xmdYGF";

    private static final Key KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private static final long EXP_MILLIS = 24L * 60 * 60 * 1000;

    private static final long CLOCK_SKEW_MILLIS = 60_000;

    private JwtUtil() {}

    private static String normalizeRole(String role) {
        if (role == null || role.isBlank()) return null;
        final String up = role.toUpperCase();
        return up.startsWith("ROLE_") ? up : "ROLE_" + up;
    }

    private static String stripBearer(String token) {
        if (token == null) return null;
        String t = token.trim();
        if (t.startsWith("\"") && t.endsWith("\"")) t = t.substring(1, t.length() - 1);
        return t.replaceFirst("(?i)^Bearer\\s+", "").trim();
    }

    public static String generateToken(String subject, Long userId, String role) {
        return generateToken(subject, userId, role, null);
    }

    public static String generateToken(String subject, Long userId, String role, String email) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + EXP_MILLIS);

        String norm = normalizeRole(role);

        JwtBuilder builder = Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(KEY, SignatureAlgorithm.HS256)
                .claim("uid", userId);

        if (email != null) {
            builder.claim("email", email);
        }

        if (norm != null) {
            builder.claim("role", norm.replaceFirst("^ROLE_", ""));
            builder.claim("authorities", List.of(norm));
        } else {
            builder.claim("authorities", Collections.emptyList());
        }
        return builder.compact();
    }

    public static String generateToken(String subject, Long userId) {
        return generateToken(subject, userId, (String) null);
    }

    public static String generateToken(String subject) {
        return generateToken(subject, null, (String) null);
    }

    public static String extractUsername(String token) {
        return getClaims(token).getSubject();
    }

    public static String extractRole(String token) {
        Object r = getClaims(token).get("role");
        return r == null ? null : r.toString();
    }

    public static Long extractUserId(String token) {
        Object uid = getClaims(token).get("uid");
        if (uid == null) return null;
        if (uid instanceof Number) return ((Number) uid).longValue();
        try { return Long.parseLong(uid.toString()); } catch (NumberFormatException e) { return null; }
    }

    public static String extractEmail(String token) {
        Object email = getClaims(token).get("email");
        return email != null ? email.toString() : null;
    }

    @SuppressWarnings("unchecked")
    public static List<String> extractAuthorities(String token) {
        Object a = getClaims(token).get("authorities");
        if (a instanceof List<?>) {
            return ((List<?>) a).stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    public static boolean isTokenValid(String token) {
        try {
            Claims c = getClaims(token);
            Date now = new Date(System.currentTimeMillis() - CLOCK_SKEW_MILLIS);
            return c.getExpiration().after(now);
        } catch (Exception e) {
            return false;
        }
    }

    // NOVO MÉTODO: Extrai AuthPrincipal do token
    public static AuthPrincipal extractAuthPrincipal(String token) {
        Claims claims = getClaims(token);
        Long userId = extractUserId(token);
        String username = claims.getSubject();
        String role = extractRole(token);
        String email = extractEmail(token);

        return new AuthPrincipal(userId, username, email, role, true);
    }

    private static Claims getClaims(String rawToken) {
        String token = stripBearer(rawToken);
        return Jwts.parserBuilder()
                .setSigningKey(KEY)
                .setAllowedClockSkewSeconds(CLOCK_SKEW_MILLIS / 1000)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}