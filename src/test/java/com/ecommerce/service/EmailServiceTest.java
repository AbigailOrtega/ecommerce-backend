package com.ecommerce.service;

import com.ecommerce.dto.response.OrderResponse;

import com.ecommerce.entity.Role;
import com.ecommerce.entity.User;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Properties;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailService")
class EmailServiceTest {

    @Mock JavaMailSender mailSender;
    @Mock TemplateEngine templateEngine;
    @Mock ResendClient resendClient;
    @Mock StoreInfoService storeInfoService;
    @InjectMocks EmailService emailService;

    private User testUser;
    private MimeMessage realMessage;

    // NOTE: @Async is inactive in unit tests (no Spring context), so methods run
    // synchronously here — no need for CountDownLatch or thread coordination.

    @BeforeEach
    void setUp() {
        // Inject @Value fields that Spring would normally resolve
        ReflectionTestUtils.setField(emailService, "fromEmail",    "tienda@gmail.com");
        ReflectionTestUtils.setField(emailService, "frontendUrl",  "http://localhost:4200");

        // Use a real MimeMessage backed by an empty Session so MimeMessageHelper can
        // set headers without throwing — much simpler than deep-stubbing.
        realMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        lenient().when(mailSender.createMimeMessage()).thenReturn(realMessage);

        // Template engine returns minimal HTML for all calls
        lenient().when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html>ok</html>");

        testUser = User.builder()
                .id(1L).firstName("Ana").lastName("López")
                .email("ana@example.com").password("encoded")
                .role(Role.CUSTOMER).enabled(true).build();
    }

    // ─── sendWelcomeEmail ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("pickup link (reschedule / cancel) in order emails")
    class PickupLink {

        private Context contextOf(OrderResponse order) {
            ArgumentCaptor<Context> ctx = ArgumentCaptor.forClass(Context.class);
            emailService.sendGuestOrderConfirmationEmail("lucia@example.com", "Lucía", order);
            verify(templateEngine).process(eq("email/order-confirmation"), ctx.capture());
            return ctx.getValue();
        }

        @Test
        @DisplayName("a pickup order's email links to its pickup page, with the order's token")
        void pickupOrder() {
            OrderResponse order = OrderResponse.builder().orderNumber("ORD-PK1").shippingType("PICKUP")
                    .manageToken("11111111-2222-3333-4444-555555555555").build();
            assertThat(contextOf(order).getVariable("pickupManageUrl"))
                    .isEqualTo(ReflectionTestUtils.getField(emailService, "frontendUrl")
                            + "/recoleccion/ORD-PK1?t=11111111-2222-3333-4444-555555555555");
        }

        @Test
        @DisplayName("a home-delivery order has no pickup link")
        void nationalOrder() {
            OrderResponse order = OrderResponse.builder().orderNumber("ORD-NA1").shippingType("NATIONAL")
                    .manageToken("11111111-2222-3333-4444-555555555555").build();
            assertThat(contextOf(order).getVariable("pickupManageUrl")).isNull();
        }
    }

    @Nested
    @DisplayName("store name and year in every email")
    class Branding {

        private Context renderedContext() {
            ArgumentCaptor<Context> ctx = ArgumentCaptor.forClass(Context.class);
            emailService.sendWelcomeEmail(testUser);
            verify(templateEngine).process(eq("email/welcome"), ctx.capture());
            return ctx.getValue();
        }

        @Test
        @DisplayName("uses the name from the admin's store info, and the current year")
        void fromStoreInfo() {
            when(storeInfoService.getOrCreate()).thenReturn(com.ecommerce.entity.StoreInfo.builder().name("Marea Textiles").build());
            Context ctx = renderedContext();
            assertThat(ctx.getVariable("storeName")).isEqualTo("Marea Textiles");
            assertThat(ctx.getVariable("year")).isEqualTo(java.time.Year.now().getValue());
        }

        @Test
        @DisplayName("falls back to \"Marea\" when the store info has no name")
        void blankName() {
            when(storeInfoService.getOrCreate()).thenReturn(com.ecommerce.entity.StoreInfo.builder().name("  ").build());
            assertThat(renderedContext().getVariable("storeName")).isEqualTo("Marea");
        }

