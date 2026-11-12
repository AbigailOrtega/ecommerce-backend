package com.ecommerce.platform;

import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.entity.Role;
import com.ecommerce.entity.User;
import com.ecommerce.tenant.TenantContext;
import com.ecommerce.tenant.Tenant;
import com.ecommerce.tenant.TenantFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * The platform's API (the super admin's, not a store admin's): create stores, invite their admins,
 * add domains, suspend them. It only exists on the platform's own domain (PLATFORM_HOST; a store's
 * domain gets a 404) and only for a super admin of store #1 (role set by hand in the database) logged in there.
 */
@RestController
@RequestMapping("/api/platform/stores")
public class PlatformController {

    private final PlatformService platformService;
    private final String platformHost;
    private final HttpServletRequest request;

    public PlatformController(PlatformService platformService,
                              @Value("${app.platform.host:}") String platformHost,
                              HttpServletRequest request) {
        this.platformService = platformService;
        this.platformHost = platformHost.trim().toLowerCase(java.util.Locale.ROOT);
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

    /**
     * Only on the platform's domain (elsewhere a 404, as if it did not exist), and only for a super
     * admin of store #1 logged in there.
     */
    private void authorize() {
        if (platformHost.isEmpty() || !platformHost.equals(TenantFilter.requestHost(request))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (user.getRole() != Role.SUPER_ADMIN || TenantContext.current() != TenantContext.DEFAULT_TENANT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta cuenta no es super admin");
        }
    }
}
