package com.ecommerce.controller;

import com.ecommerce.config.SecurityConfig;
import com.ecommerce.dto.response.PickupLocationResponse;
import com.ecommerce.dto.response.ShippingCalculateResponse;
import com.ecommerce.dto.response.ShippingConfigResponse;
import com.ecommerce.dto.response.ShippingRatesResponse;
import com.ecommerce.dto.response.SkydropxQuotationResponse;
import com.ecommerce.dto.response.SkydropxRateDto;
import com.ecommerce.entity.ShippingConfig;
import com.ecommerce.security.JwtAuthFilter;
import com.ecommerce.service.PickupLocationService;
import com.ecommerce.service.ShippingConfigService;
import com.ecommerce.service.SkydropxService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    value = ShippingController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class},
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class)
    }
)
@DisplayName("ShippingController")
class ShippingControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ShippingConfigService shippingConfigService;
    @MockBean PickupLocationService pickupLocationService;
    @MockBean SkydropxService skydropxService;

    // ── Stub helpers ─────────────────────────────────────────────────────────

    private ShippingConfigResponse stubConfig() {
        return new ShippingConfigResponse(
                true, true,
                BigDecimal.valueOf(50), BigDecimal.valueOf(5),
                "Calle Falsa 123", BigDecimal.ZERO);
    }

    private PickupLocationResponse stubPickupLocation(Long id) {
        return new PickupLocationResponse(id, "Sucursal Centro", "Calle 5 de Mayo 10",
                "Ciudad de México", "CDMX", true);
    }

    private ShippingConfig stubShippingConfigEntity(boolean hasSkydropx) {
        ShippingConfig cfg = new ShippingConfig();
        if (hasSkydropx) {
            cfg.setSkydropxClientId("client-id");
            cfg.setSkydropxClientSecret("client-secret");
        }
        return cfg;
    }

    private ShippingCalculateResponse stubCalculate() {
        return new ShippingCalculateResponse(150.5, BigDecimal.valueOf(199.99));
    }

    private SkydropxQuotationResponse stubQuotation() {
        List<SkydropxRateDto> rates = List.of(
                new SkydropxRateDto("rate-1", "Estafeta", "Express", 180.00, 2));
        return new SkydropxQuotationResponse("quot-abc123", rates);
    }

    private Map<String, String> buildAddressBody() {
        return Map.of(
                "address", "Calle Falsa 123",
                "city", "Ciudad de México",
                "state", "CDMX",
                "zipCode", "06600",
                "country", "MX");
    }

    // ─── GET /api/shipping/config ─────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/shipping/config")
    class GetConfig {

        @Test
        @DisplayName("returns 200 with public shipping config")
        void getConfig_200() throws Exception {
            when(shippingConfigService.getPublicConfig()).thenReturn(stubConfig());

            mockMvc.perform(get("/api/shipping/config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.nationalEnabled").value(true))
                    .andExpect(jsonPath("$.data.pickupEnabled").value(true))
                    .andExpect(jsonPath("$.data.nationalBasePrice").value(50))
                    .andExpect(jsonPath("$.data.originAddress").value("Calle Falsa 123"))
                    .andExpect(jsonPath("$.data.whatsappNumber").value("5215512345678"));
        }
    }

    // ─── GET /api/shipping/pickup-locations ───────────────────────────────────

    @Nested
    @DisplayName("GET /api/shipping/pickup-locations")
    class GetPickupLocations {

        @Test
        @DisplayName("returns 200 with active pickup locations")
        void getPickupLocations_200() throws Exception {
            when(pickupLocationService.getActiveLocations())
                    .thenReturn(List.of(stubPickupLocation(1L), stubPickupLocation(2L)));

            mockMvc.perform(get("/api/shipping/pickup-locations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].name").value("Sucursal Centro"))
                    .andExpect(jsonPath("$.data[0].active").value(true));
        }

        @Test
        @DisplayName("returns 200 with empty list when no active locations")
        void getPickupLocations_200_empty() throws Exception {
            when(pickupLocationService.getActiveLocations()).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/shipping/pickup-locations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }

    // ─── POST /api/shipping/calculate-national ────────────────────────────────

    @Nested
    @DisplayName("POST /api/shipping/calculate-national")
    class CalculateNational {

        @Test
        @DisplayName("returns 200 with flat rate when Skydropx is not configured")
        void calculateNational_200_flatRate() throws Exception {
            when(shippingConfigService.getOrCreate()).thenReturn(stubShippingConfigEntity(false));
            when(shippingConfigService.calculateNational(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(stubCalculate());

            mockMvc.perform(post("/api/shipping/calculate-national")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildAddressBody())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.skydropxAvailable").value(false))
                    .andExpect(jsonPath("$.data.distanceKm").value(150.5))
                    .andExpect(jsonPath("$.data.flatPrice").value(199.99));
        }

        @Test
        @DisplayName("returns 200 with Skydropx rates when credentials are configured")
        void calculateNational_200_skydropxRates() throws Exception {
            when(shippingConfigService.getOrCreate()).thenReturn(stubShippingConfigEntity(true));
            when(skydropxService.createQuotationForAddress(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(stubQuotation());

            mockMvc.perform(post("/api/shipping/calculate-national")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildAddressBody())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.skydropxAvailable").value(true))
                    .andExpect(jsonPath("$.data.quotationId").value("quot-abc123"))
                    .andExpect(jsonPath("$.data.rates").isArray())
                    .andExpect(jsonPath("$.data.rates[0].carrier").value("Estafeta"))
                    .andExpect(jsonPath("$.data.rates[0].price").value(180.00));
        }

        @Test
        @DisplayName("returns 200 when body is empty map (null fields handled by service)")
        void calculateNational_200_emptyBody() throws Exception {
            when(shippingConfigService.getOrCreate()).thenReturn(stubShippingConfigEntity(false));
            when(shippingConfigService.calculateNational(any(), any(), any(), any(), any()))
                    .thenReturn(new ShippingCalculateResponse(0, BigDecimal.ZERO));

            mockMvc.perform(post("/api/shipping/calculate-national")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.skydropxAvailable").value(false));
        }

        @Test
        @DisplayName("returns 200 with zero distance and price on empty config")
        void calculateNational_200_zeroDistanceFlatConfig() throws Exception {
            ShippingConfig cfgNoSkydropx = stubShippingConfigEntity(false);
            when(shippingConfigService.getOrCreate()).thenReturn(cfgNoSkydropx);
            when(shippingConfigService.calculateNational(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(new ShippingCalculateResponse(0.0, BigDecimal.ZERO));

            mockMvc.perform(post("/api/shipping/calculate-national")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildAddressBody())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.flatPrice").value(0));
        }
    }
}
