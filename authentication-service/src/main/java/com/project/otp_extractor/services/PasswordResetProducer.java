package com.project.otp_extractor.services;

import com.project.otp_extractor.dtos.ResetRequestResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PasswordResetProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendMessage(ResetRequestResponse resetRequestResponse){
        rabbitTemplate.convertAndSend("password-reset-request", resetRequestResponse);
    }
}
