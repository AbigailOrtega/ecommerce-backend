package com.ecommerce.controller;

import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.dto.response.CouponResponse;
import com.ecommerce.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Coupons", description = "Coupon validation for users")
public class CouponController {

    private final CouponService couponService;

    @PostMapping("/validate")
    @Operation(summary = "Validate a coupon code")
    public ResponseEntity<ApiResponse<CouponResponse>> validate(@RequestParam String code) {
        return ResponseEntity.ok(ApiResponse.success(couponService.validateCoupon(code)));
    }
}
