package com.otp.extractor.extract_otp.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.WatchRequest;
import com.google.api.services.gmail.model.WatchResponse;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class GmailWatchService {

    private static final String PUBSUB_TOPIC = "projects/otp-extractor-477323/topics/gmail-notify-topic";
    private final GoogleCredentialsService googleCredentialsService;
    private final GmailWatchCacheService gmailWatchCacheService;

    @Getter
    private final Set<String> userEmails = ConcurrentHashMap.newKeySet();

    public WatchResponse createWatchForUser(String email) throws GeneralSecurityException, IOException {
        Gmail gmailService = buildGmailService(email);

        WatchRequest watchRequest = new WatchRequest()
                .setLabelIds(Collections.singletonList("INBOX"))
                .setTopicName(PUBSUB_TOPIC);

        WatchResponse watchResponse = gmailService.users()
                .watch("me",  watchRequest)
                .execute();

        userEmails.add(email);

        gmailWatchCacheService.addWatchHistoryId(
                email,
                watchResponse.getHistoryId(),
                watchResponse.getExpiration()
        );

        System.out.println("Watch setup historyId [" + email + "]: " + watchResponse.getHistoryId());
        return watchResponse;
    }

    private Gmail buildGmailService(String email) throws GeneralSecurityException, IOException {
        GoogleCredentials credentials = googleCredentialsService.getValidCredentials(email);

        return new Gmail.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials)
        ).setApplicationName("otp-extractor").build();
    }
}
