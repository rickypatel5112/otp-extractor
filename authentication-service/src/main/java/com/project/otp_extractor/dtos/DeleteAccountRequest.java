package com.project.otp_extractor.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(
        @NotBlank(message = "Email cannot be blank") @Email(message = "Invalid email format")
                String email) {}
