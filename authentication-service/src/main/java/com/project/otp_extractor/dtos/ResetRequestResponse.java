package com.project.otp_extractor.dtos;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResetRequestResponse {
    private String email;
    private String resetToken;
    private String frontEndUrl;
}
