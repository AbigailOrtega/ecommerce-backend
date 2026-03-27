package com.ecommerce.service;

import com.ecommerce.dto.response.SkydropxQuotationResponse;
import com.ecommerce.dto.response.SkydropxShipmentResponse;
import com.ecommerce.entity.*;
import com.ecommerce.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SkydropxService")
class SkydropxServiceTest {

    @Mock private ShippingConfigService shippingConfigService;
    @Mock private RestTemplate restTemplate;

    @InjectMocks private SkydropxService skydropxService;

    // ── Reusable fixtures ─────────────────────────────────────────────────────

    private ShippingConfig validConfig;
    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        // Inject mock RestTemplate and credentials (not populated by @Value in unit tests)
        ReflectionTestUtils.setField(skydropxService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(skydropxService, "clientId", "CLIENT_ID");
        ReflectionTestUtils.setField(skydropxService, "clientSecret", "CLIENT_SECRET");

        validConfig = ShippingConfig.builder()
                .skydropxClientId("CLIENT_ID")
                .skydropxClientSecret("CLIENT_SECRET")
                .skydropxOriginPostalCode("06600")
                .skydropxOriginCity("CDMX")
                .skydropxOriginState("CDMX")
                .skydropxOriginCountry("MX")
                .skydropxOriginStreet("Reforma 42")
                .skydropxSenderName("Test Shop")
                .skydropxSenderEmail("shop@test.com")
                .skydropxSenderPhone("5551234567")
                .skydropxDefaultWeight(1.0)
                .skydropxDefaultLength(30)
                .skydropxDefaultWidth(20)
                .skydropxDefaultHeight(15)
                .build();

        User user = User.builder()
                .firstName("Juan")
                .lastName("Perez")
                .email("juan@example.com")
                .phone("5559876543")
                .password("hashed")
                .build();

        sampleOrder = Order.builder()
                .orderNumber("ORD-SKY01")
                .user(user)
                .shippingAddress("Insurgentes Sur 1000")
                .shippingCity("CDMX")
                .shippingState("CDMX")
                .shippingZipCode("03100")
                .shippingCountry("MX")
                .skydropxRateId("RATE-1")
                .build();
    }

    // ─── Helper: stubs a successful OAuth token exchange ─────────────────────

