package com.ecommerce.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends email through Resend's HTTPS API. Render's free plan blocks outbound SMTP
 * (ports 25/465/587), so production mail goes over port 443 when RESEND_API_KEY is set.
 */
@Slf4j
@Component
public class ResendClient {

    static final String API_URL = "https://api.resend.com/emails";

    private final RestTemplate restTemplate;

    @Value("${app.mail.resend-api-key:}")
    private String apiKey;

    public ResendClient() {
        this(new RestTemplate());
    }

    ResendClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** An image referenced from the HTML as {@code cid:<contentId>}. */
    public record InlineImage(String contentId, byte[] bytes, String contentType) {}

    public void send(String from, String to, String subject, String html, InlineImage inlineImage) {
        Map<String, Object> body = new HashMap<>();
        body.put("from", from);
        body.put("to", List.of(to));
        body.put("subject", subject);
        body.put("html", html);
        if (inlineImage != null) {
            List<Map<String, String>> attachments = new ArrayList<>();
            attachments.add(Map.of(
                    "filename", inlineImage.contentId(),
                    "content", Base64.getEncoder().encodeToString(inlineImage.bytes()),
                    "content_type", inlineImage.contentType(),
                    "content_id", inlineImage.contentId()));
            body.put("attachments", attachments);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            restTemplate.postForEntity(API_URL, new HttpEntity<>(body, headers), Map.class);
        } catch (RestClientResponseException e) {
            // Resend explains the problem in the body (unverified domain, invalid from, bad key...)
            throw new MailSendException("Resend rejected the email (" + e.getStatusCode().value() + "): "
                    + e.getResponseBodyAsString(), e);
        }
    }
}
