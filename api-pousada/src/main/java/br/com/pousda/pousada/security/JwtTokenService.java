package br.com.pousda.pousada.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Collections;

@Service
public class JwtTokenService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.exp:86400}")
    private long expSeconds;

    private Key key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generate(String subject, Map<String,Object> claims) {
        Instant now = Instant.now();

        // 🔍 GARANTIR QUE ROLE ESTÁ NO FORMATO CORRETO
        if (claims != null && claims.containsKey("role")) {
            String role = claims.get("role").toString().toUpperCase();
            if (!role.startsWith("ROLE_")) {
                role = "ROLE_" + role;
                claims.put("role", role);
            }

            claims.put("authorities", Collections.singletonList(role));
        }
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(expSeconds)))
                .addClaims(claims == null ? Map.of() : claims)
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}