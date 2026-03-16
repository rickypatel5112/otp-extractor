package com.project.otp_extractor.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("front-end")
public class FrontendConfig {

    private List<String> urls;

    public List<String> getUrls() {
        return urls;
    }

    public void setUrls(List<String> urls) {
        this.urls = urls;
    }

    public boolean isUrlAllowed(String url) {
        return urls.contains(url);
    }
}
