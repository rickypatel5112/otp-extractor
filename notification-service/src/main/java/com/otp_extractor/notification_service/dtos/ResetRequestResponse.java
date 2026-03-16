package com.otp_extractor.notification_service.dtos;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResetRequestResponse {
    private String email;
    private String resetToken;
    private String frontEndUrl;
}
