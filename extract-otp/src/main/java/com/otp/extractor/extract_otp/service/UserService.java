package com.otp.extractor.extract_otp.service;

import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.otp.extractor.extract_otp.dto.GoogleUserInfo;
import com.otp.extractor.extract_otp.repository.GoogleUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final GoogleUserRepository googleUserRepository;
    private final EncryptionService encryptionService;

    public GoogleUserInfo saveOrUpdateUser(GoogleUserInfo user) {
        return googleUserRepository.findByEmail(user.getEmail())
                .map(existing -> {
                    existing.setRefreshToken(user.getRefreshToken());
                    return googleUserRepository.save(existing);
                })
                .orElseGet(() -> googleUserRepository.save(user));
    }

    public GoogleUserInfo buildGoogleUserInfo(GoogleIdToken idToken, TokenResponse tokenResponse) {
        GoogleIdToken.Payload payload = idToken.getPayload();
        return GoogleUserInfo.builder()
                .email(payload.getEmail())
                .verified(Boolean.TRUE.equals(payload.getEmailVerified()))
                .refreshToken(encryptionService.encrypt(tokenResponse.getRefreshToken()))
                .build();
    }
}
