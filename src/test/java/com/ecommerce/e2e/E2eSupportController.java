package com.ecommerce.e2e;

import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.entity.User;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * Lets the Playwright suite read what a real user would get by email (profile "e2e-support",
 * test classes only, admin token required). Never part of the production jar.
 */
@RestController
@RequestMapping("/api/e2e")
@Profile("e2e-support")
@RequiredArgsConstructor
public class E2eSupportController {

    private final UserRepository userRepository;
    private final Optional<FakePaymentService> fakePayments;

    /** The password-reset token that the forgot-password email would contain. */
    @GetMapping("/reset-token")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> resetToken(@RequestParam String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        if (user.getResetToken() == null) throw new ResourceNotFoundException("Reset token", "email", email);
        return ResponseEntity.ok(ApiResponse.success(Map.of("token", user.getResetToken())));
    }

    /** The next PayPal capture fails, as when the buyer's funding source is declined. */
    @PostMapping("/paypal/decline-next-capture")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> declineNextPayPalCapture() {
        fakePayments.orElseThrow(() -> new IllegalStateException("fake-payments profile is not active"))
                .declineNextPayPalCapture();
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
