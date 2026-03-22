package com.project.otp_extractor.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.project.otp_extractor.dtos.JwtTokenMetadata;
import com.project.otp_extractor.dtos.TokenType;
import com.project.otp_extractor.services.JwtService;
import com.project.otp_extractor.services.RedisPIDService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.lang.reflect.Field;
import java.security.Key;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class JwtServiceTest {

    private static final String TOKEN_PREFIX = "token:";
    private static final String SECRET =
            "c29tZS1zZWNyZXQta2V5LTI1Ni1iaXRzLWxvbmctajF3YTRkNmI4Y3NoOGVkOA==";

    @Mock private RedisTemplate<String, JwtTokenMetadata> redisTemplate;
    @Mock private ValueOperations<String, JwtTokenMetadata> valueOperations;
    @Mock private RedisPIDService redisPIDService;

    @InjectMocks private JwtService jwtService;

    private Key key;

    @BeforeEach
    void setup() throws Exception {
        MockitoAnnotations.openMocks(this);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        Field field = JwtService.class.getDeclaredField("SECRET_KEY");
        field.setAccessible(true);
        field.set(jwtService, SECRET);

        key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
    }

    // =========================
    // Helper for real JWTs
    // =========================
    private String buildToken(String subject, String pid, String type, long expiryMillis) {
        return Jwts.builder()
                .setSubject(subject)
                .claim("pid", pid)
                .claim("type", type)
                .setId("jti-" + System.nanoTime())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiryMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    void shouldGenerateAccessTokenWithCorrectSubjectPidAndType() {
        when(redisPIDService.getPasswordId("user@gmail.com")).thenReturn("pid123");

        String token = jwtService.generateToken("user@gmail.com", TokenType.ACCESS);

        assertEquals("user@gmail.com", jwtService.extractSubject(token));

        String pid = jwtService.extractClaim(token, c -> c.get("pid").toString());
        String type = jwtService.extractClaim(token, c -> c.get("type").toString());

        assertEquals("pid123", pid);
        assertEquals(TokenType.ACCESS.name(), type);
    }

    @Test
    void shouldReturnTrueWhenAccessTokenValidAndNotRevoked() {
        when(redisPIDService.getPasswordId("user@gmail.com")).thenReturn("pid");

        String token = jwtService.generateToken("user@gmail.com", TokenType.ACCESS);
        String key = TOKEN_PREFIX + jwtService.extractJti(token);

        when(valueOperations.get(key)).thenReturn(null);

        assertTrue(jwtService.isTokenValid(token, TokenType.ACCESS));
    }

    @Test
    void shouldReturnFalseWhenTokenIsActuallyExpired() {
        when(redisPIDService.getPasswordId("user@gmail.com")).thenReturn("pid");

        String token = buildToken("user@gmail.com", "pid", TokenType.ACCESS.name(), -1000);

        assertFalse(jwtService.isTokenValid(token, TokenType.ACCESS));
    }

    @Test
    void shouldReturnFalseWhenTokenTypeDoesNotMatchExpected() {
        when(redisPIDService.getPasswordId("user@gmail.com")).thenReturn("pid");

        String token = jwtService.generateToken("user@gmail.com", TokenType.ACCESS);

        assertFalse(jwtService.isTokenValid(token, TokenType.REFRESH));
    }

    @Test
    void shouldReturnFalseWhenPasswordIdChangedAfterTokenIssued() {
        when(redisPIDService.getPasswordId("user@gmail.com")).thenReturn("pid1");

        String token = jwtService.generateToken("user@gmail.com", TokenType.ACCESS);

        when(redisPIDService.getPasswordId("user@gmail.com")).thenReturn("pid2");

        assertFalse(jwtService.isTokenValid(token, TokenType.ACCESS));
    }

    @Test
    void shouldReturnFalseWhenStoredPidIsNull() {
        when(redisPIDService.getPasswordId("user@gmail.com")).thenReturn("pid");

        String token = jwtService.generateToken("user@gmail.com", TokenType.ACCESS);

        when(redisPIDService.getPasswordId("user@gmail.com")).thenReturn(null);

        assertFalse(jwtService.isTokenValid(token, TokenType.ACCESS));
    }

    @Test
    void shouldReturnFalseWhenAccessTokenIsRevokedInRedis() {
        when(redisPIDService.getPasswordId("user@gmail.com")).thenReturn("pid");

        String token = jwtService.generateToken("user@gmail.com", TokenType.ACCESS);
        String key = TOKEN_PREFIX + jwtService.extractJti(token);

        JwtTokenMetadata metadata =
                JwtTokenMetadata.builder().revoked(true).type(TokenType.ACCESS).build();

        when(valueOperations.get(key)).thenReturn(metadata);

        assertFalse(jwtService.isTokenValid(token, TokenType.ACCESS));
    }

    @Test
    void shouldReturnTrueWhenRefreshTokenExistsAndNotRevoked() {
        when(redisPIDService.getPasswordId("user@gmail.com")).thenReturn("pid");

        String token = jwtService.generateToken("user@gmail.com", TokenType.REFRESH);
        String key = TOKEN_PREFIX + jwtService.extractJti(token);

        JwtTokenMetadata metadata =
                JwtTokenMetadata.builder().revoked(false).type(TokenType.REFRESH).build();

        when(valueOperations.get(key)).thenReturn(metadata);

        assertTrue(jwtService.isTokenValid(token, TokenType.REFRESH));
    }

    @Test
    void shouldReturnFalseWhenRefreshTokenMissingFromRedis() {
        when(redisPIDService.getPasswordId("user@gmail.com")).thenReturn("pid");

        String token = jwtService.generateToken("user@gmail.com", TokenType.REFRESH);

        when(valueOperations.get(TOKEN_PREFIX + jwtService.extractJti(token))).thenReturn(null);

        assertFalse(jwtService.isTokenValid(token, TokenType.REFRESH));
    }

    @Test
    void shouldReturnFalseWhenRefreshTokenRevoked() {
        when(redisPIDService.getPasswordId("user@gmail.com")).thenReturn("pid");

        String token = jwtService.generateToken("user@gmail.com", TokenType.REFRESH);
        String key = TOKEN_PREFIX + jwtService.extractJti(token);

        JwtTokenMetadata metadata =
                JwtTokenMetadata.builder().revoked(true).type(TokenType.REFRESH).build();

        when(valueOperations.get(key)).thenReturn(metadata);

        assertFalse(jwtService.isTokenValid(token, TokenType.REFRESH));
    }

    @Test
    void shouldReturnTrueForResetPasswordTokenWhenValidRegardlessOfRedis() {
        when(redisPIDService.getPasswordId("user@gmail.com")).thenReturn("pid");

        String token = jwtService.generateToken("user@gmail.com", TokenType.RESET_PASSWORD);

        assertTrue(jwtService.isTokenValid(token, TokenType.RESET_PASSWORD));
    }

    @Test
    void shouldReturnFalseWhenTypeClaimIsInvalidEnum() {
        when(redisPIDService.getPasswordId("user@gmail.com")).thenReturn("pid");

        String token = buildToken("user@gmail.com", "pid", "INVALID", 60000);

        assertFalse(jwtService.isTokenValid(token, TokenType.ACCESS));
    }

    @Test
    void shouldReturnFalseWhenTypeClaimMissing() {
        when(redisPIDService.getPasswordId("user@gmail.com")).thenReturn("pid");

        String token =
                Jwts.builder()
                        .setSubject("user@gmail.com")
                        .claim("pid", "pid")
                        .setId("jti123")
                        .setIssuedAt(new Date())
                        .setExpiration(new Date(System.currentTimeMillis() + 60000))
                        .signWith(key, SignatureAlgorithm.HS256)
                        .compact();

        assertFalse(jwtService.isTokenValid(token, TokenType.ACCESS));
    }

    @Test
    void shouldReturnFalseWhenPidClaimMissing() {
        when(redisPIDService.getPasswordId("user@gmail.com")).thenReturn("pid");

        String token =
                Jwts.builder()
                        .setSubject("user@gmail.com")
                        .claim("type", TokenType.ACCESS.name())
                        .setId("jti123")
                        .setIssuedAt(new Date())
                        .setExpiration(new Date(System.currentTimeMillis() + 60000))
                        .signWith(key, SignatureAlgorithm.HS256)
                        .compact();

        assertFalse(jwtService.isTokenValid(token, TokenType.ACCESS));
    }

    @Test
    void shouldIgnoreMetadataTypeMismatchForAccessToken() {
        when(redisPIDService.getPasswordId("user@gmail.com")).thenReturn("pid");

        String token = jwtService.generateToken("user@gmail.com", TokenType.ACCESS);
        String key = TOKEN_PREFIX + jwtService.extractJti(token);

        JwtTokenMetadata metadata =
                JwtTokenMetadata.builder().type(TokenType.REFRESH).revoked(false).build();

        when(valueOperations.get(key)).thenReturn(metadata);

        assertTrue(jwtService.isTokenValid(token, TokenType.ACCESS));
    }

    @Test
    void shouldSetCorrectExpiration_ForAccessToken() {
        when(redisPIDService.getPasswordId("user@gmail.com")).thenReturn("pid");

        String token = jwtService.generateToken("user@gmail.com", TokenType.ACCESS);

        Date issuedAt = jwtService.extractIssuedAt(token);
        Date expiresAt = jwtService.extractExpiresAt(token);

        long diff = expiresAt.getTime() - issuedAt.getTime();

        assertTrue(Math.abs(diff - (15 * 60 * 1000)) < 1000);
    }

    @Test
    void shouldMarkTokenAsRevokedAndPreserveExistingTTL() {
        when(redisPIDService.getPasswordId("user@gmail.com")).thenReturn("pid");

        String token = jwtService.generateToken("user@gmail.com", TokenType.ACCESS);
        String key = TOKEN_PREFIX + jwtService.extractJti(token);

        JwtTokenMetadata metadata = JwtTokenMetadata.builder().revoked(false).build();

        when(valueOperations.get(key)).thenReturn(metadata);
        when(redisTemplate.getExpire(key, TimeUnit.MILLISECONDS)).thenReturn(5000L);

        jwtService.revokeToken(token);

        assertTrue(metadata.isRevoked());
        verify(valueOperations).set(key, metadata, 5000L, TimeUnit.MILLISECONDS);
    }

    @Test
    void shouldDoNothingWhenTokenNotFoundDuringRevoke() {
        when(redisPIDService.getPasswordId("user@gmail.com")).thenReturn("pid");

        String token = jwtService.generateToken("user@gmail.com", TokenType.ACCESS);

        when(valueOperations.get(TOKEN_PREFIX + jwtService.extractJti(token))).thenReturn(null);

        jwtService.revokeToken(token);

        verify(valueOperations, never()).set(any(), any(), anyLong(), any());
    }
}
