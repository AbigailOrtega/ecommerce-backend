package com.ecommerce.controller;

import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.service.CloudinaryService;
import com.ecommerce.service.ImageCleanupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Upload", description = "File upload endpoints")
public class UploadController {

    private final CloudinaryService cloudinaryService;
    private final ImageCleanupService imageCleanupService;

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Upload an image to Cloudinary")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", defaultValue = "default") String type) {
        String url = cloudinaryService.upload(file, type);
        return ResponseEntity.ok(ApiResponse.success(Map.of("url", url)));
    }

    /**
     * Discards an image uploaded from an admin form that was then removed or cancelled before saving.
     * Images still used by any product, category, banner or store record are kept.
     */
    @DeleteMapping("/image")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete an uploaded image that was never saved")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> discardImage(@RequestParam("url") String url) {
        boolean deleted = imageCleanupService.deleteIfUnused(url);
        return ResponseEntity.ok(ApiResponse.success(Map.of("deleted", deleted)));
    }
}
