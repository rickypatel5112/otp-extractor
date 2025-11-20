package com.otp.extractor.extract_otp.service;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GmailRequestProducer {

    private final RabbitTemplate rabbitTemplate;
    private final Queue gmailRequestQueue;

    public void requestWatchSetup(String email){
        try {
            rabbitTemplate.convertAndSend(gmailRequestQueue.getName(), email);
        } catch (AmqpException e) {
            throw new AmqpException("Failed to send password reset message", e);
        }
    }
}
