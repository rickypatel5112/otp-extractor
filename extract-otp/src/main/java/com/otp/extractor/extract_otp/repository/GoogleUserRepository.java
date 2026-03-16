package com.otp.extractor.extract_otp.repository;

import com.otp.extractor.extract_otp.dto.GoogleUserInfo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoogleUserRepository extends JpaRepository<GoogleUserInfo, Long> {

    Optional<GoogleUserInfo> findByEmail(String email);
}
