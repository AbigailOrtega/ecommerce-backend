package com.ecommerce.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("ResendClient")
class ResendClientTest {

    private MockRestServiceServer server;
    private ResendClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new ResendClient(restTemplate);
        ReflectionTestUtils.setField(client, "apiKey", "re_test_123");
    }

    @Test
    @DisplayName("is configured only when an API key is present")
    void isConfigured() {
        assertThat(client.isConfigured()).isTrue();
        ReflectionTestUtils.setField(client, "apiKey", "");
        assertThat(client.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("posts from/to/subject/html with the bearer key")
    void postsEmail() {
        server.expect(requestTo(ResendClient.API_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer re_test_123"))
                .andExpect(jsonPath("$.from").value("Marea <pedidos@mareatextiles.com>"))
                .andExpect(jsonPath("$.to[0]").value("ana@example.com"))
                .andExpect(jsonPath("$.subject").value("Hola"))
                .andExpect(jsonPath("$.html").value("<p>hi</p>"))
                .andExpect(jsonPath("$.attachments").doesNotExist())
                .andRespond(withSuccess("{\"id\":\"e1\"}", MediaType.APPLICATION_JSON));

        client.send("Marea <pedidos@mareatextiles.com>", "ana@example.com", "Hola", "<p>hi</p>", null);
        server.verify();
    }

    @Test
    @DisplayName("sends an inline image as a base64 attachment with content_id")
    void inlineImage() {
        server.expect(requestTo(ResendClient.API_URL))
                .andExpect(jsonPath("$.attachments[0].content_id").value("promoImage"))
                .andExpect(jsonPath("$.attachments[0].content").value("AQID"))
                .andExpect(jsonPath("$.attachments[0].content_type").value("image/png"))
                .andRespond(withSuccess("{\"id\":\"e1\"}", MediaType.APPLICATION_JSON));

        client.send("a@b.com", "c@d.com", "s", "<img src=\"cid:promoImage\">",
                new ResendClient.InlineImage("promoImage", new byte[]{1, 2, 3}, "image/png"));
        server.verify();
    }

    @Test
    @DisplayName("includes Resend's explanation when it rejects the email")
    void rejected() {
        server.expect(requestTo(ResendClient.API_URL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"The mareatextiles.com domain is not verified\"}"));

        assertThatThrownBy(() -> client.send("a@mareatextiles.com", "c@d.com", "s", "h", null))
                .isInstanceOf(MailSendException.class)
                .hasMessageContaining("403")
                .hasMessageContaining("domain is not verified");
    }
}
