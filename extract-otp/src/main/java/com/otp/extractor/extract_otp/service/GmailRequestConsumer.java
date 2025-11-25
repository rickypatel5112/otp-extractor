package com.otp.extractor.extract_otp.service;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class GmailRequestConsumer {

    private final GmailWatchService gmailWatchService;

    @RabbitListener(queues = "#{gmailRequestQueue.name}")
    public void handleWatchRequest(String email) throws GoogleJsonResponseException {
        try {
            gmailWatchService.createWatchForUser(email);

        } catch (GoogleJsonResponseException e) {
//            if (isRateLimitError(e)) {
//                long delayMs = calculateBackoff(email);
//                // requeue with delay
//                rabbitTemplate.convertAndSend(
//                        "gmail.watch.delayed.exchange",
//                        "gmail.watch.retry",
//                        email,
//                        m -> {
//                            m.getMessageProperties().setDelay(delayMs);
//                            return m;
//                        }
//                );
//                return;
//            }
            throw e; // go to DLQ if unrecoverable
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isRateLimitError(GoogleJsonResponseException e) {
        return e.getStatusCode() == 429 ||
                e.getDetails().getErrors().stream()
                        .anyMatch(err -> err.getReason().contains("rateLimitExceeded"));
    }

    private long calculateBackoff(int retry) {
        long base = (long) Math.pow(2, retry) * 1000; // ms
        long jitter = ThreadLocalRandom.current().nextLong(300, 900);
        return Math.min(base + jitter, 60000); // cap at 60s
    }
}