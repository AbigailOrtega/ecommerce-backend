package com.ecommerce.service;

import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.entity.User;
import java.time.LocalDate;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Year;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final ResendClient resendClient;
    private final StoreInfoService storeInfoService;

    @Value("${app.mail.from:${spring.mail.username:noreply@ecommerce.com}}")
    private String fromEmail;

    @Value("${app.mail.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    private com.ecommerce.tenant.TenantRepository tenantRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setTenantRepository(com.ecommerce.tenant.TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    /**
     * The address of the store the e-mail is for (links, logo, unsubscribe). Stores without their
     * own base URL (Marea) use app.mail.frontend-url. Runs on the async mail thread, which keeps the
     * request's store (TenancyConfig's task decorator).
     */
    private String storeUrl() {
        if (tenantRepository == null) return frontendUrl;
        return tenantRepository.findById(com.ecommerce.tenant.TenantContext.current())
                .map(com.ecommerce.tenant.Tenant::getBaseUrl)
                .filter(url -> !url.isBlank())
                .map(url -> url.replaceAll("/+$", ""))
                .orElse(frontendUrl);
    }

    @Async
    public void sendWelcomeEmail(User user) {
        try {
            Context context = new Context();
            context.setVariable("firstName", user.getFirstName());
            context.setVariable("email", user.getEmail());
            context.setVariable("frontendUrl", storeUrl());

            String html = render("email/welcome", context);
            sendEmail(user.getEmail(), "¡Bienvenido a nuestra tienda!", html);
            log.info("Welcome email sent to: {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to send welcome email to: {}", user.getEmail(), e);
        }
    }

    @Async
    public void sendOrderConfirmationEmail(User user, OrderResponse order) {
        try {
            Context context = new Context();
            context.setVariable("firstName", user.getFirstName());
            context.setVariable("order", order);

            String html = render("email/order-confirmation", context);
            sendEmail(user.getEmail(), "Confirmación de tu pedido #" + order.getOrderNumber(), html);
            log.info("Order confirmation email sent to: {} for order: {}", user.getEmail(), order.getOrderNumber());
        } catch (Exception e) {
            log.error("Failed to send order confirmation email to: {}", user.getEmail(), e);
        }
    }

    @Async
    public void sendGuestOrderConfirmationEmail(String email, String firstName, OrderResponse order) {
        try {
            Context context = new Context();
            context.setVariable("firstName", firstName);
            context.setVariable("order", order);

            String html = render("email/order-confirmation", context);
            sendEmail(email, "Confirmación de tu pedido #" + order.getOrderNumber(), html);
            log.info("Guest order confirmation email sent to: {} for order: {}", email, order.getOrderNumber());
        } catch (Exception e) {
            log.error("Failed to send guest order confirmation email to: {}", email, e);
        }
    }

    @Async
    public void sendOrderStatusUpdateEmail(User user, OrderResponse order) {
        try {
            Context context = new Context();
            context.setVariable("firstName", user.getFirstName());
            context.setVariable("order", order);
            context.setVariable("statusLabel", getStatusLabel(order.getStatus()));

            String html = render("email/order-status", context);
            sendEmail(user.getEmail(), "Actualización de tu pedido #" + order.getOrderNumber(), html);
            log.info("Order status email sent to: {} for order: {} - status: {}",
                    user.getEmail(), order.getOrderNumber(), order.getStatus());
        } catch (Exception e) {
            log.error("Failed to send order status email to: {}", user.getEmail(), e);
        }
    }

    @Async
    public void sendGuestOrderStatusUpdateEmail(String email, String firstName, OrderResponse order) {
        try {
            Context context = new Context();
            context.setVariable("firstName", firstName);
            context.setVariable("order", order);
            context.setVariable("statusLabel", getStatusLabel(order.getStatus()));

            String html = render("email/order-status", context);
            sendEmail(email, "Actualización de tu pedido #" + order.getOrderNumber(), html);
            log.info("Guest order status email sent to: {} for order: {} - status: {}",
                    email, order.getOrderNumber(), order.getStatus());
        } catch (Exception e) {
            log.error("Failed to send guest order status email to: {}", email, e);
        }
    }

    /**
     * Tells someone they were made admin. With a token, the account is new and the email carries
     * the link to set its password; without one, it links to the admin panel.
     */
    @Async
    public void sendAdminInvitationEmail(String email, String firstName, String setPasswordToken) {
        try {
            Context context = new Context();
            context.setVariable("firstName", firstName);
            context.setVariable("setPasswordLink", setPasswordToken == null ? null
                    : storeUrl() + "/reset-password?token=" + setPasswordToken + "&invite=1");
            context.setVariable("adminLink", storeUrl() + "/admin");

            String html = render("email/admin-invitation", context);
            sendEmail(email, "Ahora tienes acceso de admin", html);
            log.info("Admin invitation email sent to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send admin invitation email to: {}", email, e);
        }
    }

    @Async
    public void sendPasswordResetEmail(String email, String firstName, String resetToken) {
        try {
            Context context = new Context();
            context.setVariable("firstName", firstName);
            context.setVariable("resetLink", storeUrl() + "/reset-password?token=" + resetToken);

            String html = render("email/password-reset", context);
            sendEmail(email, "Restablecer tu contraseña", html);
            log.info("Password reset email sent to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", email, e);
        }
    }

    @Async
    public void sendPickupRescheduleEmail(String toEmail, String firstName, String orderNumber,
            String locationName, LocalDate pickupDate, String timeSlotLabel, String reason, String manageToken) {
        try {
            Context context = new Context();
            context.setVariable("firstName", firstName);
            context.setVariable("orderNumber", orderNumber);
            context.setVariable("locationName", locationName);
            context.setVariable("pickupDate", pickupDate);
            context.setVariable("timeSlotLabel", timeSlotLabel);
            context.setVariable("reason", reason);
            context.setVariable("frontendUrl", storeUrl());
            context.setVariable("pickupManageUrl", pickupManageUrl(orderNumber, manageToken));

            String html = render("email/pickup-reschedule", context);
            sendEmail(toEmail, "Tu recolección ha sido cancelada — Pedido #" + orderNumber, html);
            log.info("Pickup reschedule email sent to: {} for order: {}", toEmail, orderNumber);
        } catch (Exception e) {
            log.error("Failed to send pickup reschedule email to: {}", toEmail, e);
        }
    }

    @Async
    public void sendPickupRescheduledConfirmationEmail(String toEmail, String firstName, String orderNumber,
            String locationName, LocalDate pickupDate, String timeSlotLabel, String manageToken) {
        try {
            Context context = new Context();
            context.setVariable("firstName", firstName);
            context.setVariable("orderNumber", orderNumber);
            context.setVariable("locationName", locationName);
            context.setVariable("pickupDate", pickupDate);
            context.setVariable("timeSlotLabel", timeSlotLabel);
            context.setVariable("frontendUrl", storeUrl());
            context.setVariable("pickupManageUrl", pickupManageUrl(orderNumber, manageToken));

            String html = render("email/pickup-rescheduled-confirmation", context);
            sendEmail(toEmail, "Tu recolección ha sido confirmada — Pedido #" + orderNumber, html);
            log.info("Pickup rescheduled confirmation email sent to: {} for order: {}", toEmail, orderNumber);
        } catch (Exception e) {
            log.error("Failed to send pickup rescheduled confirmation email to: {}", toEmail, e);
        }
    }

    @Async
    public void sendLabelPendingAlert(String toEmail, String firstName, String orderNumber, String shipmentId) {
        try {
            Context context = new Context();
            context.setVariable("firstName", firstName);
            context.setVariable("orderNumber", orderNumber);
            context.setVariable("shipmentId", shipmentId);
            context.setVariable("adminUrl", storeUrl());

            String html = render("email/label-pending", context);
            sendEmail(toEmail, "Acción requerida: guía pendiente — Pedido #" + orderNumber, html);
            log.info("Label pending alert sent to: {} for order: {}", toEmail, orderNumber);
        } catch (Exception e) {
            log.error("Failed to send label pending alert to: {}", toEmail, e);
        }
    }

    @Async
    public void sendShipmentTrackingEmail(User user, OrderResponse order) {
        try {
            Context context = new Context();
            context.setVariable("firstName", user.getFirstName());
            context.setVariable("order", order);
            context.setVariable("frontendUrl", storeUrl());

            String html = render("email/shipment-tracking", context);
            sendEmail(user.getEmail(), "Tu pedido #" + order.getOrderNumber() + " está en camino", html);
            log.info("Shipment tracking email sent to: {} for order: {}", user.getEmail(), order.getOrderNumber());
        } catch (Exception e) {
            log.error("Failed to send shipment tracking email to: {}", user.getEmail(), e);
        }
    }

    @Async
    public void sendGuestShipmentTrackingEmail(String email, String firstName, OrderResponse order) {
        try {
            Context context = new Context();
            context.setVariable("firstName", firstName);
            context.setVariable("order", order);
            context.setVariable("frontendUrl", storeUrl());

            String html = render("email/shipment-tracking", context);
            sendEmail(email, "Tu pedido #" + order.getOrderNumber() + " está en camino", html);
            log.info("Guest shipment tracking email sent to: {} for order: {}", email, order.getOrderNumber());
        } catch (Exception e) {
            log.error("Failed to send guest shipment tracking email to: {}", email, e);
        }
    }

    @Async
    public void sendMarketingEmailBulk(java.util.List<User> users, String subject, String bodyText,
                                       byte[] imageBytes, String imageContentType) {
        int sent = 0;
        for (User user : users) {
            try {
                Context context = new Context();
                context.setVariable("firstName", user.getFirstName());
                context.setVariable("bodyText", bodyText);
                context.setVariable("hasImage", imageBytes != null && imageBytes.length > 0);
                context.setVariable("unsubscribeUrl", storeUrl() + "/unsubscribe?token=" + user.getUnsubscribeToken());
                context.setVariable("frontendUrl", storeUrl());

                String html = render("email/marketing", context);

                ResendClient.InlineImage image = imageBytes != null && imageBytes.length > 0
                        ? new ResendClient.InlineImage("promoImage", imageBytes,
                                imageContentType != null ? imageContentType : "image/jpeg")
                        : null;
                sendEmail(user.getEmail(), subject, html, image);
                sent++;
            } catch (Exception e) {
                log.error("Failed to send marketing email to: {}", user.getEmail(), e);
            }
        }
        log.info("Marketing campaign sent: {}/{} emails delivered", sent, users.size());
    }

    /**
     * Synchronous (no @Async), no try/catch — lets the real SMTP/Resend error propagate
     * to the caller so the admin can see exactly what went wrong.
     */
    public void sendTestEmail(String toEmail) throws MessagingException {
        Context context = new Context();
        context.setVariable("firstName", "Test");
        context.setVariable("email", toEmail);
        context.setVariable("frontendUrl", storeUrl());

        String html = render("email/welcome", context);
        sendEmail(toEmail, "Correo de prueba — " + storeName(), html);
        log.info("Test email sent to: {}", toEmail);
    }

    /** The page where the buyer reschedules or cancels a pickup; null without a token. */
    String pickupManageUrl(String orderNumber, String manageToken) {
        if (orderNumber == null || manageToken == null) return null;
        return storeUrl() + "/recoleccion/" + orderNumber + "?t=" + manageToken;
    }

    /** Name shown when the store info has none (or cannot be read). */
    static final String DEFAULT_STORE_NAME = "Marea";

    /** Every email carries the store's name (from the admin's store info) and the current year. */
    private String render(String template, Context context) {
        context.setVariable("storeName", storeName());
        // order emails: a pickup order carries its reschedule/cancel link
        if (context.getVariable("order") instanceof OrderResponse order && "PICKUP".equals(order.getShippingType())
                && !context.containsVariable("pickupManageUrl")) {
            context.setVariable("pickupManageUrl", pickupManageUrl(order.getOrderNumber(), order.getManageToken()));
        }
        context.setVariable("year", Year.now().getValue());
        return templateEngine.process(template, context);
    }

    String storeName() {
        try {
            String name = storeInfoService.getOrCreate().getName();
            return name == null || name.isBlank() ? DEFAULT_STORE_NAME : name.trim();
        } catch (RuntimeException e) {
            log.warn("Store name unavailable for emails, using \"{}\": {}", DEFAULT_STORE_NAME, e.getMessage());
            return DEFAULT_STORE_NAME;
        }
    }

    private void sendEmail(String to, String subject, String htmlContent) throws MessagingException {
        sendEmail(to, subject, htmlContent, null);
    }

    // Resend (HTTPS) when RESEND_API_KEY is set, because Render's free plan blocks SMTP; SMTP otherwise.
    private void sendEmail(String to, String subject, String htmlContent,
                           ResendClient.InlineImage inlineImage) throws MessagingException {
        if (resendClient.isConfigured()) {
            resendClient.send(fromEmail, to, subject, htmlContent, inlineImage);
            return;
        }
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);
        if (inlineImage != null) {
            helper.addInline(inlineImage.contentId(),
                    new org.springframework.core.io.ByteArrayResource(inlineImage.bytes()),
                    inlineImage.contentType());
        }
        mailSender.send(message);
    }

    private String getStatusLabel(String status) {
        return switch (status) {
            case "PENDING"    -> "Pendiente";
            case "CONFIRMED"  -> "Confirmado";
            case "PROCESSING" -> "En Proceso";
            case "SHIPPED"    -> "Enviado";
            case "DELIVERED"  -> "Entregado";
            case "CANCELLED"  -> "Cancelado";
            case "REFUNDED"         -> "Reembolsado";
            case "SHIPMENT_PENDING" -> "Envío pendiente";
            default                 -> status;
        };
    }
}
