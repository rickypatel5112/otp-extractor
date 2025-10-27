package com.project.otp_extractor.services;

import com.project.otp_extractor.dtos.JwtTokenMetadata;
import com.project.otp_extractor.dtos.TokenType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisTokenService {
    private final JwtService jwtService;
    private final RedisTemplate<String, JwtTokenMetadata> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    static final String TOKEN_PREFIX = "token:";

    public void storeToken(String token, TokenType tokenType) {

        JwtTokenMetadata tokenMetadata = JwtTokenMetadata.builder()
                .jti(jwtService.extractJti(token))
                .userEmail(jwtService.extractSubject(token))
                .issuedAt(jwtService.extractIssuedAt(token).toInstant())
                .expiresAt(jwtService.extractExpiresAt(token).toInstant())
                .type(tokenType)
                .build();

        String key = TOKEN_PREFIX + tokenMetadata.getJti();

        Duration ttl = Duration.between(Instant.now(), tokenMetadata.getExpiresAt());
        if (!ttl.isNegative() && !ttl.isZero()) {
            redisTemplate.opsForValue().set(key, tokenMetadata, ttl.toMillis(), TimeUnit.MILLISECONDS);
        }

    }

    public void addPasswordIdToRedis(String userEmail){
        stringRedisTemplate.opsForValue().set("user:" + userEmail, UUID.randomUUID().toString());
    }


    public String getPasswordId(String userEmail){
        return stringRedisTemplate.opsForValue().get("user:" + userEmail);
    }

}
