package com.ecommerce.controller;

import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.dto.response.PostalCodeResponse;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.service.PostalCodeCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
@Tag(name = "Postal codes", description = "Mexico's postal code catalog (SEPOMEX)")
public class PostalCodeController {

    private final PostalCodeCatalogService catalog;

    @GetMapping("/api/shipping/postal-codes/{code}")
    @Operation(summary = "State, municipality and colonias of a Mexican postal code")
    public ResponseEntity<ApiResponse<PostalCodeResponse>> lookup(@PathVariable String code) {
        PostalCodeResponse found = catalog.lookup(code)
                .orElseThrow(() -> new ResourceNotFoundException("Código postal", "código", code));
        // the catalog changes a few times a year
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .body(ApiResponse.success(found));
    }

    @GetMapping("/api/admin/postal-codes/status")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "How many colonias are loaded, and how the last refresh went")
    public ResponseEntity<ApiResponse<PostalCodeCatalogService.Status>> status() {
        return ResponseEntity.ok(ApiResponse.success(catalog.status()));
    }

    @PostMapping("/api/admin/postal-codes/refresh")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Download the catalog from Correos de México again, in the background")
    public ResponseEntity<ApiResponse<PostalCodeCatalogService.Status>> refresh() {
        boolean started = catalog.startRefresh();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
                started ? "Actualizando el catálogo; tarda uno o dos minutos." : "Ya se está actualizando.",
                catalog.status()));
    }
}
