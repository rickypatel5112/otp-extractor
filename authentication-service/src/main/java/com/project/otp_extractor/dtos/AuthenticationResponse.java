package com.project.otp_extractor.dtos;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
public class AuthenticationResponse {
    private final String accessToken;
}
