package com.example.aiagent.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    // Clé secrète HS256 — minimum 32 caractères
    private static final String SECRET = "GhostEmployerSecretKey2026!XyZ#$%";
    private static final long EXPIRATION_MS = 24 * 60 * 60 * 1000L; // 24h

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    /** Génère un JWT contenant l'email et le rôle de l'utilisateur. */
    public String generateToken(String email, String role) {
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(getKey())
                .compact();
    }

    /** Extrait l'email (subject) du token. */
    public String extractEmail(String token) {
        return getClaims(token).getSubject();
    }

    /** Extrait le rôle du token. */
    public String extractRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    /** Vérifie que le token est valide (signature + expiration). */
    public boolean isValid(String token) {
        try {
            getClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
