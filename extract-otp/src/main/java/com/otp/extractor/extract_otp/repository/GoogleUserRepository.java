package com.otp.extractor.extract_otp.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.otp.extractor.extract_otp.dto.GoogleUserInfo;

public interface GoogleUserRepository extends JpaRepository<GoogleUserInfo, Long> {

    Optional<GoogleUserInfo> findByEmail(String email);
}
