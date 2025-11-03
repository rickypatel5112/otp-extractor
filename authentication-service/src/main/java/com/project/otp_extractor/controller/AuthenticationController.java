package com.project.otp_extractor.controller;

import com.project.otp_extractor.dtos.*;
import com.project.otp_extractor.exceptions.UserAlreadyExistsException;
import com.project.otp_extractor.services.AuthenticationService;
import com.project.otp_extractor.services.JwtService;
import com.project.otp_extractor.user.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private static final String cookiePath = "/api/v1/auth";
    private final AuthenticationService authenticationService;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest request) throws UserAlreadyExistsException {
        authenticationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body("User registered successfully!");
    }

    @PostMapping("/authenticate")
    public ResponseEntity<AuthenticationResponse> authenticate(@Valid @RequestBody AuthenticationRequest request) throws MethodArgumentNotValidException {
        var tokenPair = authenticationService.authenticate(request);

        ResponseCookie cookie = ResponseCookie.from("refreshToken", tokenPair.refreshToken())
                .httpOnly(true)
                .secure(true)
                .path(cookiePath)
                .build();

        return ResponseEntity.ok()
                .header("Set-Cookie", cookie.toString())
                .body(new AuthenticationResponse(tokenPair.accessToken()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthenticationResponse> refresh(@CookieValue(name = "refreshToken") String refreshToken) {

        if(refreshToken == null ||  refreshToken.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

       var tokenPair = authenticationService.issueNewNonExpiredToken(refreshToken);

        ResponseCookie cookie = ResponseCookie.from("refreshToken", tokenPair.refreshToken())
                .httpOnly(true)
                .secure(true)
                .path(cookiePath)
                .build();

        return ResponseEntity.ok()
                .header("Set-Cookie", cookie.toString())
                .body(new AuthenticationResponse(tokenPair.accessToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@CookieValue(value = "refreshToken") String refreshToken
    , @RequestHeader(value = "Authorization") String authorizationHeader) {

        authenticationService.logout(authorizationHeader, refreshToken);

        ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .path(cookiePath)
                .maxAge(0)
                .build();

        return ResponseEntity
                .ok()
                .header("Set-Cookie", cookie.toString())
                .body("Logged out successfully!");
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        try {
            authenticationService.forgotPassword(request);
            // Always return 200 OK, even if email not found (to prevent user enumeration)
            return ResponseEntity.ok().body("If a user with that email exists, you will receive an email with a password reset link");
        } catch (AmqpException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Internal messaging error. Please try again later.",
                    e
            );
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestParam(name = "token") String token, @Valid @RequestBody ResetPasswordRequest resetPasswordRequest) throws Exception {
        try {
//            String token = jwtService.generateToken("ricky.patel.sde@gmail.com", TokenType.RESET_PASSWORD);
            boolean isSuccess = authenticationService.resetPassword(token, resetPasswordRequest);

            if (isSuccess) {
                return ResponseEntity.ok("Password reset successful");
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Password reset failed. Please try again.");
            }
        } catch (JwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid or expired token");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred: " + e.getMessage());
        }

    }

    @PostMapping("/delete-account")
    public ResponseEntity<String> deleteAccount(@RequestBody String email){

        long deletedCount = authenticationService.deleteAccount(email);

       if(deletedCount == 0){
           return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
       }
       return ResponseEntity.ok().body("Account deleted successfully!");
    }

}
