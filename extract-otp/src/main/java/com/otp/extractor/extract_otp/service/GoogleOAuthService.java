package com.otp.extractor.extract_otp.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.otp.extractor.extract_otp.dto.GoogleUserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class GoogleOAuthService {

    private final UserService userService;
    private final GoogleAccessTokenCacheService googleAccessTokenCacheService;
    private final GmailRequestProducer gmailRequestProducer;

    @Value("${google.client.id}")
    private String clientId;

    @Value("${google.client.secret}")
    private String clientSecret;

    @Value("${google.redirect-uri}")
    private String redirectUri;

    private final NetHttpTransport httpTransport = new NetHttpTransport();
    private final GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

    public ResponseEntity<?> processGoogleOAuthCallback(String code) throws Exception {
        GoogleTokenResponse tokenResponse = exchangeAuthCodeForTokens(code);
        GoogleIdToken idToken = verifyIdToken(tokenResponse.getIdToken());

        GoogleUserInfo userInfo = userService.buildGoogleUserInfo(idToken, tokenResponse);
        userInfo = userService.saveOrUpdateUser(userInfo);

        // Cache the token
        googleAccessTokenCacheService.addAccessToken(
                userInfo.getEmail(),
                tokenResponse.getAccessToken(),
                tokenResponse.getExpiresInSeconds(),
                TimeUnit.SECONDS
        );

        //Publish the message to rabbitmq
        gmailRequestProducer.requestWatchSetup(userInfo.getEmail());

//        var watchResponse = gmailWatchService.createWatchForUser(userInfo.getEmail());
//        return ResponseEntity.ok(watchResponse);

        return ResponseEntity.ok("Watch.....");
    }

    private GoogleTokenResponse exchangeAuthCodeForTokens(String code) throws IOException {
        return new GoogleAuthorizationCodeTokenRequest(
                httpTransport,
                jsonFactory,
                "https://oauth2.googleapis.com/token",
                clientId,
                clientSecret,
                code,
                redirectUri
        ).execute();
    }

    private GoogleIdToken verifyIdToken(String idTokenString) throws GeneralSecurityException, IOException {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(httpTransport, jsonFactory)
                .setAudience(Collections.singletonList(clientId))
                .build();

        GoogleIdToken idToken = verifier.verify(idTokenString);
        if (idToken == null) {
            throw new GeneralSecurityException("Invalid ID token");
        }

        return idToken;
    }
}

//Watch idea
//lets say we keep track of global count and per user count per minute in redis or
// something that has very fast read and writes and is time sensitive upto milliseconds.
// and in our message broker we get a message our consumer consumes it and
// checks if global count has exceeded or per user exceeded if any then should
// we retry the request with exponential backoff? or put it in a retry queue of
// lets say 15 seconds and if that fails put it in 30 secs retry queue if not then dead queue.
// hows that sound so far?