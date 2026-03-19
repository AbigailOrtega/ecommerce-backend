package com.ecommerce.service;

import com.ecommerce.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GoogleMapsService")
class GoogleMapsServiceTest {

    @Mock private RestTemplate restTemplate;

    @InjectMocks private GoogleMapsService googleMapsService;

    private static final String API_KEY    = "AIza_test_key";
    private static final String ORIGIN     = "Reforma 42, CDMX, Mexico";
    private static final String DESTINATION = "Insurgentes Sur 1000, CDMX, Mexico";

    @BeforeEach
    void injectRestTemplate() {
        // GoogleMapsService creates its own RestTemplate internally;
        // inject the mock so we can intercept HTTP calls.
        ReflectionTestUtils.setField(googleMapsService, "restTemplate", restTemplate);
    }

    // ─── calculateDistanceKm ─────────────────────────────────────────────────

    @Nested
    @DisplayName("calculateDistanceKm")
    class CalculateDistanceKm {

        @Test
        @DisplayName("returns distance in km from a valid API response")
        void calculateDistanceKm_success() {
            Map<String, Object> distanceMap = Map.of("value", 15_000, "text", "15 km");
            Map<String, Object> element     = Map.of("status", "OK", "distance", distanceMap);
            Map<String, Object> row         = Map.of("elements", List.of(element));
            Map<String, Object> apiResponse = Map.of(
                    "status", "OK",
                    "rows",   List.of(row)
            );

            when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(apiResponse);

            double km = googleMapsService.calculateDistanceKm(ORIGIN, DESTINATION, API_KEY);

            assertThat(km).isEqualTo(15.0);
        }

        @Test
        @DisplayName("handles non-integer distance value (double)")
        void calculateDistanceKm_doubleValue() {
            Map<String, Object> distanceMap = Map.of("value", 8_750.0);
            Map<String, Object> element     = Map.of("status", "OK", "distance", distanceMap);
            Map<String, Object> row         = Map.of("elements", List.of(element));
            Map<String, Object> apiResponse = Map.of(
                    "status", "OK",
                    "rows",   List.of(row)
            );

            when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(apiResponse);

            double km = googleMapsService.calculateDistanceKm(ORIGIN, DESTINATION, API_KEY);

            assertThat(km).isEqualTo(8.75);
        }

