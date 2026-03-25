package com.project.otp_extractor.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.project.otp_extractor.config.FrontendConfig;
import com.project.otp_extractor.dtos.*;
import com.project.otp_extractor.exceptions.UserAlreadyExistsException;
import com.project.otp_extractor.exceptions.UserNotFoundException;
import com.project.otp_extractor.services.*;
import com.project.otp_extractor.user.User;
import com.project.otp_extractor.user.UserRepository;
import io.jsonwebtoken.JwtException;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private RedisTokenService redisTokenService;
    @Mock private RedisPIDService redisPIDService;
    @Mock private FrontendConfig frontendConfig;
    @Mock private PasswordResetProducerService passwordResetProducerService;

    @InjectMocks private AuthenticationService authenticationService;

    private RegisterRequest registerRequest;

    @BeforeEach
    void setup() {
        registerRequest =
                RegisterRequest.builder()
                        .email("test@gmail.com")
                        .firstname("John")
                        .lastname("Doe")
                        .password("Password1@")
                        .build();
    }

    @Test
    void shouldEncodePasswordAndSaveUserAndStorePasswordIdWhenRegisteringNewUser() {
        when(userRepository.findByEmail(registerRequest.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(registerRequest.getPassword())).thenReturn("encoded");

        authenticationService.register(registerRequest);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User savedUser = captor.getValue();
        assertEquals("encoded", savedUser.getPassword());
        assertEquals(registerRequest.getEmail(), savedUser.getEmail());

        verify(redisPIDService).addPasswordId(registerRequest.getEmail());
    }

    @Test
    void shouldThrowUserAlreadyExistsExceptionWhenEmailAlreadyExists() {
        when(userRepository.findByEmail(registerRequest.getEmail()))
                .thenReturn(Optional.of(new User()));

        assertThrows(
                UserAlreadyExistsException.class,
                () -> authenticationService.register(registerRequest));
    }

    @Test
    void shouldTranslateDataIntegrityViolationToUserAlreadyExistsException() {
        when(userRepository.findByEmail(registerRequest.getEmail())).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenThrow(DataIntegrityViolationException.class);

        assertThrows(
                UserAlreadyExistsException.class,
                () -> authenticationService.register(registerRequest));
    }

    @Test
    void shouldAuthenticateUserGenerateTokensAndStoreRefreshToken() {
        AuthenticationRequest request = new AuthenticationRequest("test@gmail.com", "Password1@");

        when(jwtService.generateToken("test@gmail.com", TokenType.ACCESS)).thenReturn("access");
        when(jwtService.generateToken("test@gmail.com", TokenType.REFRESH)).thenReturn("refresh");

        AuthenticationService.TokenPair result = authenticationService.authenticate(request);

        assertEquals("access", result.accessToken());
        assertEquals("refresh", result.refreshToken());

        verify(authenticationManager).authenticate(any());
        verify(jwtService).generateToken("test@gmail.com", TokenType.ACCESS);
        verify(jwtService).generateToken("test@gmail.com", TokenType.REFRESH);
        verify(redisTokenService).addRefreshToken("refresh");
    }

    @Test
    void shouldPropagateExceptionWhenAuthenticationFails() {
        AuthenticationRequest request = new AuthenticationRequest("test@gmail.com", "wrong");

        doThrow(new RuntimeException("auth failed"))
                .when(authenticationManager)
                .authenticate(any());

        assertThrows(RuntimeException.class, () -> authenticationService.authenticate(request));
    }

    @Test
    void shouldThrowJwtExceptionWhenRefreshTokenInvalid() {
        when(jwtService.isTokenValid("token", TokenType.REFRESH)).thenReturn(false);

        assertThrows(
                JwtException.class, () -> authenticationService.issueNewNonExpiredToken("token"));
    }

    @Test
    void shouldReuseRefreshTokenAndIssueNewAccessTokenWhenNotNearExpiry() {
        when(jwtService.isTokenValid("refresh", TokenType.REFRESH)).thenReturn(true);
        when(jwtService.extractSubject("refresh")).thenReturn("email");
        when(jwtService.generateToken("email", TokenType.ACCESS)).thenReturn("newAccess");

        Date future = new Date(System.currentTimeMillis() + 60 * 60 * 1000);
        when(jwtService.extractClaim(eq("refresh"), any())).thenReturn(future);

        AuthenticationService.TokenPair result =
                authenticationService.issueNewNonExpiredToken("refresh");

        assertEquals("newAccess", result.accessToken());
        assertEquals("refresh", result.refreshToken());

        verify(jwtService, never()).generateToken("email", TokenType.REFRESH);
    }

    @Test
    void shouldGenerateNewRefreshTokenWhenNearExpiry() {
        when(jwtService.isTokenValid("refresh", TokenType.REFRESH)).thenReturn(true);
        when(jwtService.extractSubject("refresh")).thenReturn("email");
        when(jwtService.generateToken("email", TokenType.ACCESS)).thenReturn("newAccess");
        when(jwtService.generateToken("email", TokenType.REFRESH)).thenReturn("newRefresh");

        Date nearExpiry = new Date(System.currentTimeMillis() + 5 * 60 * 1000);
        when(jwtService.extractClaim(eq("refresh"), any())).thenReturn(nearExpiry);

        AuthenticationService.TokenPair result =
                authenticationService.issueNewNonExpiredToken("refresh");

        assertEquals("newRefresh", result.refreshToken());

        verify(jwtService).generateToken("email", TokenType.REFRESH);
    }

    @Test
    void shouldRevokeTokensAndBlacklistAccessTokenOnLogout() {
        authenticationService.logout("Bearer accessToken", "refreshToken");

        verify(redisTokenService).addAccessToken("accessToken");
        verify(jwtService).revokeToken("accessToken");
        verify(jwtService).revokeToken("refreshToken");
    }

    @Test
    void shouldSendResetMessageWithCorrectEmailWhenRequestIsValid() {
        ForgotPasswordRequest request =
                new ForgotPasswordRequest("test@gmail.com", "http://frontend");

        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(new User()));
        when(jwtService.generateToken("test@gmail.com", TokenType.RESET_PASSWORD))
                .thenReturn("token");
        when(frontendConfig.isUrlAllowed("http://frontend")).thenReturn(true);

        authenticationService.forgotPassword(request);

        verify(passwordResetProducerService).sendMessage(any());
    }

    @Test
    void shouldNotSendResetMessageWhenUserDoesNotExist() {
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.empty());

        authenticationService.forgotPassword(new ForgotPasswordRequest("test@gmail.com", "url"));

        verifyNoInteractions(passwordResetProducerService);
    }

    @Test
    void shouldNotSendResetMessageWhenUrlNotAllowed() {
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(new User()));
        when(frontendConfig.isUrlAllowed("bad-url")).thenReturn(false);

        authenticationService.forgotPassword(
                new ForgotPasswordRequest("test@gmail.com", "bad-url"));

        verifyNoInteractions(passwordResetProducerService);
    }

    @Test
    void shouldUpdatePasswordAndStorePasswordIdWhenResetSuccessful() {
        when(jwtService.isTokenValid("token", TokenType.RESET_PASSWORD)).thenReturn(true);
        when(jwtService.extractSubject("token")).thenReturn("email");
        when(passwordEncoder.encode("newPass")).thenReturn("encoded");

        User user = new User();
        when(userRepository.findByEmail("email")).thenReturn(Optional.of(user));

        boolean result =
                authenticationService.resetPassword("token", new ResetPasswordRequest("newPass"));

        assertTrue(result);
        verify(redisPIDService).addPasswordId("email");
    }

    @Test
    void shouldThrowJwtExceptionWhenResetTokenInvalid() {
        when(jwtService.isTokenValid(any(), any())).thenReturn(false);

        assertThrows(
                JwtException.class,
                () ->
                        authenticationService.resetPassword(
                                "token", new ResetPasswordRequest("pass")));
    }

    @Test
    void shouldReturnFalseWhenUserNotFoundDuringReset() {
        when(jwtService.isTokenValid(any(), any())).thenReturn(true);
        when(jwtService.extractSubject(any())).thenReturn("email");
        when(userRepository.findByEmail("email")).thenReturn(Optional.empty());

        boolean result =
                authenticationService.resetPassword("token", new ResetPasswordRequest("pass"));

        assertFalse(result);
    }

    @Test
    void shouldDeleteUserAndRemovePasswordIdWhenUserExists() {
        when(userRepository.deleteByEmail("email")).thenReturn(1L);

        assertDoesNotThrow(() -> authenticationService.deleteAccount("email"));

        verify(redisPIDService).removePasswordId("email");
    }

    @Test
    void shouldThrowExceptionWhenRedisFailsDuringDelete() {
        when(userRepository.deleteByEmail("email")).thenReturn(1L);
        doThrow(new RuntimeException()).when(redisPIDService).removePasswordId("email");

        assertThrows(RuntimeException.class, () -> authenticationService.deleteAccount("email"));
    }

    @Test
    void shouldThrowUserNotFoundExceptionWhenUserDoesNotExistOnDelete() {
        when(userRepository.deleteByEmail("email")).thenReturn(0L);

        assertThrows(
                UserNotFoundException.class, () -> authenticationService.deleteAccount("email"));

        verify(redisPIDService, never()).removePasswordId(any());
    }
}
