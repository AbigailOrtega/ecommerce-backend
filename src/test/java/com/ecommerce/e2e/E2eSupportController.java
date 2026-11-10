package com.ecommerce.e2e;

import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.User;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final Optional<FakePaymentService> fakePayments;

    /**
     * Settles one pending order the way AbandonedOrderJob does once it is an hour old (asks the
     * payment provider, then confirms or cancels it), so a test does not have to wait the hour.
     */
    @PostMapping("/orders/{orderNumber}/settle-abandoned")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> settleAbandoned(@PathVariable String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber));
        PaymentService.AbandonedOutcome outcome = paymentService.resolveAbandonedOrder(order.getId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("outcome", outcome.name())));
    }

    /** The password-reset token that the forgot-password email would contain. */
    @GetMapping("/reset-token")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> resetToken(@RequestParam String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        if (user.getResetToken() == null) throw new ResourceNotFoundException("Reset token", "email", email);
        return ResponseEntity.ok(ApiResponse.success(Map.of("token", user.getResetToken())));
    }

    /** This buyer's next PayPal capture fails, as when their funding source is declined. */
    @PostMapping("/paypal/decline-capture")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> declinePayPalCapture(@RequestParam String email) {
        fakePayments.orElseThrow(() -> new IllegalStateException("fake-payments profile is not active"))
                .declinePayPalCaptureFor(email);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
