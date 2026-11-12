package com.ecommerce.tenant;

import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TenancyConfig {

    /** Runs before Spring Security's filter chain, so login and token checks know the store. */
    @Bean
    public FilterRegistrationBean<TenantFilter> tenantFilter(TenantResolver resolver) {
        FilterRegistrationBean<TenantFilter> registration = new FilterRegistrationBean<>(new TenantFilter(resolver));
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 10);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