        @Test
        @DisplayName("falls back to \"Marea\" when the store info cannot be read (the email still goes out)")
        void storeInfoFails() {
            when(storeInfoService.getOrCreate()).thenThrow(new IllegalStateException("db down"));
            assertThat(renderedContext().getVariable("storeName")).isEqualTo("Marea");
        }
    }

    @Nested
    @DisplayName("sendWelcomeEmail")
    class SendWelcomeEmail {

        // ── Bug regression: frontendUrl was missing from the context ─────────

        @Test
        @DisplayName("passes frontendUrl to the template context [regression]")
        void sendsWelcomeEmail_includesFrontendUrl() {
            ArgumentCaptor<Context> ctxCaptor = ArgumentCaptor.forClass(Context.class);

            emailService.sendWelcomeEmail(testUser);

            verify(templateEngine).process(eq("email/welcome"), ctxCaptor.capture());
            assertThat(ctxCaptor.getValue().getVariable("frontendUrl"))
                    .isEqualTo("http://localhost:4200");
        }

        // ── Bug regression: from address must match the SMTP account ─────────

        @Test
        @DisplayName("sends from the configured fromEmail address [regression]")
        void sendWelcomeEmail_sendsFromConfiguredAddress() throws Exception {
            emailService.sendWelcomeEmail(testUser);

            verify(mailSender).send(realMessage);
            // The from header must match the SMTP-authenticated account;
            // a mismatch causes Gmail to reject the message silently.
            // (the display name is the store's: see EmailSenderNameTest)
            assertThat(((jakarta.mail.internet.InternetAddress) realMessage.getFrom()[0]).getAddress())
                    .isEqualTo("tienda@gmail.com");
        }

        // ── Happy-path ────────────────────────────────────────────────────────

        @Test
        @DisplayName("passes firstName and email variables to the template context")
        void sendWelcomeEmail_includesUserData() {
            ArgumentCaptor<Context> ctxCaptor = ArgumentCaptor.forClass(Context.class);

            emailService.sendWelcomeEmail(testUser);

            verify(templateEngine).process(eq("email/welcome"), ctxCaptor.capture());
            Context ctx = ctxCaptor.getValue();
            assertThat(ctx.getVariable("firstName")).isEqualTo("Ana");
            assertThat(ctx.getVariable("email")).isEqualTo("ana@example.com");
        }

        @Test
        @DisplayName("uses the email/welcome Thymeleaf template")
        void sendWelcomeEmail_usesWelcomeTemplate() {
            emailService.sendWelcomeEmail(testUser);

            verify(templateEngine).process(eq("email/welcome"), any(Context.class));
        }

        @Test
        @DisplayName("sends email to the user's email address")
        void sendWelcomeEmail_sendsToUserEmail() throws Exception {
            emailService.sendWelcomeEmail(testUser);

            verify(mailSender).send(realMessage);
            assertThat(realMessage.getAllRecipients()[0].toString())
                    .isEqualTo("ana@example.com");
        }

        @Test
        @DisplayName("calls mailSender.send exactly once")
        void sendWelcomeEmail_sendsExactlyOnce() {
            emailService.sendWelcomeEmail(testUser);

            verify(mailSender, times(1)).send(any(MimeMessage.class));
        }

        // ── Resilience ────────────────────────────────────────────────────────

        @Test
        @DisplayName("does not propagate exception when SMTP fails")
        void sendWelcomeEmail_silentWhenSmtpFails() {
            when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("SMTP unreachable"));

            assertThatNoException().isThrownBy(() -> emailService.sendWelcomeEmail(testUser));
        }

