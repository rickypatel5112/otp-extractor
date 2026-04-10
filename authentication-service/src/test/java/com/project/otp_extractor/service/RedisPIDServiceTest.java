package com.project.otp_extractor.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.project.otp_extractor.services.RedisPIDService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisPIDServiceTest {
    private static final String REDIS_ACCESS_TOKEN_PID_PREFIX = "access-token:pid:";

    @Mock private StringRedisTemplate stringRedisTemplate;

    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks private RedisPIDService redisPIDService;

    @BeforeEach
    void setup() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void shouldStorePasswordIdWithCorrectKeyPrefix() {
        String email = "user@gmail.com";

        redisPIDService.addPasswordId(email);

        verify(valueOperations).set(startsWith(REDIS_ACCESS_TOKEN_PID_PREFIX + email), anyString());
    }

    @Test
    void shouldGenerateDifferentPasswordIdsForSameUserOnMultipleCalls() {
        String email = "user@gmail.com";

        redisPIDService.addPasswordId(email);
        redisPIDService.addPasswordId(email);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        verify(valueOperations, times(2))
                .set(eq(REDIS_ACCESS_TOKEN_PID_PREFIX + email), captor.capture());

        String first = captor.getAllValues().get(0);
        String second = captor.getAllValues().get(1);

        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(first, second);
    }

    @Test
    void shouldReturnPasswordIdWhenKeyExists() {
        String email = "user@gmail.com";
        String expectedPid = "pid123";

        when(valueOperations.get(REDIS_ACCESS_TOKEN_PID_PREFIX + email)).thenReturn(expectedPid);

        String result = redisPIDService.getPasswordId(email);

        assertEquals(expectedPid, result);
    }

    @Test
    void shouldReturnNullWhenPasswordIdDoesNotExist() {
        String email = "user@gmail.com";

        when(valueOperations.get(REDIS_ACCESS_TOKEN_PID_PREFIX + email)).thenReturn(null);

        String result = redisPIDService.getPasswordId(email);

        assertNull(result);
    }

    @Test
    void shouldDeletePasswordIdWithCorrectKey() {
        String email = "user@gmail.com";

        when(stringRedisTemplate.delete(REDIS_ACCESS_TOKEN_PID_PREFIX + email)).thenReturn(true);

        boolean isPasswordIdRemoved = redisPIDService.removePasswordId(email);

        verify(stringRedisTemplate).delete(REDIS_ACCESS_TOKEN_PID_PREFIX + email);
        assertTrue(isPasswordIdRemoved);
    }

    @Test
    void shouldThrowExceptionForAddingPasswordIdWhenEmailIsNull() {
        assertThrows(IllegalArgumentException.class, () -> redisPIDService.addPasswordId(null));
    }

    @Test
    void shouldThrowExceptionForAddingPasswordIdWhenEmailIsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> redisPIDService.addPasswordId(""));
    }

    @Test
    void shouldHandleNullEmailGracefullyWhenGettingPasswordId() {
        when(valueOperations.get(REDIS_ACCESS_TOKEN_PID_PREFIX + null)).thenReturn(null);

        String result = redisPIDService.getPasswordId(null);

        assertNull(result);
    }

    @Test
    void shouldThrowExceptionForRemovePasswordIdWhenEmailIsNull() {
        assertThrows(IllegalArgumentException.class, () -> redisPIDService.removePasswordId(null));
    }

    @Test
    void shouldThrowExceptionForRemovePasswordIdWhenEmailIsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> redisPIDService.removePasswordId(""));
    }

    @Test
    void getPasswordIdReturnsNullForMissingKey() {
        when(valueOperations.get(REDIS_ACCESS_TOKEN_PID_PREFIX + "missing@example.com"))
                .thenReturn(null);

        String pid = redisPIDService.getPasswordId("missing@example.com");
        assertNull(pid);
    }

    @Test
    void removePasswordIdDoesNotFailForMissingKey() {
        when(stringRedisTemplate.delete(REDIS_ACCESS_TOKEN_PID_PREFIX + "nonexistent@example.com"))
                .thenReturn(false);

        assertDoesNotThrow(() -> redisPIDService.removePasswordId("nonexistent@example.com"));

        verify(stringRedisTemplate)
                .delete(REDIS_ACCESS_TOKEN_PID_PREFIX + "nonexistent@example.com");
    }

    @Test
    void shouldReturnFalseForNullValueInRemovePassword() {
        when(stringRedisTemplate.delete(REDIS_ACCESS_TOKEN_PID_PREFIX + "test@gmail.com"))
                .thenReturn(null);

        assertFalse(redisPIDService.removePasswordId("test@gmail.com"));
    }

    @Test
    void addPasswordId_overwritesExistingKey() {
        String email = "user@example.com";

        doAnswer(
                        invocation -> {
                            String key = invocation.getArgument(0);
                            String value = invocation.getArgument(1);
                            assertEquals(REDIS_ACCESS_TOKEN_PID_PREFIX + "" + email, key);
                            assertNotNull(value); // UUID generated
                            return null;
                        })
                .when(valueOperations)
                .set(anyString(), anyString());

        redisPIDService.addPasswordId(email);
        redisPIDService.addPasswordId(email);

        verify(valueOperations, times(2))
                .set(eq(REDIS_ACCESS_TOKEN_PID_PREFIX + "" + email), anyString());
    }
}
