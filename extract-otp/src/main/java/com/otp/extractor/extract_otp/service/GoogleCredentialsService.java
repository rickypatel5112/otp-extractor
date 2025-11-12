package com.otp.extractor.extract_otp.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.GmailScopes;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.otp.extractor.extract_otp.dto.GoogleUserInfo;
import com.otp.extractor.extract_otp.repository.GoogleUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static com.otp.extractor.extract_otp.service.GoogleAccessTokenCacheService.ACCESS_TOKEN_PREFIX;

@Service
@RequiredArgsConstructor
public class GoogleCredentialsService {

    private final GoogleUserRepository googleUserRepository;
    private final EncryptionService encryptionService;
    private final GoogleAccessTokenCacheService googleAccessTokenCacheService;
    @Value("${google.client.id}")
    private String clientId;

    @Value("${google.client.secret}")
    private String clientSecret;

    public GoogleCredentials getValidCredentials(String email) throws IOException {
        String accessToken = googleAccessTokenCacheService.getAccessToken(ACCESS_TOKEN_PREFIX + email);
        long expiresInSeconds = googleAccessTokenCacheService.getExpiresInSeconds(ACCESS_TOKEN_PREFIX + email);
        long expirationTimeMillis = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(expiresInSeconds);

        if (accessToken != null) {
            return buildCredentials(accessToken, new Date(expirationTimeMillis));
        }

        GoogleTokenResponse response = refreshAccessToken(email);

        //Cache access token
        googleAccessTokenCacheService.addAccessToken(
                email,
                response.getAccessToken(),
                response.getExpiresInSeconds(),
                TimeUnit.SECONDS);

        long expiresAt = System.currentTimeMillis() + (response.getExpiresInSeconds() * 1000L);
        return buildCredentials(response.getAccessToken(), new Date(expiresAt));
    }

    private GoogleTokenResponse refreshAccessToken(String email) throws IOException {
        GoogleUserInfo user = googleUserRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        return new GoogleRefreshTokenRequest(
                new NetHttpTransport(),
                new GsonFactory(),
                encryptionService.decrypt(user.getRefreshToken()),
                clientId,
                clientSecret
        ).execute();
    }

    private GoogleCredentials buildCredentials(String token, Date expiration) {
        return GoogleCredentials.create(new AccessToken(token, expiration))
                .createScoped(Collections.singleton(GmailScopes.GMAIL_READONLY));
    }
}
