package com.project.otp_extractor.services;

import com.project.otp_extractor.dtos.JwtTokenMetadata;
import com.project.otp_extractor.dtos.TokenType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.project.otp_extractor.services.RedisTokenService.TOKEN_PREFIX;


@Service
@RequiredArgsConstructor
public class JwtService {



    private final RedisTemplate<String, JwtTokenMetadata> redisTemplate;

    @Value("${jwt.secret-key}")
    private String SECRET_KEY;

    public String extractSubject(String jwt) {
        return extractClaim(jwt, Claims::getSubject);
    }

    private Date extractExpiration(String jwt) {
        return extractClaim(jwt, Claims::getExpiration);
    }

    public String extractJti(String jwt) {
        return extractClaim(jwt, Claims::getId);
    }

    private TokenType extractType(String jwt) {
        return extractClaim(jwt, claims -> {
            String typeStr = claims.get("type", String.class);
                return TokenType.valueOf(typeStr.toUpperCase());
        });
    }

    public Date extractIssuedAt(String jwt) {
        return extractClaim(jwt, Claims::getIssuedAt);
    }

    public Date extractExpiresAt(String jwt) {
        return extractClaim(jwt, Claims::getExpiration);
    }

    public <T> T extractClaim(String jwt, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(jwt);
        return claimsResolver.apply(claims);
    }

    public String generateToken(Map<String, Object> extraClaims, String subject, TokenType tokenType) {
        long expirationMillis = (tokenType == TokenType.ACCESS)
                ? 1000 * 60 * 10         // 15 minutes
                : 1000 * 60 * 30; // 7 days 1000 * 60 * 60 * 24 * 7

        if(tokenType == TokenType.REFRESH) {
            extraClaims.put("type", "refresh");
        }else if(tokenType == TokenType.ACCESS) {
            extraClaims.put("type", "access");
        }

        return Jwts
                .builder()
                .setClaims(extraClaims)
                .setId(UUID.randomUUID().toString())
                .setSubject(subject)
                .setIssuedAt(Date.from(Instant.now()))
                .setExpiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateToken(String subject,  TokenType tokenType) {
        return generateToken(new HashMap<>(), subject, tokenType);
    }

    public void revokeToken(String token) {
        String jti = extractJti(token);
        String key = TOKEN_PREFIX + jti;

        JwtTokenMetadata metadata = redisTemplate.opsForValue().get(key);
        if (metadata != null) {
            metadata.setRevoked(true);

            Long ttl = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
            redisTemplate.opsForValue().set(key, metadata, ttl, TimeUnit.MILLISECONDS);
        }
    }

    public boolean isTokenValid(String token) {
        try {
            if (isTokenExpired(token)) {
                return false;
            };

//            String type = extractClaim(token, claims -> claims.get("type", String.class));
            String type = extractType(token).toString();
            final String jti = extractJti(token);
            JwtTokenMetadata metadata = redisTemplate.opsForValue().get(TOKEN_PREFIX + jti);

            // Access token: valid if not expired or revoked
            if ("ACCESS".equals(type)) {
                return metadata == null || !metadata.isRevoked();
            }

            // Refresh token: must exist in Redis and not revoked
            if ("REFRESH".equals(type)) {
                return metadata != null && !metadata.isRevoked();
            }

            // Unknown type — treat as invalid
            return false;

        } catch (JwtException e) {
            // Covers invalid signature, malformed, etc.
            return false;
        }
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Claims extractAllClaims(String jwt) {
        return Jwts
                .parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(jwt)
                .getBody();
    }

    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET_KEY);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
