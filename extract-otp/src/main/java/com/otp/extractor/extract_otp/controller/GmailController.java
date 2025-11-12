package com.otp.extractor.extract_otp.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.services.gmail.GmailScopes;
import com.otp.extractor.extract_otp.service.GoogleOAuthService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/v1/auth")
public class GmailController {

    private final GoogleOAuthService googleOAuthService;
    @Value("${google.client.id}")
    private String GOOGLE_CLIENT_ID;

    @Value("${google.redirect-uri}")
    private String REDIRECT_URI;

    @GetMapping("/sign-in-google")
    public void generateAuthorizationUrl(HttpServletResponse response) throws IOException {
        String authorizationUrl = new GoogleAuthorizationCodeRequestUrl(
                GOOGLE_CLIENT_ID,
                REDIRECT_URI,
                List.of("openid", "email", "profile", GmailScopes.GMAIL_READONLY)
        )
                .setAccessType("offline")
                .setApprovalPrompt("force")
                .build();

        response.sendRedirect(authorizationUrl);
    }

    @GetMapping("/oauth/google/callback")
    public ResponseEntity<?> handleGoogleCallback(@RequestParam("code") String code) {
        try {
            return googleOAuthService.processGoogleOAuthCallback(code);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Failed to authenticate user with Google");
        }
    }

    @PostMapping("/gmail/notify")
    public ResponseEntity<String> handlePubSubMessage(@RequestBody Map<String, Object> body) {
        System.out.println("Received Pub/Sub message: " + body);
        return ResponseEntity.ok("ACK");
    }
}
