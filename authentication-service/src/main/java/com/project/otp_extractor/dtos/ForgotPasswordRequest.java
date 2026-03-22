package com.project.otp_extractor.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ForgotPasswordRequest {

    private String email;
    private String frontEndUrl;
}
