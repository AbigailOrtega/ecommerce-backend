package com.ecommerce.controller;

import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.dto.response.PickupLocationResponse;
import com.ecommerce.dto.response.ShippingCalculateResponse;
import com.ecommerce.dto.response.ShippingConfigResponse;
import com.ecommerce.dto.response.ShippingRatesResponse;
import com.ecommerce.entity.ShippingConfig;
import com.ecommerce.service.PickupLocationService;
import com.ecommerce.service.ShippingConfigService;
import com.ecommerce.service.SkydropxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
@Tag(name = "Shipping", description = "Shipping configuration and calculation")
public class ShippingController {

    private final ShippingConfigService shippingConfigService;
    private final PickupLocationService pickupLocationService;
    private final SkydropxService skydropxService;

    @GetMapping("/config")
    @Operation(summary = "Get public shipping configuration")
    public ResponseEntity<ApiResponse<ShippingConfigResponse>> getConfig() {
        return ResponseEntity.ok(ApiResponse.success(shippingConfigService.getPublicConfig()));
    }

    @GetMapping("/pickup-locations")
    @Operation(summary = "Get active pickup locations")
    public ResponseEntity<ApiResponse<List<PickupLocationResponse>>> getPickupLocations() {
        return ResponseEntity.ok(ApiResponse.success(pickupLocationService.getActiveLocations()));
    }

    @PostMapping("/calculate-national")
    @Operation(summary = "Calculate national shipping cost (returns Skydropx rates if configured, otherwise flat price)")
    public ResponseEntity<ApiResponse<ShippingRatesResponse>> calculateNational(
            @RequestBody Map<String, String> body) {
        ShippingConfig cfg = shippingConfigService.getOrCreate();
        boolean hasSkydropx = cfg.getSkydropxClientId() != null && !cfg.getSkydropxClientId().isBlank()
                && cfg.getSkydropxClientSecret() != null && !cfg.getSkydropxClientSecret().isBlank();

        if (hasSkydropx) {
            var quotation = skydropxService.createQuotationForAddress(
                    body.get("address"), body.get("city"), body.get("state"),
                    body.get("zipCode"), body.get("country"));
            return ResponseEntity.ok(ApiResponse.success(
                    new ShippingRatesResponse(true, 0, null, quotation.quotationId(), quotation.rates())));
        }

        ShippingCalculateResponse calc = shippingConfigService.calculateNational(
                body.get("address"), body.get("city"), body.get("state"),
                body.get("zipCode"), body.get("country"));
        return ResponseEntity.ok(ApiResponse.success(
                new ShippingRatesResponse(false, calc.distanceKm(), calc.price(), null, List.of())));
    }
}
