package com.project.otp_extractor.dtos;

import lombok.Data;

@Data
public class ResetPasswordRequest {

    private String email;
    private String frontEndUrl;
}