    private void stubTokenExchange() {
        Map<String, Object> tokenResponse = Map.of(
                "access_token", "TOKEN-XYZ",
                "expires_in", 7200
        );
        when(restTemplate.postForObject(
                contains("/api/v1/oauth/token"), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(tokenResponse);
    }

    // ─── Helper: stubs a completed quotation (POST + GET poll) ───────────────

    @SuppressWarnings("unchecked")
    private void stubCompletedQuotation(String quotationId, List<Map<String, Object>> rates) {
        Map<String, Object> initialQuotation = new HashMap<>();
        initialQuotation.put("id", quotationId);
        initialQuotation.put("is_completed", false);

        Map<String, Object> completedQuotation = new HashMap<>();
        completedQuotation.put("id", quotationId);
        completedQuotation.put("is_completed", true);
        completedQuotation.put("rates", rates);

        ResponseEntity<Map> postResponse = ResponseEntity.ok(initialQuotation);
        ResponseEntity<Map> getResponse  = ResponseEntity.ok(completedQuotation);

        when(restTemplate.exchange(
                contains("/api/v1/quotations"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)))
                .thenReturn(postResponse);

        when(restTemplate.exchange(
                contains("/api/v1/quotations/" + quotationId),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Map.class)))
                .thenReturn(getResponse);
    }

    // ─── Helper: stubs the poll GET for pollShipmentUntilLabelReady ─────────

    @SuppressWarnings("unchecked")
    private void stubShipmentPoll(String shipmentId) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("workflow_status", "completed");
        Map<String, Object> data = new HashMap<>();
        data.put("id", shipmentId);
        data.put("attributes", attrs);
        Map<String, Object> pollResponse = new HashMap<>();
        pollResponse.put("data", data);

        when(restTemplate.exchange(
                contains("/api/v1/shipments/" + shipmentId),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Map.class)))
                .thenReturn(ResponseEntity.ok(pollResponse));
    }

    // ─── Helper: builds a successful rate map ────────────────────────────────

    private Map<String, Object> successRate(String id, String carrier, double total, int days) {
        Map<String, Object> rate = new HashMap<>();
        rate.put("id", id);
        rate.put("success", true);
        rate.put("provider_display_name", carrier);
        rate.put("provider_service_name", "Express");
        rate.put("total", total);
        rate.put("days", days);
        return rate;
    }

    // ─── invalidateTokenCache ─────────────────────────────────────────────────

    @Nested
    @DisplayName("invalidateTokenCache")
    class InvalidateTokenCache {

        @Test
        @DisplayName("clears cached token and catalog values without throwing")
        void invalidateTokenCache_clearsState() {
            assertThatNoException().isThrownBy(() -> skydropxService.invalidateTokenCache());

            // After invalidation a new token is fetched on next use
            // (validated indirectly through subsequent operation tests)
        }
    }

    // ─── getToken (via createQuotation) ──────────────────────────────────────

    @Nested
    @DisplayName("getToken (via createQuotation)")
    class GetToken {

        @Test
        @DisplayName("throws BadRequestException when credentials are blank")
        void getToken_missingCredentials() {
            // Override credentials set in setUp to simulate unconfigured service
            ReflectionTestUtils.setField(skydropxService, "clientId", "");
            ReflectionTestUtils.setField(skydropxService, "clientSecret", "");

            ShippingConfig cfg = ShippingConfig.builder()
                    .skydropxOriginPostalCode("06600")
                    .skydropxClientId("")
                    .skydropxClientSecret("")
                    .build();
            when(shippingConfigService.getOrCreate()).thenReturn(cfg);

            assertThatThrownBy(() -> skydropxService.createQuotation(sampleOrder))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Skydropx no está configurado");
        }

        @Test
        @DisplayName("throws BadRequestException when auth endpoint returns 401")
        void getToken_authError() {
            when(shippingConfigService.getOrCreate()).thenReturn(validConfig);

            when(restTemplate.postForObject(
                    contains("/api/v1/oauth/token"), any(HttpEntity.class), eq(Map.class)))
                    .thenThrow(HttpClientErrorException.create(
                            HttpStatus.UNAUTHORIZED, "Unauthorized",
                            HttpHeaders.EMPTY, new byte[0], null));

            assertThatThrownBy(() -> skydropxService.createQuotation(sampleOrder))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Error al autenticar con Skydropx");
        }

        @Test
        @DisplayName("throws BadRequestException when token response is null")
        void getToken_nullResponse() {
            when(shippingConfigService.getOrCreate()).thenReturn(validConfig);

            when(restTemplate.postForObject(
                    contains("/api/v1/oauth/token"), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(null);

            assertThatThrownBy(() -> skydropxService.createQuotation(sampleOrder))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Respuesta inválida de Skydropx al autenticar");
        }

        @Test
        @DisplayName("reuses cached token on second call without re-authenticating")
        void getToken_cachedToken() {
            when(shippingConfigService.getOrCreate()).thenReturn(validConfig);
            stubTokenExchange();
            stubCompletedQuotation("Q-CACHED", List.of(successRate("R1", "DHL", 100.0, 2)));

            // First call — should authenticate
            skydropxService.createQuotation(sampleOrder);
            // Second call — should reuse token
            skydropxService.createQuotation(sampleOrder);

            // OAuth endpoint should only be hit once
            verify(restTemplate, times(1)).postForObject(
                    contains("/api/v1/oauth/token"), any(HttpEntity.class), eq(Map.class));
        }
    }

    // ─── createQuotation ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("createQuotation")
    class CreateQuotation {

        @Test
        @DisplayName("returns quotation with rates parsed from completed API response")
        void createQuotation_success() {
            when(shippingConfigService.getOrCreate()).thenReturn(validConfig);
            stubTokenExchange();
            stubCompletedQuotation("Q-001", List.of(
                    successRate("R1", "DHL", 149.0, 2),
                    successRate("R2", "FedEx", 199.0, 1)
            ));

            SkydropxQuotationResponse response = skydropxService.createQuotation(sampleOrder);

            assertThat(response.quotationId()).isEqualTo("Q-001");
            assertThat(response.rates()).hasSize(2);
            assertThat(response.rates().get(0).carrier()).isEqualTo("DHL");
            assertThat(response.rates().get(0).price()).isEqualTo(149.0);
            assertThat(response.rates().get(1).carrier()).isEqualTo("FedEx");
        }

        @Test
        @DisplayName("filters out rates where success=false")
        void createQuotation_filtersFailedRates() {
            Map<String, Object> failedRate = new HashMap<>();
            failedRate.put("id", "R99");
            failedRate.put("success", false);
            failedRate.put("provider_display_name", "USPS");
            failedRate.put("total", 50.0);

            when(shippingConfigService.getOrCreate()).thenReturn(validConfig);
            stubTokenExchange();
            stubCompletedQuotation("Q-002", List.of(
                    successRate("R1", "DHL", 149.0, 2),
                    failedRate
            ));

            SkydropxQuotationResponse response = skydropxService.createQuotation(sampleOrder);

            assertThat(response.rates()).hasSize(1);
            assertThat(response.rates().get(0).id()).isEqualTo("R1");
        }

        @Test
        @DisplayName("throws BadRequestException when origin postal code is missing")
        void createQuotation_missingOriginPostalCode() {
            ShippingConfig cfg = ShippingConfig.builder()
                    .skydropxClientId("ID")
                    .skydropxClientSecret("SECRET")
                    .skydropxOriginPostalCode("")   // blank
                    .build();
            when(shippingConfigService.getOrCreate()).thenReturn(cfg);

            assertThatThrownBy(() -> skydropxService.createQuotation(sampleOrder))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("código postal de origen");
        }

        @Test
        @DisplayName("throws BadRequestException after max poll attempts without completion")
        void createQuotation_pollTimeout() {
            when(shippingConfigService.getOrCreate()).thenReturn(validConfig);
            stubTokenExchange();

            Map<String, Object> incompleteQuotation = Map.of("id", "Q-TIMEOUT", "is_completed", false);

            when(restTemplate.exchange(
                    contains("/api/v1/quotations"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(Map.class)))
                    .thenReturn(ResponseEntity.ok(incompleteQuotation));

            when(restTemplate.exchange(
                    contains("/api/v1/quotations/Q-TIMEOUT"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(Map.class)))
                    .thenReturn(ResponseEntity.ok(incompleteQuotation));  // never completes

            assertThatThrownBy(() -> skydropxService.createQuotation(sampleOrder))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("tardó demasiado");
        }

        @Test
        @DisplayName("throws BadRequestException when quotation POST returns 422")
        void createQuotation_httpError() {
            when(shippingConfigService.getOrCreate()).thenReturn(validConfig);
            stubTokenExchange();

            when(restTemplate.exchange(
                    contains("/api/v1/quotations"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(Map.class)))
                    .thenThrow(HttpClientErrorException.create(
                            HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable",
                            HttpHeaders.EMPTY, "{\"message\":\"invalid address\"}".getBytes(), null));

            assertThatThrownBy(() -> skydropxService.createQuotation(sampleOrder))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Error al cotizar con Skydropx");
        }
    }

    // ─── createQuotationForAddress ─────────────────────────────────────────────

    @Nested
    @DisplayName("createQuotationForAddress")
    class CreateQuotationForAddress {

        @Test
        @DisplayName("returns quotation for explicit address parameters")
        void createQuotationForAddress_success() {
            when(shippingConfigService.getOrCreate()).thenReturn(validConfig);
            stubTokenExchange();
            stubCompletedQuotation("Q-ADDR-01", List.of(successRate("R5", "Estafeta", 89.0, 3)));

            SkydropxQuotationResponse response = skydropxService.createQuotationForAddress(
                    "Calle Falsa 123", "Guadalajara", "Jalisco", "44100", "MX");

            assertThat(response.quotationId()).isEqualTo("Q-ADDR-01");
            assertThat(response.rates()).hasSize(1);
            assertThat(response.rates().get(0).carrier()).isEqualTo("Estafeta");
        }

        @Test
        @DisplayName("returns empty rates list when all API rates failed")
        void createQuotationForAddress_allRatesFailed() {
            Map<String, Object> failedRate = new HashMap<>();
            failedRate.put("id", "RF1");
            failedRate.put("success", false);

            when(shippingConfigService.getOrCreate()).thenReturn(validConfig);
            stubTokenExchange();
            stubCompletedQuotation("Q-EMPTY", List.of(failedRate));

            SkydropxQuotationResponse response = skydropxService.createQuotationForAddress(
                    "Av. Juárez 5", "Monterrey", "Nuevo León", "64000", "MX");

            assertThat(response.rates()).isEmpty();
        }
    }

    // ─── fetchShipment ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("fetchShipment")
    class FetchShipment {

        @Test
        @DisplayName("returns parsed shipment when API responds with flat format")
        @SuppressWarnings("unchecked")
        void fetchShipment_flatFormat() {
            stubTokenExchange();

            Map<String, Object> shipmentData = new HashMap<>();
            shipmentData.put("id", "SHIP-001");
            shipmentData.put("status", "pending");
            shipmentData.put("tracking_number", "TRK123456");
            shipmentData.put("carrier_name", "DHL");
            shipmentData.put("label_url", "https://skydropx.com/label/SHIP-001.pdf");

            ResponseEntity<Map> responseEntity = ResponseEntity.ok(shipmentData);
            when(restTemplate.exchange(
                    contains("/api/v1/shipments/SHIP-001"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(Map.class)))
                    .thenReturn(responseEntity);

            SkydropxShipmentResponse result = skydropxService.fetchShipment("SHIP-001");

            assertThat(result.shipmentId()).isEqualTo("SHIP-001");
            assertThat(result.trackingNumber()).isEqualTo("TRK123456");
            assertThat(result.carrierName()).isEqualTo("DHL");
            assertThat(result.labelUrl()).isEqualTo("https://skydropx.com/label/SHIP-001.pdf");
            assertThat(result.status()).isEqualTo("pending");
        }

        @Test
        @DisplayName("parses shipment with JSON:API data/attributes structure")
        @SuppressWarnings("unchecked")
        void fetchShipment_jsonApiFormat() {
            stubTokenExchange();

            Map<String, Object> attrs = new HashMap<>();
            attrs.put("status", "in_transit");
            attrs.put("carrier_name", "FedEx");

            Map<String, Object> packageAttrs = new HashMap<>();
            packageAttrs.put("tracking_number", "FX9876");
            packageAttrs.put("label_url", "https://cdn.skydropx.com/labels/001.pdf");

            Map<String, Object> packageIncluded = new HashMap<>();
            packageIncluded.put("type", "package");
            packageIncluded.put("attributes", packageAttrs);

            Map<String, Object> data = new HashMap<>();
            data.put("id", "SHIP-002");
            data.put("attributes", attrs);

            Map<String, Object> fullResponse = new HashMap<>();
            fullResponse.put("data", data);
            fullResponse.put("included", List.of(packageIncluded));

            ResponseEntity<Map> responseEntity = ResponseEntity.ok(fullResponse);
            when(restTemplate.exchange(
                    contains("/api/v1/shipments/SHIP-002"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(Map.class)))
                    .thenReturn(responseEntity);

            SkydropxShipmentResponse result = skydropxService.fetchShipment("SHIP-002");

            assertThat(result.shipmentId()).isEqualTo("SHIP-002");
            assertThat(result.trackingNumber()).isEqualTo("FX9876");
            assertThat(result.labelUrl()).isEqualTo("https://cdn.skydropx.com/labels/001.pdf");
            assertThat(result.status()).isEqualTo("in_transit");
        }

        @Test
        @DisplayName("throws BadRequestException when shipment fetch returns 404")
        @SuppressWarnings("unchecked")
        void fetchShipment_notFound() {
            stubTokenExchange();

            when(restTemplate.exchange(
                    contains("/api/v1/shipments/MISSING"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(Map.class)))
                    .thenThrow(HttpClientErrorException.create(
                            HttpStatus.NOT_FOUND, "Not Found",
                            HttpHeaders.EMPTY, new byte[0], null));

            assertThatThrownBy(() -> skydropxService.fetchShipment("MISSING"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Error al obtener guía de Skydropx");
        }
    }

    // ─── cancelShipment ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancelShipment")
    class CancelShipment {

        @Test
        @DisplayName("sends POST to cancellations endpoint without throwing")
        @SuppressWarnings("unchecked")
        void cancelShipment_success() {
            stubTokenExchange();

            when(restTemplate.exchange(
                    contains("/api/v1/shipments/SHIP-CANCEL/cancellations"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(Map.class)))
                    .thenReturn(ResponseEntity.ok(Map.of("status", "cancelled")));

            assertThatNoException().isThrownBy(() ->
                    skydropxService.cancelShipment("SHIP-CANCEL"));

            verify(restTemplate).exchange(
                    contains("/api/v1/shipments/SHIP-CANCEL/cancellations"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(Map.class));
        }

        @Test
        @DisplayName("throws BadRequestException when cancellation returns 422")
        void cancelShipment_httpError() {
            stubTokenExchange();

            when(restTemplate.exchange(
                    contains("/api/v1/shipments/SHIP-ERR/cancellations"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(Map.class)))
                    .thenThrow(HttpClientErrorException.create(
                            HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable",
                            HttpHeaders.EMPTY, "{\"message\":\"already cancelled\"}".getBytes(), null));

            assertThatThrownBy(() -> skydropxService.cancelShipment("SHIP-ERR"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Error al cancelar envío en Skydropx");
        }
    }

    // ─── downloadLabel ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("downloadLabel")
    class DownloadLabel {

        @Test
        @DisplayName("downloads label bytes using resolved label_url from re-fetched shipment")
        @SuppressWarnings("unchecked")
        void downloadLabel_fromResolvedUrl() {
            stubTokenExchange();

            // Step 1: re-fetch shipment to get latest label URL
            Map<String, Object> shipmentData = new HashMap<>();
            shipmentData.put("id", "SHIP-LBL");
            shipmentData.put("status", "ready");
            shipmentData.put("label_url", "https://storage.skydropx.com/labels/SHIP-LBL.pdf");

            ResponseEntity<Map> shipmentResponse = ResponseEntity.ok(shipmentData);
            when(restTemplate.exchange(
                    contains("/api/v1/shipments/SHIP-LBL"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(Map.class)))
                    .thenReturn(shipmentResponse);

            // Step 2: download PDF from resolved URL
            byte[] pdfBytes = new byte[]{37, 80, 68, 70};  // %PDF header
            ResponseEntity<byte[]> pdfResponse = ResponseEntity.ok(pdfBytes);
            when(restTemplate.exchange(
                    eq("https://storage.skydropx.com/labels/SHIP-LBL.pdf"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(byte[].class)))
                    .thenReturn(pdfResponse);

            byte[] result = skydropxService.downloadLabel("SHIP-LBL", "https://old-url.com/label.pdf");

            assertThat(result).isEqualTo(pdfBytes);
        }

        @Test
        @DisplayName("falls back to stored labelUrl when re-fetch fails")
        @SuppressWarnings("unchecked")
        void downloadLabel_fallbackToStoredUrl() {
            stubTokenExchange();

            // Re-fetch shipment fails
            when(restTemplate.exchange(
                    contains("/api/v1/shipments/SHIP-FB"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(Map.class)))
                    .thenThrow(new RuntimeException("connection timeout"));

            // Fallback: download from the stored labelUrl
            byte[] pdfBytes = new byte[]{37, 80, 68, 70};
            when(restTemplate.exchange(
                    eq("https://storage.skydropx.com/labels/fallback.pdf"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(byte[].class)))
                    .thenReturn(ResponseEntity.ok(pdfBytes));

            byte[] result = skydropxService.downloadLabel(
                    "SHIP-FB", "https://storage.skydropx.com/labels/fallback.pdf");

            assertThat(result).isEqualTo(pdfBytes);
        }

        @Test
        @DisplayName("throws BadRequestException when no label endpoint returns bytes")
        @SuppressWarnings("unchecked")
        void downloadLabel_noLabelAvailable() {
            stubTokenExchange();

            // Re-fetch returns shipment with no label URL
            Map<String, Object> shipmentData = new HashMap<>();
            shipmentData.put("id", "SHIP-NOLBL");
            shipmentData.put("status", "in_progress");
            // no label_url

            when(restTemplate.exchange(
                    contains("/api/v1/shipments/SHIP-NOLBL"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(Map.class)))
                    .thenReturn(ResponseEntity.ok(shipmentData));

            // All byte[] download attempts fail
            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(byte[].class)))
                    .thenThrow(new RuntimeException("not found"));

            assertThatThrownBy(() ->
                    skydropxService.downloadLabel("SHIP-NOLBL", null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("guía aún no está disponible");
        }
    }

    // ─── createShipment ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("createShipment")
    class CreateShipment {

        @Test
        @DisplayName("creates shipment and returns parsed response with label_url")
        @SuppressWarnings("unchecked")
        void createShipment_success() {
            when(shippingConfigService.getOrCreate()).thenReturn(validConfig);
            stubTokenExchange();

            // Fresh quotation re-quote
            stubCompletedQuotation("Q-FRESH", List.of(successRate("RATE-1", "DHL", 149.0, 2)));

            // Catalog: packagings
            when(restTemplate.exchange(
                    contains("/api/v1/consignment_notes/packagings"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(Object.class)))
                    .thenReturn(ResponseEntity.ok(
                            List.of(Map.of("code", "4G", "description", "Cardboard Box"))));

            // Catalog: categories
            when(restTemplate.exchange(
                    contains("/api/v1/consignment_notes/categories"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(Object.class)))
                    .thenReturn(ResponseEntity.ok(List.of(Map.of("id", 1L, "name", "Electronics"))));

            // Catalog: subcategories
            when(restTemplate.exchange(
                    contains("/api/v1/consignment_notes/categories/1/subcategories"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(Object.class)))
                    .thenReturn(ResponseEntity.ok(List.of(Map.of("id", 10L, "name", "Consumer Electronics"))));

            // Catalog: classes
            when(restTemplate.exchange(
                    contains("/api/v1/consignment_notes/subcategories/10/classes"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(Object.class)))
                    .thenReturn(ResponseEntity.ok(List.of(Map.of("code", "43211507", "name", "Smartphones"))));

            // Shipment creation response
            Map<String, Object> shipmentFlat = new HashMap<>();
            shipmentFlat.put("id", "SHIP-NEW");
            shipmentFlat.put("status", "pending");
            shipmentFlat.put("tracking_number", "TRK-999");
            shipmentFlat.put("carrier_name", "DHL");
            shipmentFlat.put("label_url", "https://skydropx.com/labels/SHIP-NEW.pdf");

            when(restTemplate.exchange(
                    contains("/api/v1/shipments"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(Map.class)))
                    .thenReturn(ResponseEntity.ok(shipmentFlat));

            stubShipmentPoll("SHIP-NEW");

            SkydropxShipmentResponse result =
                    skydropxService.createShipment("RATE-1", sampleOrder);

            assertThat(result.shipmentId()).isEqualTo("SHIP-NEW");
            assertThat(result.trackingNumber()).isEqualTo("TRK-999");
            assertThat(result.labelUrl()).isEqualTo("https://skydropx.com/labels/SHIP-NEW.pdf");
        }

        @Test
        @DisplayName("throws BadRequestException when no successful rates are available")
        @SuppressWarnings("unchecked")
        void createShipment_noRatesAvailable() {
            when(shippingConfigService.getOrCreate()).thenReturn(validConfig);
            stubTokenExchange();

            Map<String, Object> failedRate = new HashMap<>();
            failedRate.put("id", "RF99");
            failedRate.put("success", false);
            stubCompletedQuotation("Q-NO-RATES", List.of(failedRate));

            assertThatThrownBy(() ->
                    skydropxService.createShipment("RATE-MISSING", sampleOrder))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("No hay tarifas disponibles");
        }

        @Test
        @DisplayName("falls back to first successful rate when preferred rateId is not in fresh quotation")
        @SuppressWarnings("unchecked")
        void createShipment_fallbackToFirstRate() {
            when(shippingConfigService.getOrCreate()).thenReturn(validConfig);
            stubTokenExchange();

            // Quotation returns a different rate id than the preferred one
            stubCompletedQuotation("Q-FALLBACK", List.of(successRate("RATE-FRESH", "FedEx", 199.0, 1)));

            // Catalog — use SAT fallbacks (make catalog calls throw to trigger fallback codes)
            when(restTemplate.exchange(
                    contains("/api/v1/consignment_notes/packagings"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(Object.class)))
                    .thenThrow(new RuntimeException("catalog unavailable"));

            when(restTemplate.exchange(
                    contains("/api/v1/consignment_notes/categories"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(Object.class)))
                    .thenThrow(new RuntimeException("catalog unavailable"));

            // Shipment creation with rate RATE-FRESH (the fallback)
            Map<String, Object> shipmentResponse = new HashMap<>();
            shipmentResponse.put("id", "SHIP-FALLBACK");
            shipmentResponse.put("status", "pending");
            shipmentResponse.put("tracking_number", "TRK-FALLBACK");
            shipmentResponse.put("carrier_name", "FedEx");
            shipmentResponse.put("label_url", "https://cdn.skydropx.com/labels/SHIP-FALLBACK.pdf");

            when(restTemplate.exchange(
                    contains("/api/v1/shipments"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(Map.class)))
                    .thenReturn(ResponseEntity.ok(shipmentResponse));

            stubShipmentPoll("SHIP-FALLBACK");

            // Preferred rate "OLD-RATE" is absent from fresh quotation, so "RATE-FRESH" is chosen
            SkydropxShipmentResponse result =
                    skydropxService.createShipment("OLD-RATE", sampleOrder);

            assertThat(result.shipmentId()).isEqualTo("SHIP-FALLBACK");
        }

        @Test
        @DisplayName("polls shipment until label_url becomes available")
        @SuppressWarnings("unchecked")
        void createShipment_pollsForLabel() {
            when(shippingConfigService.getOrCreate()).thenReturn(validConfig);
            stubTokenExchange();
            stubCompletedQuotation("Q-POLL", List.of(successRate("RATE-P", "DHL", 100.0, 2)));

            // Catalog calls throw — use SAT fallback codes
            when(restTemplate.exchange(
                    contains("/api/v1/consignment_notes/packagings"),
                    eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class)))
                    .thenThrow(new RuntimeException("unavailable"));
            when(restTemplate.exchange(
                    contains("/api/v1/consignment_notes/categories"),
                    eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class)))
                    .thenThrow(new RuntimeException("unavailable"));

            // Initial shipment response has no label_url
            Map<String, Object> initialShipment = new HashMap<>();
            initialShipment.put("id", "SHIP-POLL");
            initialShipment.put("status", "in_progress");

            // Polled shipment response has label_url
            Map<String, Object> polledShipment = new HashMap<>();
            polledShipment.put("id", "SHIP-POLL");
            polledShipment.put("status", "ready");
            polledShipment.put("label_url", "https://cdn.skydropx.com/label/SHIP-POLL.pdf");

            when(restTemplate.exchange(
                    contains("/api/v1/shipments"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(Map.class)))
                    .thenReturn(ResponseEntity.ok(initialShipment));

            when(restTemplate.exchange(
                    contains("/api/v1/shipments/SHIP-POLL"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(Map.class)))
                    .thenReturn(ResponseEntity.ok(polledShipment));

            SkydropxShipmentResponse result =
                    skydropxService.createShipment("RATE-P", sampleOrder);

            assertThat(result.labelUrl()).isEqualTo("https://cdn.skydropx.com/label/SHIP-POLL.pdf");
        }

        @Test
        @DisplayName("throws BadRequestException when shipment POST returns 422")
        @SuppressWarnings("unchecked")
        void createShipment_httpError() {
            when(shippingConfigService.getOrCreate()).thenReturn(validConfig);
            stubTokenExchange();
            stubCompletedQuotation("Q-SHERR", List.of(successRate("RATE-ERR", "DHL", 100.0, 2)));

            when(restTemplate.exchange(
                    contains("/api/v1/consignment_notes/packagings"),
                    eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class)))
                    .thenThrow(new RuntimeException("unavailable"));
            when(restTemplate.exchange(
                    contains("/api/v1/consignment_notes/categories"),
                    eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class)))
                    .thenThrow(new RuntimeException("unavailable"));

            when(restTemplate.exchange(
                    contains("/api/v1/shipments"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(Map.class)))
                    .thenThrow(HttpClientErrorException.create(
                            HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable",
                            HttpHeaders.EMPTY, "{\"message\":\"bad address\"}".getBytes(), null));

            assertThatThrownBy(() ->
                    skydropxService.createShipment("RATE-ERR", sampleOrder))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Error al crear envío en Skydropx");
        }

        @Test
        @DisplayName("uses fallback sender phone when skydropxSenderPhone is blank")
        @SuppressWarnings("unchecked")
        void createShipment_fallbackSenderPhone() {
            ShippingConfig cfgNoPhone = ShippingConfig.builder()
                    .skydropxClientId("CLIENT_ID")
                    .skydropxClientSecret("CLIENT_SECRET")
                    .skydropxOriginPostalCode("06600")
                    .skydropxOriginCity("CDMX")
                    .skydropxOriginState("CDMX")
                    .skydropxOriginCountry("MX")
                    .skydropxOriginStreet("Reforma 42")
                    .skydropxSenderName("Test Shop")
                    .skydropxSenderEmail("shop@test.com")
                    .skydropxSenderPhone(null)   // phone missing
                    .skydropxDefaultWeight(1.0)
                    .skydropxDefaultLength(30)
                    .skydropxDefaultWidth(20)
                    .skydropxDefaultHeight(15)
                    .build();

            when(shippingConfigService.getOrCreate()).thenReturn(cfgNoPhone);
            stubTokenExchange();
            stubCompletedQuotation("Q-PHONE", List.of(successRate("RATE-PHN", "DHL", 100.0, 2)));

            when(restTemplate.exchange(
                    contains("/api/v1/consignment_notes/packagings"),
                    eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class)))
                    .thenThrow(new RuntimeException("unavailable"));
            when(restTemplate.exchange(
                    contains("/api/v1/consignment_notes/categories"),
                    eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class)))
                    .thenThrow(new RuntimeException("unavailable"));

            Map<String, Object> shipmentResponse = new HashMap<>();
            shipmentResponse.put("id", "SHIP-PHONE");
            shipmentResponse.put("status", "pending");
            shipmentResponse.put("label_url", "https://cdn.skydropx.com/labels/SHIP-PHONE.pdf");

            when(restTemplate.exchange(
                    contains("/api/v1/shipments"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(Map.class)))
                    .thenAnswer(inv -> {
                        HttpEntity<?> entity = inv.getArgument(2);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> body = (Map<String, Object>) entity.getBody();
                        @SuppressWarnings("unchecked")
                        Map<String, Object> shipment = (Map<String, Object>) body.get("shipment");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> addrFrom = (Map<String, Object>) shipment.get("address_from");
                        // Fallback phone must be "0000000000"
                        assertThat(addrFrom.get("phone")).isEqualTo("0000000000");
                        return ResponseEntity.ok(shipmentResponse);
                    });

            stubShipmentPoll("SHIP-PHONE");

            SkydropxShipmentResponse result =
                    skydropxService.createShipment("RATE-PHN", sampleOrder);

            assertThat(result.shipmentId()).isEqualTo("SHIP-PHONE");
        }
    }
}
