package tn.bna.simulateur.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;
import java.util.function.Function;

@Slf4j
@Component
public class JwtUtil {

    // HS384 (algorithme choisi par jjwt pour une clé de 48 à 63 octets)
    // ou HS512 : la clé doit faire au moins 32 octets.
    private static final int MIN_SECRET_BYTES = 32;

    private final long EXPIRATION_TIME = 86400000L * 30L; // ✅ 30 jours

    private final SecretKey signingKey;

    /**
     * La clé vient de la propriété app.jwt.secret (variable d'environnement JWT_SECRET).
     * Si elle est absente, une clé aléatoire est générée au démarrage : pratique en
     * développement, mais tous les tokens deviennent invalides à chaque redémarrage.
     */
    public JwtUtil(@Value("${app.jwt.secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            byte[] random = new byte[64];
            new SecureRandom().nextBytes(random);
            this.signingKey = Keys.hmacShaKeyFor(random);
            log.warn("JWT_SECRET non défini : clé JWT aléatoire générée. "
                    + "Les utilisateurs devront se reconnecter après chaque redémarrage.");
            return;
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET doit contenir au moins " + MIN_SECRET_BYTES + " octets.");
        }
        this.signingKey = Keys.hmacShaKeyFor(bytes);
    }

    private SecretKey getSigningKey() {
        return signingKey;
    }

    public String generateToken(String username, String role) {
        return Jwts.builder()
                .subject(username)
                .claim("role", role != null ? role : "CLIENT")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public boolean validateToken(String token) {
        try {
            if (token == null || token.isBlank()) {
                return false;
            }
            extractAllClaims(token); // parse + vérif signature
            return !isTokenExpired(token);
        } catch (Exception e) {
            System.out.println("❌ [JwtUtil] " + e.getClass().getSimpleName() + " : " + e.getMessage());
            return false;
        }
    }
}