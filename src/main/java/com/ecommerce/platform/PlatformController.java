package com.ecommerce.platform;

import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.tenant.Tenant;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * The platform owner's API (not a store's admin): create and suspend stores. Protected by the
 * PLATFORM_API_KEY secret sent as X-Platform-Key; without that variable the API does not exist (404).
 */
@RestController
@RequestMapping("/api/platform/stores")
public class PlatformController {

    private final PlatformService platformService;
    private final String apiKey;

    public PlatformController(PlatformService platformService, @Value("${app.platform.api-key:}") String apiKey) {
        this.platformService = platformService;
        this.apiKey = apiKey;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StoreSummary>>> list(@RequestHeader(value = "X-Platform-Key", required = false) String key) {
        authorize(key);
        return ResponseEntity.ok(ApiResponse.success(platformService.list()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StoreSummary>> create(@RequestHeader(value = "X-Platform-Key", required = false) String key,
                                                            @Valid @RequestBody CreateStoreRequest request) {
        authorize(key);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tienda creada; se envió la invitación al admin", platformService.create(request)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<StoreSummary>> status(@RequestHeader(value = "X-Platform-Key", required = false) String key,
                                                            @PathVariable Long id, @RequestParam Tenant.Status status) {
        authorize(key);
        return ResponseEntity.ok(ApiResponse.success(platformService.setStatus(id, status)));
    }

    private void authorize(String key) {
        if (apiKey == null || apiKey.length() < 32) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (key == null || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), apiKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }
}
