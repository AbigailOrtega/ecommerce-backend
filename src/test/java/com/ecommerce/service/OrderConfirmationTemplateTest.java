package com.ecommerce.service;

import com.ecommerce.dto.response.OrderResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Renders the real order-confirmation email template (EmailServiceTest mocks the engine). */
@DisplayName("order-confirmation email template")
class OrderConfirmationTemplateTest {

    private static String render(String notes) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();   // SpEL, as in the app
        engine.setTemplateResolver(resolver);

        OrderResponse order = OrderResponse.builder()
                .orderNumber("ORD-TPL01").status("PENDING")
                .totalAmount(new BigDecimal("350.91")).shippingCost(new BigDecimal("99.00"))
                .shippingType("NATIONAL").shippingMethodName("Envío Nacional")
                .shippingAddress("Calle Falsa 123").shippingCity("CDMX").shippingState("CDMX")
                .shippingZipCode("06600").shippingNeighborhood("Roma Norte").shippingCountry("México")
                .items(List.of())
                .notes(notes)
                .build();
        Context context = new Context();
        context.setVariable("firstName", "Lucía");
        context.setVariable("order", order);
        context.setVariable("storeName", "Marea");
        context.setVariable("year", 2026);
        return engine.process("email/order-confirmation", context);
    }

    @Test
    @DisplayName("shows the buyer's delivery instructions")
    void withNotes() {
        assertThat(render("Casa azul, tocar timbre 2"))
                .contains("Tus indicaciones de entrega")
                .contains("Casa azul, tocar timbre 2");
    }

    @Test
    @DisplayName("is branded with the store name and year, not the old placeholder")
    void branding() {
        assertThat(render(null))
                .contains("<h1>Marea</h1>")
                .contains("© <span>2026</span> <span>Marea</span>")
                .doesNotContain("E-Commerce Store");
    }

    @Test
    @DisplayName("shows the colonia under the street")
    void colonia() {
        assertThat(render(null)).contains("Calle Falsa 123").contains("Col. Roma Norte");
    }

    @Test
    @DisplayName("a pickup order shows the button to reschedule or cancel")
    void pickupButton() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        Context context = new Context();
        context.setVariable("firstName", "Lucía");
        context.setVariable("storeName", "Marea");
        context.setVariable("year", 2026);
        context.setVariable("order", OrderResponse.builder().orderNumber("ORD-PK1").status("CONFIRMED")
                .totalAmount(new BigDecimal("100.00")).shippingType("PICKUP").pickupLocationName("Sucursal Centro")
                .pickupTimeSlotLabel("Lunes 10:00 – 14:00").items(List.of()).build());
        context.setVariable("pickupManageUrl", "https://www.mareatextiles.com/recoleccion/ORD-PK1?t=abc");

        assertThat(engine.process("email/order-confirmation", context))
                .contains("Reprogramar o cancelar")
                .contains("href=\"https://www.mareatextiles.com/recoleccion/ORD-PK1?t=abc\"");
    }

    @Test
    @DisplayName("has no instructions section when there are none")
    void withoutNotes() {
        assertThat(render(null)).doesNotContain("Tus indicaciones de entrega");
        assertThat(render("   ")).doesNotContain("Tus indicaciones de entrega");
    }
}
