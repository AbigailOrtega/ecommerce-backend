package com.ecommerce.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PublicCacheConfig")
class PublicCacheConfigTest {

    private final PublicCacheConfig.PublicCatalogCacheInterceptor interceptor = new PublicCacheConfig.PublicCatalogCacheInterceptor();

    private String cacheControl(String method, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/products");
        if (authorization != null) request.addHeader("Authorization", authorization);
        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(request, response, new Object());
        return response.getHeader("Cache-Control");
    }

    @Test
    @DisplayName("anonymous catalog reads may be cached for a minute")
    void anonymousGet() {
        assertThat(cacheControl("GET", null)).isEqualTo("public, max-age=60");
    }

    @Test
    @DisplayName("signed-in reads and writes are left to Spring Security's no-store")
    void authenticatedOrWrite() {
        assertThat(cacheControl("GET", "Bearer abc")).isNull();
        assertThat(cacheControl("POST", null)).isNull();
    }
}
