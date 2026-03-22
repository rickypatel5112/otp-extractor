package com.project.otp_extractor.services;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedisPIDService {

    private final StringRedisTemplate stringRedisTemplate;
    static final String PWD_ID_PREFIX = "pid:";

    public void addPasswordId(String userEmail) {

        if (userEmail == null || userEmail.isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }

        stringRedisTemplate
                .opsForValue()
                .set(PWD_ID_PREFIX + userEmail, UUID.randomUUID().toString());
    }

    public String getPasswordId(String userEmail) {
        return stringRedisTemplate.opsForValue().get(PWD_ID_PREFIX + userEmail);
    }

    public boolean removePasswordId(String email) {
        if (email == null || email.isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }

        Boolean result = stringRedisTemplate.delete(PWD_ID_PREFIX + email);
        return Boolean.TRUE.equals(result);
    }
}
