package com.ecommerce.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("AdminBootstrap")
class AdminBootstrapTest {

    private final AdminAccountService service = mock(AdminAccountService.class);
    private final AdminBootstrap bootstrap = new AdminBootstrap(service);

    @Test
    @DisplayName("without ADMIN_EMAIL it does nothing")
    void unset() {
        ReflectionTestUtils.setField(bootstrap, "email", "");
        bootstrap.run(null);
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("with ADMIN_EMAIL it sets up the first admin")
    void set() {
        ReflectionTestUtils.setField(bootstrap, "email", "owner@shop.com");
        bootstrap.run(null);
        verify(service).bootstrap("owner@shop.com");
    }

    @Test
    @DisplayName("a failure is logged, never stops the store from starting")
    void failureDoesNotStopStartup() {
        ReflectionTestUtils.setField(bootstrap, "email", "not-an-email");
        when(service.bootstrap("not-an-email")).thenThrow(new IllegalStateException("boom"));
        assertThatCode(() -> bootstrap.run(null)).doesNotThrowAnyException();
    }
}
