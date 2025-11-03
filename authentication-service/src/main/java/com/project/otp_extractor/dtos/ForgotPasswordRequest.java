package com.project.otp_extractor.dtos;

import lombok.Data;

@Data
public class ForgotPasswordRequest {

    private String email;
    private String frontEndUrl;
}
