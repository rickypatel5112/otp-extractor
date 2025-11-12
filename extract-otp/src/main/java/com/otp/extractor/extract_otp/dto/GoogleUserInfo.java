package com.otp.extractor.extract_otp.dto;

import jakarta.persistence.*;
import lombok.*;

@Builder
@Data
@Entity
@Table(name = "google_users")
@NoArgsConstructor
@AllArgsConstructor
public class GoogleUserInfo {

    @Id
    @GeneratedValue
    private Long id;
    private String email;
    private boolean verified;
    @Column(length = 2000)
    private String refreshToken;
}
