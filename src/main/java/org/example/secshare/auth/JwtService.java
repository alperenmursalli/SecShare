package org.example.secshare.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.example.secshare.vuln.VulnProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    /**
     * VULN (vuln.jwt.weak-secret): internette dolasan/bilinen zayif bir HMAC
     * anahtari. Saldirgan bunu tahmin/OSINT ile bulup gecerli imzali admin
     * token'i uretebilir. Base64 -> "secret-secret-secret-secret-key!" (32 byte).
     */
    private static final String WEAK_SECRET_BASE64 =
            "c2VjcmV0LXNlY3JldC1zZWNyZXQtc2VjcmV0LWtleSE=";

    private final SecretKey key;
    private final long expirationMinutes;
    private final VulnProperties vuln;

    public JwtService(
            @Value("${app.jwt.secret}") String secretBase64,
            @Value("${app.jwt.expiration-minutes}") long expirationMinutes,
            VulnProperties vuln
    ) {
        this.vuln = vuln;
        this.expirationMinutes = expirationMinutes;

        String effectiveSecret = secretBase64;
        if (vuln.isEnabled() && vuln.getJwt().isWeakSecret()) {
            effectiveSecret = WEAK_SECRET_BASE64;
            log.warn("[vuln] JWT ZAYIF SECRET aktif - token forge edilebilir");
        }

        byte[] secretBytes = Decoders.BASE64.decode(effectiveSecret);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes (base64-decoded).");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public String generateToken(UUID userId, String email, List<String> roles) {
        Instant now = Instant.now();
        Instant exp = now.plus(expirationMinutes, ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Token'i dogrular ve claim'leri doner.
     * VULN (vuln.jwt.allow-none): imza tutmazsa alg:none (imzasiz) token
     * dogrulamadan kabul edilir -> tam auth bypass.
     */
    public Claims parseAndValidate(String token) throws JwtException {
        if (vuln.isEnabled() && vuln.getJwt().isAllowNone()) {
            try {
                return Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
            } catch (JwtException signedFailed) {
                return Jwts.parser()
                        .unsecured()
                        .build()
                        .parseUnsecuredClaims(token)
                        .getPayload();
            }
        }

        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