        @Test
        @DisplayName("does not propagate exception when template rendering fails")
        void sendWelcomeEmail_silentWhenTemplateErrors() {
            when(templateEngine.process(anyString(), any())).thenThrow(new RuntimeException("Template error"));

            assertThatNoException().isThrownBy(() -> emailService.sendWelcomeEmail(testUser));
        }
    }

    // ─── sendPasswordResetEmail ───────────────────────────────────────────────

    @Nested
    @DisplayName("sendPasswordResetEmail")
    class SendPasswordResetEmail {

        @Test
        @DisplayName("builds resetLink using frontendUrl + token")
        void sendPasswordResetEmail_buildsCorrectResetLink() {
            ArgumentCaptor<Context> ctxCaptor = ArgumentCaptor.forClass(Context.class);

            emailService.sendPasswordResetEmail("ana@example.com", "Ana", "abc-token-123");

            verify(templateEngine).process(eq("email/password-reset"), ctxCaptor.capture());
            String resetLink = (String) ctxCaptor.getValue().getVariable("resetLink");
            assertThat(resetLink)
                    .startsWith("http://localhost:4200")
                    .contains("abc-token-123");
        }

        @Test
        @DisplayName("passes firstName variable to the template context")
        void sendPasswordResetEmail_includesFirstName() {
            ArgumentCaptor<Context> ctxCaptor = ArgumentCaptor.forClass(Context.class);

            emailService.sendPasswordResetEmail("ana@example.com", "Ana", "tok");

            verify(templateEngine).process(eq("email/password-reset"), ctxCaptor.capture());
            assertThat(ctxCaptor.getValue().getVariable("firstName")).isEqualTo("Ana");
        }

        @Test
        @DisplayName("sends to the given email address")
        void sendPasswordResetEmail_sendsToCorrectAddress() throws Exception {
            emailService.sendPasswordResetEmail("ana@example.com", "Ana", "tok");

            verify(mailSender).send(realMessage);
            assertThat(realMessage.getAllRecipients()[0].toString())
                    .isEqualTo("ana@example.com");
        }

        @Test
        @DisplayName("does not propagate exception when sending fails")
        void sendPasswordResetEmail_silentOnError() {
            when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("SMTP error"));

            assertThatNoException().isThrownBy(() ->
                    emailService.sendPasswordResetEmail("ana@example.com", "Ana", "tok"));
        }
    }

    // ─── sendGuestOrderStatusUpdateEmail ─────────────────────────────────────

    @Nested
    @DisplayName("sendGuestOrderStatusUpdateEmail")
    class SendGuestOrderStatusUpdateEmail {

        @Test
        @DisplayName("uses email/order-status template")
        void usesOrderStatusTemplate() {
            var order = new com.ecommerce.dto.response.OrderResponse();
            order.setOrderNumber("ORD-G01");
            order.setStatus("CONFIRMED");

            emailService.sendGuestOrderStatusUpdateEmail("guest@example.com", "Invitado", order);

            verify(templateEngine).process(eq("email/order-status"), any());
        }

        @Test
        @DisplayName("passes firstName, order and statusLabel to template context")
        void includesContextVariables() {
            org.mockito.ArgumentCaptor<org.thymeleaf.context.Context> ctxCaptor =
                    org.mockito.ArgumentCaptor.forClass(org.thymeleaf.context.Context.class);
            var order = new com.ecommerce.dto.response.OrderResponse();
            order.setOrderNumber("ORD-G01");
            order.setStatus("CONFIRMED");

            emailService.sendGuestOrderStatusUpdateEmail("guest@example.com", "Invitado", order);

            verify(templateEngine).process(eq("email/order-status"), ctxCaptor.capture());
            org.thymeleaf.context.Context ctx = ctxCaptor.getValue();
            assertThat(ctx.getVariable("firstName")).isEqualTo("Invitado");
            assertThat(ctx.getVariable("order")).isSameAs(order);
            assertThat(ctx.getVariable("statusLabel")).isNotNull();
        }

        @Test
        @DisplayName("sends email to the guest email address")
        void sendsToGuestEmail() throws Exception {
            var order = new com.ecommerce.dto.response.OrderResponse();
            order.setOrderNumber("ORD-G01");
            order.setStatus("SHIPPED");

            emailService.sendGuestOrderStatusUpdateEmail("guest@example.com", "Invitado", order);

            verify(mailSender).send(realMessage);
            assertThat(realMessage.getAllRecipients()[0].toString())
                    .isEqualTo("guest@example.com");
        }

        @Test
        @DisplayName("does not propagate exception when SMTP fails")
        void silentOnSmtpError() {
            when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("SMTP down"));
            var order = new com.ecommerce.dto.response.OrderResponse();
            order.setOrderNumber("ORD-G01");
            order.setStatus("SHIPPED");

            assertThatNoException().isThrownBy(() ->
                    emailService.sendGuestOrderStatusUpdateEmail("guest@example.com", "Invitado", order));
        }
    }

    // ─── sendOrderConfirmationEmail ───────────────────────────────────────────

    @Nested
    @DisplayName("sendOrderConfirmationEmail")
    class SendOrderConfirmationEmail {

        @Test
        @DisplayName("uses email/order-confirmation template")
        void sendOrderConfirmationEmail_usesCorrectTemplate() {
            var order = new com.ecommerce.dto.response.OrderResponse();
            order.setOrderNumber("ORD-001");

            emailService.sendOrderConfirmationEmail(testUser, order);

            verify(templateEngine).process(eq("email/order-confirmation"), any(Context.class));
        }

        @Test
        @DisplayName("includes firstName and order in template context")
        void sendOrderConfirmationEmail_includesContextVars() {
            ArgumentCaptor<Context> ctxCaptor = ArgumentCaptor.forClass(Context.class);
            var order = new com.ecommerce.dto.response.OrderResponse();
            order.setOrderNumber("ORD-001");

            emailService.sendOrderConfirmationEmail(testUser, order);

            verify(templateEngine).process(eq("email/order-confirmation"), ctxCaptor.capture());
            Context ctx = ctxCaptor.getValue();
            assertThat(ctx.getVariable("firstName")).isEqualTo("Ana");
            assertThat(ctx.getVariable("order")).isSameAs(order);
        }

        @Test
        @DisplayName("does not propagate exception when sending fails")
        void sendOrderConfirmationEmail_silentOnError() {
            when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("SMTP error"));
            var order = new com.ecommerce.dto.response.OrderResponse();
            order.setOrderNumber("ORD-001");

            assertThatNoException().isThrownBy(() ->
                    emailService.sendOrderConfirmationEmail(testUser, order));
        }
    }

    // ─── Resend transport ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("when RESEND_API_KEY is configured")
    class WithResend {

        @BeforeEach
        void enableResend() {
            when(resendClient.isConfigured()).thenReturn(true);
        }

        @Test
        @DisplayName("sends through Resend and never opens an SMTP connection")
        void sendsThroughResend() {
            emailService.sendPasswordResetEmail("ana@example.com", "Ana", "tok");

            verify(resendClient).send(eq("\"Marea\" <tienda@gmail.com>"), eq("ana@example.com"),
                    anyString(), eq("<html>ok</html>"), isNull());
            verifyNoInteractions(mailSender);
        }

        @Test
        @DisplayName("passes the marketing image as an inline cid:promoImage attachment")
        void marketingImageIsInline() {
            byte[] img = {1, 2, 3};
            emailService.sendMarketingEmailBulk(java.util.List.of(testUser), "Promo", "Hola", img, "image/png");

            ArgumentCaptor<ResendClient.InlineImage> captor = ArgumentCaptor.forClass(ResendClient.InlineImage.class);
            verify(resendClient).send(eq("\"Marea\" <tienda@gmail.com>"), eq("ana@example.com"), eq("Promo"),
                    anyString(), captor.capture());
            assertThat(captor.getValue().contentId()).isEqualTo("promoImage");
            assertThat(captor.getValue().bytes()).isEqualTo(img);
            assertThat(captor.getValue().contentType()).isEqualTo("image/png");
        }

        @Test
        @DisplayName("sendTestEmail surfaces the Resend error to the admin")
        void testEmailPropagatesResendError() {
            doThrow(new org.springframework.mail.MailSendException("Resend rejected the email (403): domain not verified"))
                    .when(resendClient).send(anyString(), anyString(), anyString(), anyString(), any());

            assertThatThrownBy(() -> emailService.sendTestEmail("ana@example.com"))
                    .hasMessageContaining("domain not verified");
        }
    }
}