        @Test
        @DisplayName("throws BadRequestException when apiKey is null")
        void calculateDistanceKm_nullApiKey() {
            assertThatThrownBy(() ->
                    googleMapsService.calculateDistanceKm(ORIGIN, DESTINATION, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Google Maps API key is not configured");

            verifyNoInteractions(restTemplate);
        }

        @Test
        @DisplayName("throws BadRequestException when apiKey is blank")
        void calculateDistanceKm_blankApiKey() {
            assertThatThrownBy(() ->
                    googleMapsService.calculateDistanceKm(ORIGIN, DESTINATION, "   "))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Google Maps API key is not configured");

            verifyNoInteractions(restTemplate);
        }

        @Test
        @DisplayName("throws BadRequestException when origin is null")
        void calculateDistanceKm_nullOrigin() {
            assertThatThrownBy(() ->
                    googleMapsService.calculateDistanceKm(null, DESTINATION, API_KEY))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Origin address");

            verifyNoInteractions(restTemplate);
        }

        @Test
        @DisplayName("throws BadRequestException when origin is blank")
        void calculateDistanceKm_blankOrigin() {
            assertThatThrownBy(() ->
                    googleMapsService.calculateDistanceKm("", DESTINATION, API_KEY))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Origin address");

            verifyNoInteractions(restTemplate);
        }

        @Test
        @DisplayName("throws BadRequestException when API returns null response")
        void calculateDistanceKm_nullResponse() {
            when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(null);

            assertThatThrownBy(() ->
                    googleMapsService.calculateDistanceKm(ORIGIN, DESTINATION, API_KEY))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("No response from Google Maps API");
        }

        @Test
        @DisplayName("throws BadRequestException when top-level status is not OK")
        void calculateDistanceKm_topLevelStatusNotOk() {
            Map<String, Object> apiResponse = Map.of("status", "REQUEST_DENIED");
            when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(apiResponse);

            assertThatThrownBy(() ->
                    googleMapsService.calculateDistanceKm(ORIGIN, DESTINATION, API_KEY))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Google Maps API error: REQUEST_DENIED");
        }

        @Test
        @DisplayName("throws BadRequestException when rows list is empty")
        void calculateDistanceKm_emptyRows() {
            Map<String, Object> apiResponse = Map.of("status", "OK", "rows", List.of());
            when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(apiResponse);

            assertThatThrownBy(() ->
                    googleMapsService.calculateDistanceKm(ORIGIN, DESTINATION, API_KEY))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("No routes found.");
        }

        @Test
        @DisplayName("throws BadRequestException when elements list is empty")
        void calculateDistanceKm_emptyElements() {
            Map<String, Object> row         = Map.of("elements", List.of());
            Map<String, Object> apiResponse = Map.of("status", "OK", "rows", List.of(row));
            when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(apiResponse);

            assertThatThrownBy(() ->
                    googleMapsService.calculateDistanceKm(ORIGIN, DESTINATION, API_KEY))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("No route elements found.");
        }

        @Test
        @DisplayName("throws BadRequestException when element status is not OK")
        void calculateDistanceKm_elementStatusNotOk() {
            Map<String, Object> element     = Map.of("status", "ZERO_RESULTS");
            Map<String, Object> row         = Map.of("elements", List.of(element));
            Map<String, Object> apiResponse = Map.of("status", "OK", "rows", List.of(row));
            when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(apiResponse);

            assertThatThrownBy(() ->
                    googleMapsService.calculateDistanceKm(ORIGIN, DESTINATION, API_KEY))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Route calculation failed: ZERO_RESULTS");
        }

        @Test
        @DisplayName("throws BadRequestException when distance map is absent from element")
        void calculateDistanceKm_noDistanceMap() {
            Map<String, Object> element     = Map.of("status", "OK");  // no "distance" key
            Map<String, Object> row         = Map.of("elements", List.of(element));
            Map<String, Object> apiResponse = Map.of("status", "OK", "rows", List.of(row));
            when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(apiResponse);

            assertThatThrownBy(() ->
                    googleMapsService.calculateDistanceKm(ORIGIN, DESTINATION, API_KEY))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Distance data not available.");
        }

        @Test
        @DisplayName("wraps RestClientException in BadRequestException")
        void calculateDistanceKm_restClientException() {
            when(restTemplate.getForObject(anyString(), eq(Map.class)))
                    .thenThrow(new RestClientException("connection refused"));

            assertThatThrownBy(() ->
                    googleMapsService.calculateDistanceKm(ORIGIN, DESTINATION, API_KEY))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Failed to calculate distance");
        }

        @Test
        @DisplayName("builds URL that contains origin, destination, mode=driving, and apiKey params")
        void calculateDistanceKm_urlContainsRequiredParams() {
            Map<String, Object> distanceMap = Map.of("value", 5_000);
            Map<String, Object> element     = Map.of("status", "OK", "distance", distanceMap);
            Map<String, Object> row         = Map.of("elements", List.of(element));
            Map<String, Object> apiResponse = Map.of("status", "OK", "rows", List.of(row));

            when(restTemplate.getForObject(anyString(), eq(Map.class)))
                    .thenAnswer(inv -> {
                        String url = inv.getArgument(0);
                        assertThat(url).contains("mode=driving");
                        assertThat(url).contains(API_KEY);
                        return apiResponse;
                    });

            double km = googleMapsService.calculateDistanceKm(ORIGIN, DESTINATION, API_KEY);
            assertThat(km).isEqualTo(5.0);
        }
    }
}
