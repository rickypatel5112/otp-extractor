package com.project.otp_extractor.services;

import com.project.otp_extractor.config.FrontendConfig;
import com.project.otp_extractor.dtos.*;
import com.project.otp_extractor.exceptions.UserAlreadyExistsException;
import com.project.otp_extractor.user.User;
import com.project.otp_extractor.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RedisTokenService redisTokenService;
    private final RedisPIDService redisPIDService;
    private final FrontendConfig frontendConfig;
    private final PasswordResetProducer passwordResetProducer;

    public record TokenPair(String accessToken, String refreshToken) {
    }

    @Transactional
    public void register(RegisterRequest request) throws UserAlreadyExistsException {

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new UserAlreadyExistsException("Email already exists: " + request.getEmail());
        }

        var user = User.builder()
                .email(request.getEmail())
                .firstname(request.getFirstname())
                .lastname(request.getLastname())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        userRepository.save(user);
        redisPIDService.addPasswordId(request.getEmail());
    }

    public TokenPair authenticate(AuthenticationRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        /**
         We don't need this, we can be sure that the user would exist for that token.
         TODO: The user when they delete their account, we'll revoke all their access and refresh tokens. This way
         it won't pass the revocation check
         */
//        var user = userRepository.findByEmail(request.getEmail()).orElseThrow();

        String subject = request.getEmail();

        var accessToken = jwtService.generateToken(subject, TokenType.ACCESS);
        var refreshToken = jwtService.generateToken(subject, TokenType.REFRESH);

        redisTokenService.addRefreshToken(refreshToken);
        return new TokenPair(accessToken, refreshToken);
    }

    public TokenPair issueNewNonExpiredToken(String refreshToken) {

        // Check if refresh token is expired
        if (!jwtService.isTokenValid(refreshToken, TokenType.REFRESH)) {
            throw new JwtException("Invalid refresh token");
        }

        String subject = jwtService.extractSubject(refreshToken);

        // Generate new access token
        String newAccessToken = jwtService.generateToken(subject, TokenType.ACCESS);

        // Determine if refresh token should be rotated
        Date expirationDate = jwtService.extractClaim(refreshToken, Claims::getExpiration);
        long fifteenMinutes = 15 * 60 * 1000;
        Date threshold = new Date(System.currentTimeMillis() + fifteenMinutes);

        if (expirationDate.before(threshold)) {
            // Less than 15 minutes remaining → issue a new refresh token
            refreshToken = jwtService.generateToken(subject, TokenType.REFRESH);
        }

        return new TokenPair(newAccessToken, refreshToken);
    }

    public void logout(String authorizationHeader, String refreshToken) {

        String accessToken = null;
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            accessToken = authorizationHeader.substring(7);
        }

        //TODO: Check if accessToken and refreshToken are not null & are valid
        redisTokenService.addAccessToken(accessToken);
        jwtService.revokeToken(accessToken);
        jwtService.revokeToken(refreshToken);
    }

    public void forgotPassword(@RequestBody ForgotPasswordRequest request){

        String email = request.getEmail();
        String frontEndUrl = request.getFrontEndUrl();

        if (userRepository.findByEmail(email).isPresent()) {
            String resetToken = jwtService.generateToken(email, TokenType.RESET_PASSWORD);

            if(!frontendConfig.isUrlAllowed(frontEndUrl)) return;

            var forgotPasswordResponse = ForgotPasswordResponse
                                            .builder()
                                            .resetToken(resetToken)
                                            .email(email)
                                            .frontEndUrl(frontEndUrl)
                                            .build();

            passwordResetProducer.sendMessage(forgotPasswordResponse);
        }
    }

    @Transactional
    public boolean resetPassword(String token, ResetPasswordRequest resetPasswordRequest) throws Exception {

        if(!jwtService.isTokenValid(token, TokenType.RESET_PASSWORD)) {
            throw new JwtException("Invalid token");
        }

        String password = resetPasswordRequest.password();

        if(password == null || password.isEmpty()) {
            throw new Exception("Password is null or empty");
        }

        String userEmail = jwtService.extractSubject(token);

        boolean isSuccess = userRepository.findByEmail(userEmail)
                .map(user -> {
                    user.setPassword(passwordEncoder.encode(password));
                    userRepository.save(user);
                    return true;
                })
                .orElse(false);

        if (isSuccess) {
            try {
                redisPIDService.addPasswordId(userEmail);
            } catch (Exception e) {
                // rollback DB change manually if Redis fails
                throw new RuntimeException("Redis operation failed, rolling back", e);
            }
        }

        return isSuccess;
    }

    @Transactional
    public long deleteAccount(String email) {
        long deletedCount = userRepository.deleteByEmail(email);

        if (deletedCount > 0) {
            try {
                redisPIDService.removePasswordId(email);
            } catch (Exception e) {
                // Redis failed - rollback DB transaction
                throw new RuntimeException("Failed to remove password ID from Redis, rolling back DB delete", e);
            }
        }

        return deletedCount;
    }
}
