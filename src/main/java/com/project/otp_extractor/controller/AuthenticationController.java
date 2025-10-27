package com.project.otp_extractor.controller;

import com.project.otp_extractor.dtos.AuthenticationRequest;
import com.project.otp_extractor.dtos.AuthenticationResponse;
import com.project.otp_extractor.dtos.RegisterRequest;
import com.project.otp_extractor.exceptions.UserAlreadyExistsException;
import com.project.otp_extractor.services.AuthenticationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private static final String cookiePath = "/api/v1/auth";
    private final AuthenticationService authenticationService;

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

}
