package com.otp.extractor.extract_otp.scheduler;

import com.otp.extractor.extract_otp.service.GmailWatchCacheService;
import com.otp.extractor.extract_otp.service.GmailWatchService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.Set;

@Component
public class GmailWatchRenewalScheduler {

    private final GmailWatchCacheService gmailWatchCacheService;
    private final GmailWatchService gmailWatchService;

    public GmailWatchRenewalScheduler(GmailWatchCacheService gmailWatchCacheService, GmailWatchService gmailWatchService) {
        this.gmailWatchCacheService = gmailWatchCacheService;
        this.gmailWatchService = gmailWatchService;
    }

    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void recreateGmailWatcher() throws GeneralSecurityException, IOException {
        System.out.println("Scheduler triggered at: " + LocalDateTime.now());
        Set<String> userEmails = gmailWatchService.getUserEmails();
        if (userEmails == null || userEmails.isEmpty()) return;

        for (String email : userEmails) {
            Long ttlSeconds = gmailWatchCacheService.getWatchExpirationInSeconds(email);

            if (ttlSeconds != null && ttlSeconds < 3600) { // less than 1 hour left
                gmailWatchService.createWatchForUser(email);
            }
        }
    }
}
