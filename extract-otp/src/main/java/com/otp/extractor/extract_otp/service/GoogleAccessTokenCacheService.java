package com.otp.extractor.extract_otp.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class GoogleAccessTokenCacheService {
    final static String ACCESS_TOKEN_PREFIX = "google:accessToken:";
    private final StringRedisTemplate accessTokenRedisTemplate;
    private final EncryptionService encryptionService;

    public void addAccessToken(String email, String accessToken, long expiresIn, TimeUnit timeUnit) {
        String encryptedToken = encryptionService.encrypt(accessToken);
        accessTokenRedisTemplate.opsForValue().set(ACCESS_TOKEN_PREFIX + email, encryptedToken, expiresIn, timeUnit);
    }

    public String getAccessToken(String email) {
        String accessToken = accessTokenRedisTemplate.opsForValue().get(ACCESS_TOKEN_PREFIX + email);
        return encryptionService.decrypt(accessToken);
    }

    public Long getExpiresInSeconds(String email) {
        return accessTokenRedisTemplate.getExpire(ACCESS_TOKEN_PREFIX + email, TimeUnit.SECONDS);
    }
}
