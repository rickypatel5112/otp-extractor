package com.project.otp_extractor.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.project.otp_extractor.dtos.ForgotPasswordResponse;
import com.project.otp_extractor.services.PasswordResetProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class PasswordResetProducerTest {

    @Mock private RabbitTemplate rabbitTemplate;

    @Mock private Queue passwordResetQueue;

    @InjectMocks private PasswordResetProducer producer;

    private ForgotPasswordResponse response;

    @BeforeEach
    void setUp() {
        response =
                ForgotPasswordResponse.builder()
                        .email("test@gmail.com")
                        .resetToken("reset-token")
                        .frontEndUrl("https://frontend.com")
                        .build();
    }

    @Test
    void shouldSendMessageToCorrectQueue() {
        String queueName = "password-reset-queue";

        when(passwordResetQueue.getName()).thenReturn(queueName);

        producer.sendMessage(response);

        verify(rabbitTemplate).convertAndSend(queueName, response);
    }

    @Test
    void shouldThrowAmqpExceptionWhenSendFails() {
        String queueName = "password-reset-queue";

        when(passwordResetQueue.getName()).thenReturn(queueName);

        doThrow(new AmqpException("RabbitMQ failure"))
                .when(rabbitTemplate)
                .convertAndSend(queueName, response);

        AmqpException exception =
                assertThrows(AmqpException.class, () -> producer.sendMessage(response));

        assertEquals("Failed to send password reset message", exception.getMessage());

        verify(rabbitTemplate).convertAndSend(queueName, response);
    }
}
