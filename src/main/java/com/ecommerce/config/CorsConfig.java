package com.ecommerce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS for /api/**, applied by Spring Security (see SecurityConfig). In production the browser calls
 * the API through Vercel's same-origin /api rewrite, so only the frontend URL is allowed.
 */
@Configuration
public class CorsConfig {

    private static final List<String> LOCAL_ORIGINS = List.of(
            "http://localhost:4200",
            "http://localhost:3000",
            "http://127.0.0.1:4200",
            "http://127.0.0.1:3000",
            // the platform owner's page in development and e2e (app.platform.host)
            "http://plataforma.localhost:4200");

    private final Environment environment;

    @Value("${app.mail.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    public CorsConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
        // the refresh token cookie must cross from localhost:4200 to localhost:8080 in development
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private List<String> getAllowedOrigins() {
        // acceptsProfiles also sees profiles set with --spring.profiles.active or SPRING_PROFILES_ACTIVE
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            return List.of(frontendUrl);
        }
        return LOCAL_ORIGINS;
    }
}
