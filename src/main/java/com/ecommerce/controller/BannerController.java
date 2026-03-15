package com.ecommerce.controller;

import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.dto.response.PromoBannerResponse;
import com.ecommerce.service.PromoBannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/banner")
@RequiredArgsConstructor
@Tag(name = "Banner", description = "Public banner endpoints")
public class BannerController {

    private final PromoBannerService promoBannerService;

    @GetMapping("/active")
    @Operation(summary = "Get all active promotional banners")
    public ResponseEntity<ApiResponse<List<PromoBannerResponse>>> getActiveBanners() {
        return ResponseEntity.ok(ApiResponse.success(promoBannerService.getActiveBanners()));
    }
}
