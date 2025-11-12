package com.otp.extractor.extract_otp.service;

import org.jasypt.util.text.AES256TextEncryptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EncryptionService {

    private final AES256TextEncryptor encryptor;

    public EncryptionService(@Value("${jasypt.encryptor.password}") String jasyptSecret) {
        encryptor = new AES256TextEncryptor();
        encryptor.setPassword(jasyptSecret);
    }

    public String encrypt(String plainText) {
        return encryptor.encrypt(plainText);
    }

    public String decrypt(String cipherText) {
        return encryptor.decrypt(cipherText);
    }

}
