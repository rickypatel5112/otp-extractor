package com.project.otp_extractor.controller;

import com.project.otp_extractor.dtos.*;
import com.project.otp_extractor.services.AuthenticationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private static final String cookiePath = "/api/v1/auth";
    private final AuthenticationService authenticationService;

    private ResponseCookie buildCookie(String value, long maxAge) {
        return ResponseCookie.from("refreshToken", value)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path(cookiePath)
                .maxAge(maxAge)
                .build();
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {
        authenticationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "User registered successfully", null));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthenticationResponse>> authenticate(
            @Valid @RequestBody AuthenticationRequest request) {
        var tokenPair = authenticationService.authenticate(request);

        return ResponseEntity.ok()
                .header(
                        "Set-Cookie",
                        buildCookie(tokenPair.refreshToken(), 7 * 24 * 60 * 60).toString())
                .body(
                        ApiResponse.success(
                                "Login successful",
                                new AuthenticationResponse(tokenPair.accessToken())));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthenticationResponse>> refresh(
            @CookieValue(name = "refreshToken") String refreshToken) {
        var tokenPair = authenticationService.issueNewNonExpiredToken(refreshToken);

        return ResponseEntity.ok()
                .header(
                        "Set-Cookie",
                        buildCookie(tokenPair.refreshToken(), 7 * 24 * 60 * 60).toString())
                .body(
                        ApiResponse.success(
                                "Token refreshed",
                                new AuthenticationResponse(tokenPair.accessToken())));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue("refreshToken") String refreshToken,
            @RequestHeader("Authorization") String accessToken) {
        authenticationService.logout(accessToken, refreshToken);

        return ResponseEntity.ok()
                .header("Set-Cookie", buildCookie("", 0).toString())
                .body(ApiResponse.success("Logged out successfully"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @RequestBody ForgotPasswordRequest request) {
        authenticationService.forgotPassword(request);
        return ResponseEntity.ok()
                .body(
                        ApiResponse.success(
                                "If a user with that email exists, you will receive a password reset link"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @RequestParam(name = "token") String token,
            @Valid @RequestBody ResetPasswordRequest resetPasswordRequest) {
        boolean isSuccess = authenticationService.resetPassword(token, resetPasswordRequest);

        if (isSuccess) {
            return ResponseEntity.ok().body(ApiResponse.success("Password reset successful"));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, "Password reset failed. Please try again.", null));
    }

    @DeleteMapping("/delete-account/me")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            @CookieValue(value = "refreshToken") String refreshToken,
            @RequestHeader(value = "Authorization") String authorizationHeader) {
        authenticationService.deleteAccount(authorizationHeader, refreshToken);

        return ResponseEntity.ok()
                .header("Set-Cookie", buildCookie("", 0).toString())
                .body(ApiResponse.success("Account deleted successfully"));
    }
}
