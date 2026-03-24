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

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.mail.from:${spring.mail.username:noreply@ecommerce.com}}")
    private String fromEmail;

    @Value("${app.mail.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    @Async
    public void sendWelcomeEmail(User user) {
        try {
            Context context = new Context();
            context.setVariable("firstName", user.getFirstName());
            context.setVariable("email", user.getEmail());
            context.setVariable("frontendUrl", frontendUrl);

            String html = templateEngine.process("email/welcome", context);
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

            String html = templateEngine.process("email/order-confirmation", context);
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

            String html = templateEngine.process("email/order-confirmation", context);
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

            String html = templateEngine.process("email/order-status", context);
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

            String html = templateEngine.process("email/order-status", context);
            sendEmail(email, "Actualización de tu pedido #" + order.getOrderNumber(), html);
            log.info("Guest order status email sent to: {} for order: {} - status: {}",
                    email, order.getOrderNumber(), order.getStatus());
        } catch (Exception e) {
            log.error("Failed to send guest order status email to: {}", email, e);
        }
    }

    @Async
    public void sendPasswordResetEmail(String email, String firstName, String resetToken) {
        try {
            Context context = new Context();
            context.setVariable("firstName", firstName);
            context.setVariable("resetLink", frontendUrl + "/reset-password?token=" + resetToken);

            String html = templateEngine.process("email/password-reset", context);
            sendEmail(email, "Restablecer tu contraseña", html);
            log.info("Password reset email sent to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", email, e);
        }
    }

    @Async
    public void sendPickupRescheduleEmail(String toEmail, String firstName, String orderNumber,
            String locationName, LocalDate pickupDate, String timeSlotLabel, String reason) {
        try {
            Context context = new Context();
            context.setVariable("firstName", firstName);
            context.setVariable("orderNumber", orderNumber);
            context.setVariable("locationName", locationName);
            context.setVariable("pickupDate", pickupDate);
            context.setVariable("timeSlotLabel", timeSlotLabel);
            context.setVariable("reason", reason);
            context.setVariable("frontendUrl", frontendUrl);

            String html = templateEngine.process("email/pickup-reschedule", context);
            sendEmail(toEmail, "Tu recolección ha sido cancelada — Pedido #" + orderNumber, html);
            log.info("Pickup reschedule email sent to: {} for order: {}", toEmail, orderNumber);
        } catch (Exception e) {
            log.error("Failed to send pickup reschedule email to: {}", toEmail, e);
        }
    }

    @Async
    public void sendPickupRescheduledConfirmationEmail(String toEmail, String firstName, String orderNumber,
            String locationName, LocalDate pickupDate, String timeSlotLabel) {
        try {
            Context context = new Context();
            context.setVariable("firstName", firstName);
            context.setVariable("orderNumber", orderNumber);
            context.setVariable("locationName", locationName);
            context.setVariable("pickupDate", pickupDate);
            context.setVariable("timeSlotLabel", timeSlotLabel);
            context.setVariable("frontendUrl", frontendUrl);

            String html = templateEngine.process("email/pickup-rescheduled-confirmation", context);
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
            context.setVariable("adminUrl", frontendUrl);

            String html = templateEngine.process("email/label-pending", context);
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
            context.setVariable("frontendUrl", frontendUrl);

            String html = templateEngine.process("email/shipment-tracking", context);
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
            context.setVariable("frontendUrl", frontendUrl);

            String html = templateEngine.process("email/shipment-tracking", context);
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
                context.setVariable("unsubscribeUrl", frontendUrl + "/unsubscribe?token=" + user.getUnsubscribeToken());
                context.setVariable("frontendUrl", frontendUrl);

                String html = templateEngine.process("email/marketing", context);

                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(fromEmail);
                helper.setTo(user.getEmail());
                helper.setSubject(subject);
                helper.setText(html, true);

                if (imageBytes != null && imageBytes.length > 0) {
                    helper.addInline("promoImage",
                            new org.springframework.core.io.ByteArrayResource(imageBytes),
                            imageContentType != null ? imageContentType : "image/jpeg");
                }
                mailSender.send(message);
                sent++;
            } catch (Exception e) {
                log.error("Failed to send marketing email to: {}", user.getEmail(), e);
            }
        }
        log.info("Marketing campaign sent: {}/{} emails delivered", sent, users.size());
    }

    /**
     * Synchronous (no @Async), no try/catch — lets the real SMTP error propagate
     * to the caller so the admin can see exactly what went wrong.
     */
    public void sendTestEmail(String toEmail) throws MessagingException {
        Context context = new Context();
        context.setVariable("firstName", "Test");
        context.setVariable("email", toEmail);
        context.setVariable("frontendUrl", frontendUrl);

        String html = templateEngine.process("email/welcome", context);
        sendEmail(toEmail, "Correo de prueba — E-Commerce Store", html);
        log.info("Test email sent to: {}", toEmail);
    }

    private void sendEmail(String to, String subject, String htmlContent) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);
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
