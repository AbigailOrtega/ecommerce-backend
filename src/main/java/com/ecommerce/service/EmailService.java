package com.ecommerce.service;

import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.entity.User;
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
            case "REFUNDED"   -> "Reembolsado";
            default           -> status;
        };
    }
}
