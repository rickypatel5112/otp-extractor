package com.project.otp_extractor.dtos;

import com.project.otp_extractor.annotation.ValidPassword;

public record ResetPasswordRequest(

        @ValidPassword
        String password) {
}
