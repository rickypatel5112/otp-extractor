package com.project.otp_extractor.services;

import com.project.otp_extractor.config.FrontendConfig;
import com.project.otp_extractor.dtos.*;
import com.project.otp_extractor.exceptions.UserAlreadyExistsException;
import com.project.otp_extractor.exceptions.UserNotFoundException;
import com.project.otp_extractor.user.User;
import com.project.otp_extractor.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RedisTokenService redisTokenService;
    private final RedisPIDService redisPIDService;
    private final FrontendConfig frontendConfig;
    private final PasswordResetProducerService passwordResetProducerService;

    public record TokenPair(String accessToken, String refreshToken) {}

    @Transactional
    public void register(RegisterRequest request) throws UserAlreadyExistsException {
        log.info("Registering new user with email: {}", request.getEmail());

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            log.warn("Registration failed — email already exists: {}", request.getEmail());
            throw new UserAlreadyExistsException("Email already exists: " + request.getEmail());
        }

        var user =
                User.builder()
                        .email(request.getEmail())
                        .firstname(request.getFirstname())
                        .lastname(request.getLastname())
                        .password(passwordEncoder.encode(request.getPassword()))
                        .build();

        try {
            userRepository.save(user);
            log.info("User registered successfully: {}", request.getEmail());
        } catch (DataIntegrityViolationException e) {
            log.warn(
                    "Registration failed — data integrity violation for email: {}",
                    request.getEmail());
            throw new UserAlreadyExistsException("Email already exists: " + request.getEmail());
        }

        redisPIDService.addPasswordId(request.getEmail());
        log.debug("Password ID added to Redis for: {}", request.getEmail());
    }

    public TokenPair authenticate(AuthenticationRequest request) {
        log.info("Authentication attempt for email: {}", request.getEmail());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        String subject = request.getEmail();
        var accessToken = jwtService.generateToken(subject, TokenType.ACCESS);
        var refreshToken = jwtService.generateToken(subject, TokenType.REFRESH);

        redisTokenService.addRefreshToken(refreshToken);
        log.info("Authentication successful for email: {}", request.getEmail());

        return new TokenPair(accessToken, refreshToken);
    }

    public TokenPair issueNewNonExpiredToken(String refreshToken) {
        log.debug("Token refresh requested");

        if (!jwtService.isTokenValid(refreshToken, TokenType.REFRESH)) {
            log.warn("Token refresh failed — invalid refresh token");
            throw new JwtException("Invalid refresh token");
        }

        String subject = jwtService.extractSubject(refreshToken);
        log.debug("Issuing new access token for subject: {}", subject);

        String newAccessToken = jwtService.generateToken(subject, TokenType.ACCESS);

        Date expirationDate = jwtService.extractClaim(refreshToken, Claims::getExpiration);
        long fifteenMinutes = 15 * 60 * 1000;
        Date threshold = new Date(System.currentTimeMillis() + fifteenMinutes);

        if (expirationDate.before(threshold)) {
            log.debug(
                    "Refresh token nearing expiry — rotating refresh token for subject: {}",
                    subject);
            refreshToken = jwtService.generateToken(subject, TokenType.REFRESH);
        }

        log.info("Token refresh successful for subject: {}", subject);
        return new TokenPair(newAccessToken, refreshToken);
    }

    public void logout(String accessToken, String refreshToken) {
        log.info("Logout requested");

        if (accessToken != null && accessToken.startsWith("Bearer ")) {
            accessToken = accessToken.substring(7);
        }

        // TODO: Check if accessToken and refreshToken are not null & are valid
        redisTokenService.addAccessToken(accessToken);
        jwtService.revokeToken(accessToken);
        jwtService.revokeToken(refreshToken);

        log.info("Logout successful — tokens revoked");
    }

    public void forgotPassword(@RequestBody ForgotPasswordRequest request) {
        log.info("Forgot password requested for email: {}", request.getEmail());

        String email = request.getEmail();
        String frontEndUrl = request.getFrontEndUrl();

        if (!frontendConfig.isUrlAllowed(frontEndUrl)) {
            log.warn("Rejected forgot password request — disallowed frontend URL: {}", frontEndUrl);
            return;
        }

        if (userRepository.findByEmail(email).isPresent()) {
            String resetToken = jwtService.generateToken(email, TokenType.RESET_PASSWORD);

            var forgotPasswordResponse =
                    ForgotPasswordResponse.builder()
                            .resetToken(resetToken)
                            .email(email)
                            .frontEndUrl(frontEndUrl)
                            .build();

            passwordResetProducerService.sendMessage(forgotPasswordResponse);
            log.info("Password reset message sent for email: {}", email);
        } else {
            log.debug("Forgot password requested for unknown email: {}", email);
        }
    }

    @Transactional
    public boolean resetPassword(String token, ResetPasswordRequest resetPasswordRequest) {
        log.info("Password reset attempt");

        if (!jwtService.isTokenValid(token, TokenType.RESET_PASSWORD)) {
            log.warn("Password reset failed — invalid or expired token");
            throw new JwtException("Invalid token");
        }

        String userEmail = jwtService.extractSubject(token);
        log.debug("Processing password reset for email: {}", userEmail);

        boolean isSuccess =
                userRepository
                        .findByEmail(userEmail)
                        .map(
                                user -> {
                                    user.setPassword(
                                            passwordEncoder.encode(
                                                    resetPasswordRequest.password()));
                                    userRepository.save(user);
                                    return true;
                                })
                        .orElse(false);

        if (isSuccess) {
            log.info("Password updated successfully for email: {}", userEmail);
            try {
                redisPIDService.addPasswordId(userEmail);
                log.debug("Password ID rotated in Redis for: {}", userEmail);
            } catch (Exception e) {
                log.error(
                        "Redis operation failed during password reset for email: {} — rolling back",
                        userEmail,
                        e);
                throw new RuntimeException("Redis operation failed, rolling back", e);
            }
        } else {
            log.warn("Password reset failed — user not found for email: {}", userEmail);
        }

        return isSuccess;
    }

    @Transactional
    public void deleteAccount(String authorizationHeader, String refreshToken) {
        log.info("Account deletion requested");

        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            log.warn("Account deletion failed — invalid Authorization header");
            throw new IllegalArgumentException("Invalid Authorization header");
        }

        String accessToken = authorizationHeader.substring(7);
        String tokenEmail = jwtService.extractSubject(accessToken);
        log.debug("Processing account deletion for email: {}", tokenEmail);

        try {
            redisPIDService.removePasswordId(tokenEmail);
            jwtService.revokeToken(accessToken);
            jwtService.revokeToken(refreshToken);
            log.debug("Redis data and tokens cleared for email: {}", tokenEmail);
        } catch (Exception e) {
            log.error(
                    "Failed to remove Redis data during account deletion for email: {}",
                    tokenEmail,
                    e);
            throw new RuntimeException("Failed to remove Redis data", e);
        }

        long deletedCount = userRepository.deleteByEmail(tokenEmail);

        if (deletedCount == 0) {
            log.warn("Account deletion failed — no user found with email: {}", tokenEmail);
            throw new UserNotFoundException("No user found with email: " + tokenEmail);
        }

        log.info("Account deleted successfully for email: {}", tokenEmail);
    }
}
