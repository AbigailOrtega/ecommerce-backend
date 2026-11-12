package com.ecommerce.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("E-mails are signed with the store's name")
class EmailSenderNameTest {

    @Test
    @DisplayName("keeps MAIL_FROM's address and puts the store's name in front")
    void replacesName() {
        assertThat(EmailService.withDisplayName("Marea Textiles <pedidos@mareatextiles.com>", "Papelería Demo"))
                .isEqualTo("\"Papelería Demo\" <pedidos@mareatextiles.com>");
        assertThat(EmailService.withDisplayName("pedidos@mareatextiles.com", "Marea Textiles"))
                .isEqualTo("\"Marea Textiles\" <pedidos@mareatextiles.com>");
    }

    @Test
    @DisplayName("a name cannot break the header")
    void sanitizes() {
        assertThat(EmailService.withDisplayName("a@b.com", "Evil\"\r\nBcc: x@y.com <z>"))
                .isEqualTo("\"EvilBcc: x@y.com z\" <a@b.com>");
        assertThat(EmailService.withDisplayName("a@b.com", "  ")).isEqualTo("a@b.com");
    }
}
