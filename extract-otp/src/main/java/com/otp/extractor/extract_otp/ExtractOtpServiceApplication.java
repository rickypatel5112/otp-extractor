package com.otp.extractor.extract_otp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ExtractOtpServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ExtractOtpServiceApplication.class, args);
	}

}
