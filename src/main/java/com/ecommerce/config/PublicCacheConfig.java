package com.ecommerce.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets anonymous reads of the public catalog be cached for a minute. Spring Security otherwise marks
 * every response no-store, which also stops the frontend's server-side render from handing its API
 * responses to the browser (Angular skips no-store responses), so the browser fetched everything twice.
 * Security only adds its no-store header when the response has no Cache-Control yet.
 */
@Configuration
public class PublicCacheConfig implements WebMvcConfigurer {

    static final String PUBLIC_CACHE = "public, max-age=60";

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new PublicCatalogCacheInterceptor())
                .addPathPatterns("/api/products/**", "/api/categories/**", "/api/banner/**", "/api/store-info")
                .excludePathPatterns("/api/products/*/reviews/**");
    }

    static class PublicCatalogCacheInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            if ("GET".equals(request.getMethod()) && request.getHeader(HttpHeaders.AUTHORIZATION) == null) {
                response.setHeader(HttpHeaders.CACHE_CONTROL, PUBLIC_CACHE);
            }
            return true;
        }
    }
}
