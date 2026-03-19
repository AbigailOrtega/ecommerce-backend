package com.ecommerce.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("UploadController — Integration")
class UploadControllerIT {

    @Autowired TestRestTemplate restTemplate;
    @LocalServerPort int port;

    private String adminToken;
    private String customerToken;

    private String baseUrl(String path) {
        return "http://localhost:" + port + path;
    }

    // ── Auth helpers ──────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        adminToken    = obtainToken("admin@test.com", "Admin123!");
        customerToken = obtainToken("test@test.com",  "Test123!");
    }

    private String obtainToken(String email, String password) {
        Map<String, String> credentials = Map.of("email", email, "password", password);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl("/api/auth/login"),
                new HttpEntity<>(credentials, headers),
                JsonNode.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().path("data").path("accessToken").asText();
    }

    /**
     * Builds a multipart request entity containing a small dummy image file.
     * The "file" part name must match the @RequestParam("file") in UploadController.
     */
    private HttpEntity<MultiValueMap<String, Object>> multipartEntity(String bearerToken) {
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.IMAGE_JPEG);

        // Minimal JPEG magic bytes — just enough to form a non-empty file part.
        ByteArrayResource fileResource = new ByteArrayResource("fake-image-bytes".getBytes()) {
            @Override
            public String getFilename() {
                return "test-upload.jpg";
            }
        };

        HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(fileResource, fileHeaders);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", filePart);

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (bearerToken != null) {
            requestHeaders.setBearerAuth(bearerToken);
        }

        return new HttpEntity<>(body, requestHeaders);
    }

    // ── POST /api/upload/image ─────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("POST /api/upload/image — returns 401 without authentication token")
    void uploadImage_401_noAuth() {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl("/api/upload/image"),
                multipartEntity(null),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/upload/image — returns 403 for authenticated customer (admin only endpoint)")
    void uploadImage_403_customerToken() {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl("/api/upload/image"),
                multipartEntity(customerToken),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(3)
    @DisplayName("POST /api/upload/image — auth layer passed with admin token (may be 400 if Cloudinary not configured)")
    void uploadImage_authPassed_withAdminToken() {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl("/api/upload/image"),
                multipartEntity(adminToken),
                JsonNode.class
        );

        // Must NOT be 401 or 403 — the admin passed the security layer.
        // The actual upload will return 400 when Cloudinary is not set up in e2e.
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }
}
