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
    private final RedisPIDService redisPIDService;

    @Value("${jwt.secret-key}")
    private String SECRET_KEY;

    public String extractSubject(String jwt) {
        return extractClaim(jwt, Claims::getSubject);
    }

    private String extractPID(String jwt) {
        return extractClaim(jwt, claims -> claims.get("pid").toString());
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
            return TokenType.valueOf(typeStr);
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

    public String generateToken(String subject, TokenType tokenType) {
        return generateToken(new HashMap<>(), subject, tokenType);
    }

    private String generateToken(Map<String, Object> extraClaims, String subject, TokenType tokenType) {

        long expirationMillis = 0;

        if(tokenType == TokenType.ACCESS){
            expirationMillis = 1000 * 60 * 15; // 15 mins
        } else if (tokenType == TokenType.REFRESH) {
            expirationMillis = 1000 * 60 * 30; // 7 days 1000 * 60 * 60 * 24 * 7
        } else if (tokenType == TokenType.RESET_PASSWORD) {
            expirationMillis = 1000 * 60 * 10; // 10 mins
        }

        extraClaims.put("pid", redisPIDService.getPasswordId(subject));
        extraClaims.put("type", tokenType.name());

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

    public boolean isTokenValid(String token, TokenType tokenType) {
        try {
            TokenType extractedType = extractType(token);
            String storedPID = redisPIDService.getPasswordId(extractSubject(token));

            if (isTokenExpired(token) ||
                    extractedType != tokenType ||
                    !storedPID.equals(extractPID(token))) {
                return false;
            }

            final String jti = extractJti(token);
            JwtTokenMetadata metadata = redisTemplate.opsForValue().get(TOKEN_PREFIX + jti);

            // Access token: valid if not expired or revoked
            if (extractedType == TokenType.ACCESS) {
                return metadata == null || !metadata.isRevoked();
            }

            // Refresh token: must exist in Redis and not revoked
            if (extractedType ==  TokenType.REFRESH) {
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
