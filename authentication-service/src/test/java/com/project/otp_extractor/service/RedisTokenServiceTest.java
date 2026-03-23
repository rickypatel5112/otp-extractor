package com.project.otp_extractor.service;

import com.project.otp_extractor.dtos.JwtTokenMetadata;
import com.project.otp_extractor.dtos.TokenType;
import com.project.otp_extractor.services.JwtService;
import com.project.otp_extractor.services.RedisTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class RedisTokenServiceTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private RedisTemplate<String, JwtTokenMetadata> redisTemplate;

    @Mock
    private ValueOperations<String, JwtTokenMetadata> valueOperations;

    @InjectMocks
    private RedisTokenService redisTokenService;

    private final String token = "test-token";
    private final String jti = "jti-123";
    private final String email = "user@gmail.com";

    @BeforeEach
    void setup() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void shouldStoreAccessTokenWithCorrectMetadataAndTTL() {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(60);

        when(jwtService.extractJti(token)).thenReturn(jti);
        when(jwtService.extractSubject(token)).thenReturn(email);
        when(jwtService.extractIssuedAt(token)).thenReturn(Date.from(now));
        when(jwtService.extractExpiresAt(token)).thenReturn(Date.from(expiry));

        redisTokenService.addAccessToken(token);

        ArgumentCaptor<JwtTokenMetadata> metadataCaptor =
                ArgumentCaptor.forClass(JwtTokenMetadata.class);

        verify(valueOperations).set(
                eq("token:" + jti),
                metadataCaptor.capture(),
                anyLong(),
                eq(TimeUnit.MILLISECONDS)
        );

        JwtTokenMetadata metadata = metadataCaptor.getValue();

        assert metadata.getJti().equals(jti);
        assert metadata.getUserEmail().equals(email);
        assert metadata.getType() == TokenType.ACCESS;
    }

    @Test
    void shouldStoreRefreshTokenWithCorrectType() {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(120);

        when(jwtService.extractJti(token)).thenReturn(jti);
        when(jwtService.extractSubject(token)).thenReturn(email);
        when(jwtService.extractIssuedAt(token)).thenReturn(Date.from(now));
        when(jwtService.extractExpiresAt(token)).thenReturn(Date.from(expiry));

        redisTokenService.addRefreshToken(token);

        ArgumentCaptor<JwtTokenMetadata> metadataCaptor =
                ArgumentCaptor.forClass(JwtTokenMetadata.class);

        verify(valueOperations).set(
                eq("token:" + jti),
                metadataCaptor.capture(),
                anyLong(),
                eq(TimeUnit.MILLISECONDS)
        );

        JwtTokenMetadata metadata = metadataCaptor.getValue();

        assert metadata.getType() == TokenType.REFRESH;
    }

    @Test
    void shouldNotStoreTokenWhenExpired() {
        Instant now = Instant.now();
        Instant expired = now.minusSeconds(10);

        when(jwtService.extractJti(token)).thenReturn(jti);
        when(jwtService.extractSubject(token)).thenReturn(email);
        when(jwtService.extractIssuedAt(token)).thenReturn(Date.from(now));
        when(jwtService.extractExpiresAt(token)).thenReturn(Date.from(expired));

        redisTokenService.addAccessToken(token);

        verify(valueOperations, never()).set(any(), any(), anyLong(), any());
    }

    @Test
    void shouldNotStoreTokenWhenTTLIsZero() {
        Instant now = Instant.now();

        when(jwtService.extractJti(token)).thenReturn(jti);
        when(jwtService.extractSubject(token)).thenReturn(email);
        when(jwtService.extractIssuedAt(token)).thenReturn(Date.from(now));
        when(jwtService.extractExpiresAt(token)).thenReturn(Date.from(now)); // TTL = 0

        redisTokenService.addAccessToken(token);

        verify(valueOperations, never()).set(any(), any(), anyLong(), any());
    }
}