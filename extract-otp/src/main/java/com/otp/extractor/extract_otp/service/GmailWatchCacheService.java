package com.otp.extractor.extract_otp.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

@Service
public class GmailWatchCacheService {
    static final String WATCH_HISTORY_PREFIX = "gmail:watch:historyId:";
    private final RedisTemplate<String, BigInteger> redisTemplate;

    public GmailWatchCacheService(
            @Qualifier("gmailWatchStateRedisTemplate")
            RedisTemplate<String, BigInteger> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void addWatchHistoryId(String email, BigInteger watchHistoryId, Long expirationTimeStamp) {
        redisTemplate.opsForValue().set(
                WATCH_HISTORY_PREFIX + email,
                watchHistoryId,
                convertTimeStampToTTLSeconds(expirationTimeStamp),
                TimeUnit.SECONDS
        );
    }

    public BigInteger getWatchHistoryId(String email) {
        return redisTemplate.opsForValue().get(WATCH_HISTORY_PREFIX + email);
    }

    public Long getWatchExpirationInSeconds(String email) {
        return redisTemplate.getExpire(WATCH_HISTORY_PREFIX + email, TimeUnit.SECONDS);
    }

    private Long convertTimeStampToTTLSeconds(long expirationTimeStamp){
        return (expirationTimeStamp - System.currentTimeMillis()) / 1000;
    }
}
