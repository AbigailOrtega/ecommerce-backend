package com.ecommerce.platform;

import com.ecommerce.tenant.TenantFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

/**
 * The platform's own domain (PLATFORM_HOST). The platform's API only exists there: on a store's
 * domain, or with the variable unset, it is a 404.
 */
@Component
public class PlatformHost {

    private final String host;

    public PlatformHost(@Value("${app.platform.host:}") String host) {
        this.host = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
    }

    public void require(HttpServletRequest request) {
        if (host.isEmpty() || !host.equals(TenantFilter.requestHost(request))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    /** "https://plataforma.example.com" (http for a local host). */
    public String url() {
        return (host.endsWith("localhost") ? "http://" + host + ":4200" : "https://" + host);
    }
}
