package com.ecommerce.credentials;

import com.ecommerce.dto.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Admin → Pagos y envíos: the store's own Stripe, PayPal and Skydropx keys (admins only, /api/admin/**). */
@RestController
@RequestMapping("/api/admin/payment-settings")
@RequiredArgsConstructor
public class StoreCredentialsController {

    private final StoreCredentialsService service;

    @GetMapping
    public ResponseEntity<ApiResponse<StoreCredentialsService.View>> get() {
        return ResponseEntity.ok(ApiResponse.success(service.view()));
    }

    @PutMapping("/stripe")
    public ResponseEntity<ApiResponse<StoreCredentialsService.View>> stripe(@RequestBody StoreCredentialsService.StripeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Stripe guardado", service.saveStripe(request)));
    }

    @PutMapping("/paypal")
    public ResponseEntity<ApiResponse<StoreCredentialsService.View>> paypal(@RequestBody StoreCredentialsService.PayPalRequest request) {
        return ResponseEntity.ok(ApiResponse.success("PayPal guardado", service.savePayPal(request)));
    }

    @PutMapping("/skydropx")
    public ResponseEntity<ApiResponse<StoreCredentialsService.View>> skydropx(@RequestBody StoreCredentialsService.SkydropxRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Skydropx guardado", service.saveSkydropx(request)));
    }

    @PostMapping("/{provider}/disable")
    public ResponseEntity<ApiResponse<StoreCredentialsService.View>> remove(@PathVariable String provider,
                                                                           @RequestBody StoreCredentialsService.DisableRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Desactivado", service.remove(provider, request.currentPassword())));
    }
}
