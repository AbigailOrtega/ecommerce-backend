package com.ecommerce.controller;

import com.ecommerce.dto.request.StoreInfoRequest;
import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.dto.response.StoreInfoResponse;
import com.ecommerce.entity.StoreImage;
import com.ecommerce.service.StoreInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class StoreInfoController {

    private final StoreInfoService storeInfoService;

    @GetMapping("/api/store-info")
    public ResponseEntity<ApiResponse<StoreInfoResponse>> getPublic() {
        return ResponseEntity.ok(ApiResponse.success(storeInfoService.getPublic()));
    }

    @PutMapping("/api/admin/store-info")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StoreInfoResponse>> update(@RequestBody StoreInfoRequest request) {
        return ResponseEntity.ok(ApiResponse.success(storeInfoService.update(request)));
    }

    @PostMapping("/api/admin/store-info/images")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StoreImage>> addImage(@RequestBody Map<String, String> body) {
        StoreImage image = storeInfoService.addImage(body.get("url"));
        return ResponseEntity.ok(ApiResponse.success(image));
    }

    @DeleteMapping("/api/admin/store-info/images/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteImage(@PathVariable Long id) {
        storeInfoService.deleteImage(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/api/admin/store-info/images/reorder")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> reorderImages(@RequestBody List<Long> ids) {
        storeInfoService.reorderImages(ids);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
