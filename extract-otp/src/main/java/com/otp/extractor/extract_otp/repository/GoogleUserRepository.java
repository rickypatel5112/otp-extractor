package com.otp.extractor.extract_otp.repository;

import com.otp.extractor.extract_otp.dto.GoogleUserInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GoogleUserRepository extends JpaRepository<GoogleUserInfo, Long> {

    Optional<GoogleUserInfo> findByEmail(String email);
}
