package com.ecommerce.controller;

import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.entity.User;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Marketing", description = "Marketing email campaigns and unsubscribe")
public class MarketingController {

    private final UserRepository userRepository;
    private final EmailService emailService;

    @GetMapping("/admin/marketing/subscribers/count")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get count of marketing subscribers")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getSubscriberCount() {
        long count = userRepository.countByMarketingOptInTrue();
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", count)));
    }

    @PostMapping(value = "/admin/marketing/send", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Send marketing email to all opted-in subscribers")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendCampaign(
            @RequestParam String subject,
            @RequestParam String bodyText,
            @RequestParam(required = false) MultipartFile image) throws Exception {

        List<User> subscribers = userRepository.findAllByMarketingOptInTrue();
        if (subscribers.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(
                    Map.of("sent", 0, "message", "No hay suscriptores activos.")));
        }

        byte[] imageBytes = null;
        String imageContentType = null;
        if (image != null && !image.isEmpty()) {
            imageBytes = image.getBytes();
            imageContentType = image.getContentType();
        }

        emailService.sendMarketingEmailBulk(subscribers, subject, bodyText, imageBytes, imageContentType);

        log.info("Marketing campaign triggered for {} subscribers, subject: '{}'", subscribers.size(), subject);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("sent", subscribers.size(),
                       "message", "Campaña enviada a " + subscribers.size() + " suscriptor(es).")));
    }

    @GetMapping("/marketing/unsubscribe")
    @Operation(summary = "Unsubscribe from marketing emails using token")
    public ResponseEntity<ApiResponse<String>> unsubscribe(@RequestParam String token) {
        User user = userRepository.findByUnsubscribeToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Token", "token", token));
        user.setMarketingOptIn(false);
        userRepository.save(user);
        log.info("User {} unsubscribed from marketing emails", user.getEmail());
        return ResponseEntity.ok(ApiResponse.success("Has sido dado de baja correctamente.", "ok"));
    }
}
