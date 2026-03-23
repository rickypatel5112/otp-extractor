package com.project.otp_extractor.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
public class ForgotPasswordResponse {
    private String email;
    private String resetToken;
    private String frontEndUrl;
}
