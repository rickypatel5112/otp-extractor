package com.project.otp_extractor.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RedisPIDService {

    private final StringRedisTemplate stringRedisTemplate;
    static final String PWD_ID_PREFIX = "pid:";

    public void addPasswordId(String userEmail){
        stringRedisTemplate.opsForValue().set(PWD_ID_PREFIX + userEmail, UUID.randomUUID().toString());
    }

    public String getPasswordId(String userEmail){
        return stringRedisTemplate.opsForValue().get(PWD_ID_PREFIX + userEmail);
    }
}
