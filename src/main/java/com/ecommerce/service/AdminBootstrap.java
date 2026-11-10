package com.ecommerce.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Sets up the store's first admin from ADMIN_EMAIL (app.admin.bootstrap-email) at startup.
 * Does nothing once any admin exists, so the variable can stay set.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final AdminAccountService adminAccountService;

    @Value("${app.admin.bootstrap-email:}")
    private String email;

    @Override
    public void run(ApplicationArguments args) {
        if (email == null || email.isBlank()) return;
        try {
            if (!adminAccountService.bootstrap(email)) {
                log.debug("ADMIN_EMAIL ignored: the store already has an admin");
            }
        } catch (RuntimeException e) {
            // a bad value must not keep the store from starting
            log.error("Could not set up the first admin from ADMIN_EMAIL", e);
        }
    }
}
