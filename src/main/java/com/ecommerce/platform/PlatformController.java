package com.ecommerce.platform;

import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.tenant.Tenant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The platform's API (the super admins', not a store admin's): create stores, invite their admins,
 * add domains, suspend them. Only on the platform's own domain (PLATFORM_HOST; a store's domain
 * gets a 404) and only with a super admin's session (PlatformAuthController).
 */
@RestController
@RequestMapping("/api/platform/stores")
public class PlatformController {

    private final PlatformService platformService;
    private final PlatformHost platformHost;
    private final PlatformAuthService platformAuth;
    private final HttpServletRequest request;

    public PlatformController(PlatformService platformService, PlatformHost platformHost,
                              PlatformAuthService platformAuth, HttpServletRequest request) {
        this.platformService = platformService;
        this.platformHost = platformHost;
        this.platformAuth = platformAuth;
        this.request = request;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StoreSummary>>> list() {
        authorize();
        return ResponseEntity.ok(ApiResponse.success(platformService.list()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StoreSummary>> create(@Valid @RequestBody CreateStoreRequest request) {
        authorize();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tienda creada; se envió la invitación al admin", platformService.create(request)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<StoreSummary>> status(@PathVariable Long id, @RequestParam Tenant.Status status) {
        authorize();
        return ResponseEntity.ok(ApiResponse.success(platformService.setStatus(id, status)));
    }

    public record InviteRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email String email,
                                String firstName, String lastName) {}

    public record DomainRequest(@jakarta.validation.constraints.NotBlank String domain) {}

    @PostMapping("/{id}/invite")
    public ResponseEntity<ApiResponse<Void>> invite(@PathVariable Long id, @Valid @RequestBody InviteRequest request) {
        authorize();
        platformService.inviteAdmin(id, request.email(), request.firstName(), request.lastName());
        return ResponseEntity.ok(ApiResponse.success("Invitación enviada a " + request.email(), null));
    }

    @PostMapping("/{id}/domains")
    public ResponseEntity<ApiResponse<StoreSummary>> addDomain(@PathVariable Long id, @Valid @RequestBody DomainRequest request) {
        authorize();
        return ResponseEntity.ok(ApiResponse.success(platformService.addDomain(id, request.domain())));
    }

    /** Only on the platform's domain (elsewhere a 404, as if it did not exist), and only with a super admin's session. */
    private void authorize() {
        platformHost.require(request);
        platformAuth.authenticate(request.getHeader("Authorization"));
    }
}
