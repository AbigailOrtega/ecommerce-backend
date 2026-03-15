package com.ecommerce.service;

import com.ecommerce.dto.response.SkydropxQuotationResponse;
import com.ecommerce.dto.response.SkydropxRateDto;
import com.ecommerce.dto.response.SkydropxShipmentResponse;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.ShippingConfig;
import com.ecommerce.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.io.InputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class SkydropxService {

    private static final String PROD_URL    = "https://pro.skydropx.com";
    private static final String SANDBOX_URL = "https://sb-pro.skydropx.com";

    private final ShippingConfigService shippingConfigService;
    private final RestTemplate restTemplate = new RestTemplate();

    // ── Token cache ───────────────────────────────────────────────────────────
    private String cachedToken;
    private Instant tokenExpiresAt;
    private String cachedBaseUrl;

    // ── Consignment-note catalog cache (fetched once per base URL) ────────────
    private String cachedPackagingCode;       // for package_type
    private String cachedConsignmentNoteCode; // for consignment_note
    private String cachedCatalogBaseUrl;

    public synchronized void invalidateTokenCache() {
        cachedToken = null;
        cachedBaseUrl = null;
        cachedPackagingCode = null;
        cachedConsignmentNoteCode = null;
        cachedCatalogBaseUrl = null;
    }

    private String baseUrl() {
        ShippingConfig cfg = shippingConfigService.getOrCreate();
        return cfg.isSkydropxSandbox() ? SANDBOX_URL : PROD_URL;
    }

    private synchronized String getToken() {
        String currentBaseUrl = baseUrl();
        // Invalidate cache when switching between sandbox/production
        if (!currentBaseUrl.equals(cachedBaseUrl)) {
            cachedToken = null;
            cachedBaseUrl = currentBaseUrl;
        }
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }

        ShippingConfig cfg = shippingConfigService.getOrCreate();
        if (cfg.getSkydropxClientId() == null || cfg.getSkydropxClientId().isBlank()
                || cfg.getSkydropxClientSecret() == null || cfg.getSkydropxClientSecret().isBlank()) {
            throw new BadRequestException("Skydropx no está configurado. Ingresa el API Key y API Secret Key en la configuración de envíos.");
        }

        // OAuth2 client_credentials — form-encoded (not JSON)
        HttpHeaders tokenHeaders = new HttpHeaders();
        tokenHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> tokenBody = new LinkedMultiValueMap<>();
        tokenBody.add("client_id", cfg.getSkydropxClientId().trim());
        tokenBody.add("client_secret", cfg.getSkydropxClientSecret().trim());
        tokenBody.add("grant_type", "client_credentials");

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    baseUrl() + "/api/v1/oauth/token",
                    new HttpEntity<>(tokenBody, tokenHeaders),
                    Map.class);
            if (response == null || response.get("access_token") == null) {
                throw new BadRequestException("Respuesta inválida de Skydropx al autenticar.");
            }
            cachedToken = (String) response.get("access_token");
            Number expiresIn = (Number) response.getOrDefault("expires_in", 7200);
            tokenExpiresAt = Instant.now().plusSeconds(expiresIn.longValue() - 60);
            return cachedToken;
        } catch (HttpClientErrorException e) {
            log.error("Skydropx auth error [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BadRequestException("Error al autenticar con Skydropx: " + e.getStatusCode());
        }
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    // ── Quotation ─────────────────────────────────────────────────────────────

    public SkydropxQuotationResponse createQuotation(Order order) {
        ShippingConfig cfg = shippingConfigService.getOrCreate();
        validateOriginConfig(cfg);
        Map<String, Object> body = new HashMap<>();
        body.put("address_from", buildOriginAddress(cfg));
        body.put("address_to", buildAddressTo(
                order.getShippingZipCode(), order.getShippingState(),
                order.getShippingCity(), order.getShippingCountry()));
        body.put("parcel", buildDefaultParcel(cfg));
        return postQuotation(body);
    }

    public SkydropxQuotationResponse createQuotationForAddress(
            String street, String city, String state, String zipCode, String country) {
        ShippingConfig cfg = shippingConfigService.getOrCreate();
        validateOriginConfig(cfg);
        Map<String, Object> body = new HashMap<>();
        body.put("address_from", buildOriginAddress(cfg));
        body.put("address_to", buildAddressTo(zipCode, state, city, country));
        body.put("parcel", buildDefaultParcel(cfg));
        return postQuotation(body);
    }

    private SkydropxQuotationResponse postQuotation(Map<String, Object> body) {
        // Rails API expects body wrapped under "quotation" key
        Map<String, Object> wrapped = Map.of("quotation", body);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> initial = restTemplate.exchange(
                    baseUrl() + "/api/v1/quotations",
                    HttpMethod.POST,
                    new HttpEntity<>(wrapped, authHeaders()),
                    Map.class
            ).getBody();
            if (initial == null) throw new BadRequestException("Respuesta vacía de Skydropx.");
            // Skydropx processes quotations asynchronously — poll until is_completed=true
            String quotationId = String.valueOf(initial.get("id"));
            Map<String, Object> response = pollQuotationUntilCompleted(quotationId);
            return parseQuotationResponse(response);
        } catch (HttpClientErrorException e) {
            log.error("Skydropx quotation error [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BadRequestException("Error al cotizar con Skydropx: " + extractMessage(e));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> pollQuotationUntilCompleted(String quotationId) {
        for (int i = 0; i < 10; i++) {
            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
            try {
                Map<String, Object> response = restTemplate.exchange(
                        baseUrl() + "/api/v1/quotations/" + quotationId,
                        HttpMethod.GET,
                        new HttpEntity<>(authHeaders()),
                        Map.class
                ).getBody();
                if (response != null && Boolean.TRUE.equals(response.get("is_completed"))) {
                    return response;
                }
            } catch (HttpClientErrorException e) {
                log.error("Skydropx poll error [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
                throw new BadRequestException("Error al obtener cotización de Skydropx: " + e.getStatusCode());
            }
        }
        throw new BadRequestException("Skydropx tardó demasiado en cotizar. Intenta de nuevo.");
    }

    // ── Shipment ──────────────────────────────────────────────────────────────

    public SkydropxShipmentResponse createShipment(String preferredRateId, Order order) {
        ShippingConfig cfg = shippingConfigService.getOrCreate();
        validateOriginConfig(cfg);

        String senderPhone = (cfg.getSkydropxSenderPhone() != null && !cfg.getSkydropxSenderPhone().isBlank())
                ? cfg.getSkydropxSenderPhone() : "0000000000";

        Map<String, Object> addrFrom = new HashMap<>();
        addrFrom.put("name", minLength(cfg.getSkydropxSenderName(), "Remitente", 3));
        addrFrom.put("street1", cfg.getSkydropxOriginStreet() != null ? cfg.getSkydropxOriginStreet() : "");
        addrFrom.put("postal_code", cfg.getSkydropxOriginPostalCode() != null ? cfg.getSkydropxOriginPostalCode() : "");
        addrFrom.put("city", cfg.getSkydropxOriginCity() != null ? cfg.getSkydropxOriginCity() : "");
        addrFrom.put("state", cfg.getSkydropxOriginState() != null ? cfg.getSkydropxOriginState() : "");
        addrFrom.put("country", normalizeCountryCode(cfg.getSkydropxOriginCountry()));
        addrFrom.put("phone", senderPhone);
        addrFrom.put("email", cfg.getSkydropxSenderEmail() != null ? cfg.getSkydropxSenderEmail() : "");
        addrFrom.put("reference", "Origen");

        String recipientName = (order.getUser() != null)
                ? order.getUser().getFirstName() + " " + order.getUser().getLastName()
                : "Destinatario";
        String recipientPhone = (order.getUser() != null
                && order.getUser().getPhone() != null
                && !order.getUser().getPhone().isBlank())
                ? order.getUser().getPhone() : senderPhone;

        Map<String, Object> addrTo = new HashMap<>();
        addrTo.put("name", minLength(recipientName, "Destinatario", 3));
        addrTo.put("street1", order.getShippingAddress() != null ? order.getShippingAddress() : "");
        addrTo.put("postal_code", order.getShippingZipCode() != null ? order.getShippingZipCode() : "");
        addrTo.put("city", order.getShippingCity() != null ? order.getShippingCity() : "");
        addrTo.put("state", order.getShippingState() != null ? order.getShippingState() : "");
        addrTo.put("country", normalizeCountryCode(order.getShippingCountry()));
        addrTo.put("phone", recipientPhone);
        addrTo.put("email", order.getUser() != null ? order.getUser().getEmail() : "");
        addrTo.put("reference", "Pedido #" + order.getOrderNumber());

        // Re-quote to get a fresh rate_id tied to the current quotation (package_number must match)
        Map<String, Object> freshQuotation = createFreshQuotationRaw(cfg, order);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rates = (List<Map<String, Object>>)
                freshQuotation.getOrDefault("rates", List.of());

        // Use the same rate_id if it appears in the new quotation; otherwise pick the first successful rate
        String finalRateId = rates.stream()
                .filter(r -> Boolean.TRUE.equals(r.get("success")))
                .filter(r -> preferredRateId.equals(String.valueOf(r.get("id"))))
                .map(r -> String.valueOf(r.get("id")))
                .findFirst()
                .orElseGet(() -> rates.stream()
                        .filter(r -> Boolean.TRUE.equals(r.get("success")))
                        .map(r -> String.valueOf(r.get("id")))
                        .findFirst()
                        .orElseThrow(() -> new BadRequestException(
                                "No hay tarifas disponibles para este destino.")));

        // Build packages with valid consignment_note and package_type from Skydropx catalog
        Map<String, Object> pkg = new HashMap<>(buildDefaultParcel(cfg));
        pkg.put("package_number", 1);
        pkg.put("consignment_note", fetchConsignmentNoteCode());
        pkg.put("package_type", fetchPackagingCode());

        Map<String, Object> body = new HashMap<>();
        body.put("rate_id", finalRateId);
        body.put("address_from", addrFrom);
        body.put("address_to", addrTo);
        body.put("packages", List.of(pkg));

        Map<String, Object> wrapped = Map.of("shipment", body);
        log.info("Skydropx shipment request body: {}", wrapped);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.exchange(
                    baseUrl() + "/api/v1/shipments",
                    HttpMethod.POST,
                    new HttpEntity<>(wrapped, authHeaders()),
                    Map.class
            ).getBody();
            log.info("Skydropx shipment raw response: {}", response);
            SkydropxShipmentResponse shipment = parseShipmentResponse(response);

            // Poll until label_url is available (Skydropx generates it asynchronously)
            if (shipment.labelUrl().isBlank() && !shipment.shipmentId().isBlank()) {
                shipment = pollShipmentUntilLabelReady(shipment);
            }
            return shipment;
        } catch (HttpClientErrorException e) {
            log.error("Skydropx shipment error [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BadRequestException("Error al crear envío en Skydropx: " + extractMessage(e));
        }
    }

    /** Polls GET /shipments/{id} until workflow_status != in_progress and label_url is present. */
    @SuppressWarnings("unchecked")
    private SkydropxShipmentResponse pollShipmentUntilLabelReady(SkydropxShipmentResponse initial) {
        String shipmentId = initial.shipmentId();
        for (int i = 0; i < 15; i++) {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            try {
                Map<String, Object> response = restTemplate.exchange(
                        baseUrl() + "/api/v1/shipments/" + shipmentId,
                        HttpMethod.GET,
                        new HttpEntity<>(authHeaders()),
                        Map.class
                ).getBody();

                // Extract workflow_status from data.attributes
                String workflowStatus = "";
                if (response != null && response.get("data") instanceof Map<?,?> data) {
                    if (((Map<?,?>) data).get("attributes") instanceof Map<?,?> attrs) {
                        workflowStatus = nullToEmpty(attrs.get("workflow_status"));
                    }
                }

                SkydropxShipmentResponse polled = parseShipmentResponse(response);
                log.info("Skydropx shipment poll {}/15 — workflow_status={} label_url={}",
                        i + 1, workflowStatus, polled.labelUrl().isBlank() ? "(empty)" : polled.labelUrl());

                if (!polled.labelUrl().isBlank()) {
                    log.info("Skydropx label_url ready after {} polls", i + 1);
                    return polled;
                }
                // Stop polling if shipment finished processing but still no label (error state)
                if (!workflowStatus.isBlank() && !"in_progress".equals(workflowStatus)
                        && !"pending".equals(workflowStatus)) {
                    log.warn("Skydropx shipment workflow_status={}, stopping poll", workflowStatus);
                    return polled;
                }
            } catch (HttpClientErrorException e) {
                log.warn("Skydropx shipment poll error [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            }
        }
        log.warn("Skydropx label_url not ready after 15 polls (30s), saving shipment without it");
        return initial;
    }

    /** Creates a fresh quotation and returns the raw completed response (including packages). */
    @SuppressWarnings("unchecked")
    private Map<String, Object> createFreshQuotationRaw(ShippingConfig cfg, Order order) {
        Map<String, Object> qBody = new HashMap<>();
        qBody.put("address_from", buildOriginAddress(cfg));
        qBody.put("address_to", buildAddressTo(
                order.getShippingZipCode(), order.getShippingState(),
                order.getShippingCity(), order.getShippingCountry()));
        qBody.put("parcel", buildDefaultParcel(cfg));
        try {
            Map<String, Object> initial = restTemplate.exchange(
                    baseUrl() + "/api/v1/quotations",
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("quotation", qBody), authHeaders()),
                    Map.class
            ).getBody();
            if (initial == null) throw new BadRequestException("Respuesta vacía al re-cotizar.");
            log.info("Skydropx re-quotation raw response: {}", initial);
            return pollQuotationUntilCompleted(String.valueOf(initial.get("id")));
        } catch (HttpClientErrorException e) {
            log.error("Skydropx re-quotation error [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BadRequestException("Error al re-cotizar con Skydropx: " + extractMessage(e));
        }
    }

    /**
     * Downloads the label PDF bytes for a shipment.
     * Tries the Skydropx label endpoint first; falls back to the stored label URL with auth headers.
     */
    @SuppressWarnings("unchecked")
    public byte[] downloadLabel(String shipmentId, String labelUrl) {
        // Step 1: re-fetch the shipment to get the latest label URL from the API
        String resolvedLabelUrl = labelUrl;
        try {
            Map<String, Object> shipment = restTemplate.exchange(
                    baseUrl() + "/api/v1/shipments/" + shipmentId,
                    HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class
            ).getBody();
            log.info("Skydropx shipment for label (all keys): {}", shipment != null ? shipment.keySet() : "null");
            log.info("Skydropx shipment for label (full): {}", shipment);
            if (shipment != null) {
                SkydropxShipmentResponse parsed = parseShipmentResponse(shipment);
                if (!parsed.labelUrl().isBlank()) resolvedLabelUrl = parsed.labelUrl();
            }
        } catch (Exception e) {
            log.warn("Could not re-fetch shipment to get label URL: {}", e.getMessage());
        }

        // Step 2: try the resolved label URL with auth headers
        if (resolvedLabelUrl != null && resolvedLabelUrl.startsWith("http")) {
            try {
                HttpHeaders h = authHeaders();
                h.set("Accept", "application/pdf,application/octet-stream,*/*");
                ResponseEntity<byte[]> resp = restTemplate.exchange(
                        resolvedLabelUrl, HttpMethod.GET, new HttpEntity<>(h), byte[].class);
                if (resp.getBody() != null && resp.getBody().length > 0) {
                    log.info("Skydropx label downloaded from resolved URL");
                    return resp.getBody();
                }
            } catch (Exception e) {
                log.warn("Could not download label from resolved URL {}: {}", resolvedLabelUrl, e.getMessage());
            }
        }

        // Step 3: try known label endpoints
        String[] labelEndpoints = {
            baseUrl() + "/api/v1/shipments/" + shipmentId + "/labels",
            baseUrl() + "/api/v1/shipments/" + shipmentId + "/label"
        };
        for (String endpoint : labelEndpoints) {
            try {
                HttpHeaders h = authHeaders();
                h.set("Accept", "application/pdf,application/octet-stream,*/*");
                ResponseEntity<byte[]> resp = restTemplate.exchange(
                        endpoint, HttpMethod.GET, new HttpEntity<>(h), byte[].class);
                if (resp.getBody() != null && resp.getBody().length > 0) {
                    log.info("Skydropx label downloaded from {}", endpoint);
                    return resp.getBody();
                }
            } catch (Exception e) {
                log.debug("Label endpoint {} not available: {}", endpoint, e.getMessage());
            }
        }

        throw new BadRequestException("La guía aún no está disponible. Espera unos segundos e intenta de nuevo.");
    }

    /** Fetches the current state of a shipment (label URL, tracking, status). */
    public SkydropxShipmentResponse fetchShipment(String shipmentId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.exchange(
                    baseUrl() + "/api/v1/shipments/" + shipmentId,
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders()),
                    Map.class
            ).getBody();
            log.info("Skydropx fetch shipment raw response: {}", response);
            return parseShipmentResponse(response);
        } catch (HttpClientErrorException e) {
            log.error("Skydropx fetch shipment error [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BadRequestException("Error al obtener guía de Skydropx: " + extractMessage(e));
        }
    }

    public void cancelShipment(String shipmentId) {
        try {
            restTemplate.exchange(
                    baseUrl() + "/api/v1/shipments/" + shipmentId + "/cancellations",
                    HttpMethod.POST,
                    new HttpEntity<>(new HashMap<>(), authHeaders()),
                    Map.class
            );
        } catch (HttpClientErrorException e) {
            log.error("Skydropx cancel error [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BadRequestException("Error al cancelar envío en Skydropx: " + extractMessage(e));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void validateOriginConfig(ShippingConfig cfg) {
        if (cfg.getSkydropxOriginPostalCode() == null || cfg.getSkydropxOriginPostalCode().isBlank()) {
            throw new BadRequestException("Configura el código postal de origen de Skydropx antes de cotizar.");
        }
    }

    private Map<String, Object> buildOriginAddress(ShippingConfig cfg) {
        String city = cfg.getSkydropxOriginCity() != null ? cfg.getSkydropxOriginCity() : "";
        Map<String, Object> addr = new HashMap<>();
        addr.put("country_code", normalizeCountryCode(cfg.getSkydropxOriginCountry()));
        addr.put("postal_code", cfg.getSkydropxOriginPostalCode());
        addr.put("area_level1", cfg.getSkydropxOriginState() != null ? cfg.getSkydropxOriginState() : city);
        addr.put("area_level2", city);
        addr.put("area_level3", city);
        return addr;
    }

    private Map<String, Object> buildAddressTo(String zipCode, String state, String city, String country) {
        String c = city != null ? city : "";
        Map<String, Object> addr = new HashMap<>();
        addr.put("country_code", normalizeCountryCode(country));
        addr.put("postal_code", zipCode != null ? zipCode : "");
        addr.put("area_level1", state != null ? state : c);
        addr.put("area_level2", c);
        addr.put("area_level3", c);
        return addr;
    }

    /** Normalizes country to ISO 3166-1 alpha-2 as required by Skydropx */
    private String normalizeCountryCode(String country) {
        if (country == null || country.isBlank()) return "MX";
        return switch (country.trim().toUpperCase()) {
            case "MX", "MEX", "MEXICO", "MÉXICO" -> "MX";
            case "US", "USA", "UNITED STATES" -> "US";
            default -> country.trim().toUpperCase();
        };
    }

    private Map<String, Object> buildDefaultParcel(ShippingConfig cfg) {
        Map<String, Object> parcel = new HashMap<>();
        parcel.put("weight", cfg.getSkydropxDefaultWeight() != null ? cfg.getSkydropxDefaultWeight() : 1.0);
        parcel.put("length", cfg.getSkydropxDefaultLength() != null ? cfg.getSkydropxDefaultLength() : 30);
        parcel.put("width", cfg.getSkydropxDefaultWidth() != null ? cfg.getSkydropxDefaultWidth() : 20);
        parcel.put("height", cfg.getSkydropxDefaultHeight() != null ? cfg.getSkydropxDefaultHeight() : 15);
        return parcel;
    }

    @SuppressWarnings("unchecked")
    private SkydropxQuotationResponse parseQuotationResponse(Map<String, Object> response) {
        if (response == null) throw new BadRequestException("Respuesta vacía de Skydropx.");

        // Pro API: flat format — id and rates at root level
        String quotationId = String.valueOf(response.getOrDefault("id", "unknown"));
        List<SkydropxRateDto> rates = new ArrayList<>();
        List<Map<String, Object>> rawRates = (List<Map<String, Object>>) response.getOrDefault("rates", List.of());
        for (Map<String, Object> r : rawRates) {
            // Only include rates that were successfully priced
            if (Boolean.TRUE.equals(r.get("success"))) {
                rates.add(parseRate(r));
            }
        }

        return new SkydropxQuotationResponse(quotationId, rates);
    }

    private SkydropxRateDto parseRate(Map<String, Object> r) {
        String id = String.valueOf(r.getOrDefault("id", ""));
        String carrier = String.valueOf(r.getOrDefault("provider_display_name",
                r.getOrDefault("provider_name", "")));
        String service = String.valueOf(r.getOrDefault("provider_service_name",
                r.getOrDefault("provider_service_code", "")));
        double price = toDouble(r.getOrDefault("total", r.get("amount")));
        Integer days = toInteger(r.get("days"));
        return new SkydropxRateDto(id, carrier, service, price, days);
    }

    private double toDouble(Object val) {
        if (val == null) return 0;
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (NumberFormatException e) { return 0; }
    }

    private Integer toInteger(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.intValue();
        try { return Integer.parseInt(val.toString()); } catch (NumberFormatException e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private SkydropxShipmentResponse parseShipmentResponse(Map<String, Object> response) {
        if (response == null) throw new BadRequestException("Respuesta vacía de Skydropx al crear guía.");

        // Top-level data
        Map<String, Object> data = response.containsKey("data")
                ? (Map<String, Object>) response.get("data")
                : response;
        Map<String, Object> attrs = data.containsKey("attributes")
                ? (Map<String, Object>) data.get("attributes")
                : data;

        String shipmentId = String.valueOf(data.getOrDefault("id", ""));
        String status = String.valueOf(attrs.getOrDefault("status", "pending"));

        // Tracking & label may be in "included" array (JSON:API)
        String trackingNumber = "";
        String carrierName = "";
        String labelUrl = "";

        List<Map<String, Object>> included = (List<Map<String, Object>>) response.get("included");
        if (included != null && !included.isEmpty()) {
            // Find the package entry (not address_from / address_to)
            Map<String, Object> inc = included.stream()
                    .filter(e -> "package".equals(e.get("type")))
                    .findFirst()
                    .orElse(included.get(0));
            Map<String, Object> incAttrs = inc.containsKey("attributes")
                    ? (Map<String, Object>) inc.get("attributes")
                    : inc;
            trackingNumber = nullToEmpty(firstNonNull(incAttrs, "tracking_number", "tracking_code"));
            carrierName    = nullToEmpty(firstNonNull(attrs, "carrier_name", "carrier"));  // carrier is on data.attributes
            labelUrl       = nullToEmpty(firstNonNull(incAttrs, "label_url", "label", "pdf_url", "url"));
        } else {
            // Flat format fallback — also check labels[] array
            trackingNumber = nullToEmpty(firstNonNull(attrs, "tracking_number", "tracking_code"));
            carrierName    = nullToEmpty(firstNonNull(attrs, "carrier_name", "carrier"));
            labelUrl       = nullToEmpty(firstNonNull(attrs, "label_url", "label", "pdf_url", "url"));
            // Some Skydropx responses nest label under a "labels" array
            if (labelUrl.isEmpty()) {
                List<Map<String, Object>> labels = (List<Map<String, Object>>) attrs.get("labels");
                if (labels == null) labels = (List<Map<String, Object>>) response.get("labels");
                if (labels != null && !labels.isEmpty()) {
                    labelUrl = nullToEmpty(firstNonNull(labels.get(0), "label_url", "url", "pdf_url", "label"));
                }
            }
        }

        return new SkydropxShipmentResponse(shipmentId, trackingNumber, carrierName, labelUrl, status);
    }

    /**
     * Fetches the first available packaging code from the Skydropx catalog
     * (used as the `package_type` field when creating a shipment).
     * Result is cached per base URL.
     */
    @SuppressWarnings("unchecked")
    private synchronized String fetchPackagingCode() {
        String url = baseUrl();
        if (cachedPackagingCode != null && url.equals(cachedCatalogBaseUrl)) {
            return cachedPackagingCode;
        }
        try {
            Object raw = restTemplate.exchange(
                    url + "/api/v1/consignment_notes/packagings",
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders()),
                    Object.class
            ).getBody();
            log.info("Skydropx packagings response: {}", raw);
            String code = extractFirstCode(raw);
            if (code != null) {
                cachedPackagingCode = code;
                cachedCatalogBaseUrl = url;
                return code;
            }
        } catch (Exception e) {
            log.warn("Could not fetch Skydropx packaging codes: {}", e.getMessage());
        }
        return "4G"; // Caja de cartón — SAT fallback
    }

    /**
     * Fetches the first consignment-note class code from the Skydropx catalog hierarchy
     * (used as the `consignment_note` field when creating a shipment).
     * Result is cached per base URL.
     */
    @SuppressWarnings("unchecked")
    private synchronized String fetchConsignmentNoteCode() {
        String url = baseUrl();
        if (cachedConsignmentNoteCode != null && url.equals(cachedCatalogBaseUrl)) {
            return cachedConsignmentNoteCode;
        }
        try {
            // Step 1: categories
            Object rawCats = restTemplate.exchange(
                    url + "/api/v1/consignment_notes/categories",
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders()),
                    Object.class
            ).getBody();
            log.info("Skydropx consignment categories response: {}", rawCats);

            Long firstCatId = extractFirstId(rawCats);
            if (firstCatId == null) return "47131500";

            // Step 2: subcategories
            Object rawSubs = restTemplate.exchange(
                    url + "/api/v1/consignment_notes/categories/" + firstCatId + "/subcategories",
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders()),
                    Object.class
            ).getBody();
            log.info("Skydropx consignment subcategories response: {}", rawSubs);

            Long firstSubId = extractFirstId(rawSubs);
            if (firstSubId == null) return "47131500";

            // Step 3: classes
            Object rawClasses = restTemplate.exchange(
                    url + "/api/v1/consignment_notes/subcategories/" + firstSubId + "/classes",
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders()),
                    Object.class
            ).getBody();
            log.info("Skydropx consignment classes response: {}", rawClasses);

            String code = extractFirstCode(rawClasses);
            if (code != null) {
                cachedConsignmentNoteCode = code;
                cachedCatalogBaseUrl = url;
                return code;
            }
        } catch (Exception e) {
            log.warn("Could not fetch Skydropx consignment note classes: {}", e.getMessage());
        }
        return "47131500"; // SAT fallback
    }

    /** Extracts the first `code` field from a list or a wrapped `{data:[...]}` response. */
    @SuppressWarnings("unchecked")
    private String extractFirstCode(Object raw) {
        List<Map<String, Object>> list = toList(raw);
        if (list != null && !list.isEmpty()) {
            Object code = list.get(0).get("code");
            if (code != null && !code.toString().isBlank()) return code.toString();
        }
        return null;
    }

    /** Extracts the first `id` field as Long from a list or wrapped response. */
    @SuppressWarnings("unchecked")
    private Long extractFirstId(Object raw) {
        List<Map<String, Object>> list = toList(raw);
        if (list != null && !list.isEmpty()) {
            Object id = list.get(0).get("id");
            if (id instanceof Number n) return n.longValue();
            if (id != null) try { return Long.parseLong(id.toString()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    /** Normalizes an API response (array or `{data:[...]}` map) to a List. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toList(Object raw) {
        if (raw instanceof List<?> l) return (List<Map<String, Object>>) l;
        if (raw instanceof Map<?, ?> m) {
            Object data = m.get("data");
            if (data instanceof List<?> l) return (List<Map<String, Object>>) l;
        }
        return null;
    }

    /** Ensures a name is at least minLen characters; pads with spaces or uses fallback. */
    private String minLength(String value, String fallback, int minLen) {
        String v = (value != null && !value.isBlank()) ? value.trim() : fallback;
        while (v.length() < minLen) v = v + " ";
        return v;
    }

    /** Returns the first map value that is actually non-null among the given keys. */
    private Object firstNonNull(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key) && map.get(key) != null) return map.get(key);
        }
        return null;
    }

    /** Converts a value to String, returning "" for null (avoids the string "null"). */
    private String nullToEmpty(Object val) {
        if (val == null) return "";
        String s = val.toString();
        return "null".equals(s) ? "" : s;
    }

    private String extractMessage(HttpClientErrorException e) {
        try {
            String body = e.getResponseBodyAsString();
            // Try "message" key first, then "errors" when it's a plain string value
            for (String key : new String[]{"\"message\"", "\"errors\""}) {
                int keyIdx = body.indexOf(key);
                if (keyIdx >= 0) {
                    int colon = body.indexOf(':', keyIdx);
                    if (colon < 0) continue;
                    int q = body.indexOf('"', colon + 1);
                    if (q >= 0) {
                        int end = body.indexOf('"', q + 1);
                        if (end > q) return body.substring(q + 1, end);
                    }
                }
            }
        } catch (Exception ignored) {}
        return e.getStatusCode().toString();
    }
}
