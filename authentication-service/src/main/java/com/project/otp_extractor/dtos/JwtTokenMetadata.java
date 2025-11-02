package com.project.otp_extractor.dtos;

import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JwtTokenMetadata {

    @Id
    private String jti;
    private String userEmail;
    private Instant issuedAt;
    private Instant expiresAt;
    private TokenType type;
    private boolean revoked = false;
}
