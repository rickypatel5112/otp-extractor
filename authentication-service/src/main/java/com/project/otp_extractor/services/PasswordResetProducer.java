package com.project.otp_extractor.services;

import com.project.otp_extractor.dtos.ForgotPasswordResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PasswordResetProducer {

    private final RabbitTemplate rabbitTemplate;
    private final Queue passwordResetQueue;

    public void sendMessage(ForgotPasswordResponse forgotPasswordResponse){
        try {
            rabbitTemplate.convertAndSend(passwordResetQueue.getName(), forgotPasswordResponse);
        } catch (AmqpException e) {
            throw new AmqpException("Failed to send password reset message", e);
        }
    }
}
