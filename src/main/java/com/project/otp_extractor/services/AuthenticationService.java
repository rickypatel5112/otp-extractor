package com.project.otp_extractor.services;

import com.project.otp_extractor.dtos.AuthenticationRequest;
import com.project.otp_extractor.dtos.RegisterRequest;
import com.project.otp_extractor.dtos.TokenType;
import com.project.otp_extractor.exceptions.UserAlreadyExistsException;
import com.project.otp_extractor.user.User;
import com.project.otp_extractor.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RedisTokenService redisTokenService;
//    private final UserDetailsService userDetailsService;

    public record TokenPair(String accessToken, String refreshToken) {
    }

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
         The user when they delete their account, we'll revoke all their access and refresh tokens. This way
         it won't pass the revocation check
         */
//        var user = userRepository.findByEmail(request.getEmail()).orElseThrow();

        String subject = request.getEmail();
        var accessToken = jwtService.generateToken(subject, TokenType.ACCESS);
        var refreshToken = jwtService.generateToken(subject, TokenType.REFRESH);

        redisTokenService.storeToken(refreshToken, TokenType.REFRESH);
        return new TokenPair(accessToken, refreshToken);
    }

    public TokenPair issueNewNonExpiredToken(String refreshToken) {

        // Check if refresh token is expired
        if (!jwtService.isTokenValid(refreshToken)) {
            throw new JwtException("Refresh token has expired");
        }

        // Extract username and JTI
        String jti = jwtService.extractJti(refreshToken);
        String subject =  jwtService.extractSubject(refreshToken);

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
        redisTokenService.storeToken(accessToken, TokenType.ACCESS);
        jwtService.revokeToken(accessToken);
        jwtService.revokeToken(refreshToken);
    }
}
