package com.ecommerce.service;

import com.ecommerce.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GoogleMapsService {

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String DISTANCE_MATRIX_URL =
            "https://maps.googleapis.com/maps/api/distancematrix/json";

    /**
     * Returns driving distance in km between origin and destination.
     *
     * @throws BadRequestException if the API key is missing or the API returns an error
     */
    public double calculateDistanceKm(String origin, String destination, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BadRequestException("Google Maps API key is not configured.");
        }
        if (origin == null || origin.isBlank()) {
            throw new BadRequestException("Origin address (store address) is not configured.");
        }

        String url = UriComponentsBuilder.fromHttpUrl(DISTANCE_MATRIX_URL)
                .queryParam("origins", origin)
                .queryParam("destinations", destination)
                .queryParam("mode", "driving")
                .queryParam("key", apiKey)
                .build()
                .toUriString();

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response == null) {
                throw new BadRequestException("No response from Google Maps API.");
            }

            String status = (String) response.get("status");
            if (!"OK".equals(status)) {
                throw new BadRequestException("Google Maps API error: " + status);
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) response.get("rows");
            if (rows == null || rows.isEmpty()) {
                throw new BadRequestException("No routes found.");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> elements =
                    (List<Map<String, Object>>) rows.get(0).get("elements");
            if (elements == null || elements.isEmpty()) {
                throw new BadRequestException("No route elements found.");
            }

            Map<String, Object> element = elements.get(0);
            String elementStatus = (String) element.get("status");
            if (!"OK".equals(elementStatus)) {
                throw new BadRequestException("Route calculation failed: " + elementStatus);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> distanceMap = (Map<String, Object>) element.get("distance");
            if (distanceMap == null) {
                throw new BadRequestException("Distance data not available.");
            }

            Number meters = (Number) distanceMap.get("value");
            return meters.doubleValue() / 1000.0;

        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error calling Google Maps Distance Matrix API", e);
            throw new BadRequestException("Failed to calculate distance: " + e.getMessage());
        }
    }
}
