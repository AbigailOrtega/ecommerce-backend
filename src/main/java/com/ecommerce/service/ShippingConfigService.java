package com.ecommerce.service;

import com.ecommerce.dto.request.ShippingConfigRequest;
import com.ecommerce.dto.response.ShippingCalculateResponse;
import com.ecommerce.dto.response.ShippingConfigAdminResponse;
import com.ecommerce.dto.response.ShippingConfigResponse;
import com.ecommerce.entity.ShippingConfig;
import com.ecommerce.repository.ShippingConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class ShippingConfigService {

    private final ShippingConfigRepository repository;
    private final GoogleMapsService googleMapsService;
    private final SkydropxService skydropxService;

    public ShippingConfigService(ShippingConfigRepository repository,
                                 GoogleMapsService googleMapsService,
                                 @Lazy SkydropxService skydropxService) {
        this.repository = repository;
        this.googleMapsService = googleMapsService;
        this.skydropxService = skydropxService;
    }

    @Transactional
    public ShippingConfig getOrCreate() {
        return repository.findById(1L).orElseGet(() -> {
            ShippingConfig cfg = ShippingConfig.builder().id(1L).build();
            return repository.save(cfg);
        });
    }

    public ShippingConfigResponse getPublicConfig() {
        ShippingConfig cfg = getOrCreate();
        return new ShippingConfigResponse(
                cfg.isNationalEnabled(),
                cfg.isPickupEnabled(),
                cfg.getNationalBasePrice(),
                cfg.getNationalPricePerKm(),
                cfg.getOriginAddress(),
                cfg.getPickupCost(),
                cfg.getWhatsappNumber()
        );
    }

    public ShippingConfigAdminResponse getAdminConfig() {
        ShippingConfig cfg = getOrCreate();
        return new ShippingConfigAdminResponse(
                cfg.isNationalEnabled(),
                cfg.isPickupEnabled(),
                cfg.getNationalBasePrice(),
                cfg.getNationalPricePerKm(),
                cfg.getOriginAddress(),
                cfg.getGoogleMapsApiKey() != null && !cfg.getGoogleMapsApiKey().isBlank(),
                cfg.getPickupCost(),
                // Skydropx
                cfg.getSkydropxClientId() != null && !cfg.getSkydropxClientId().isBlank()
                        && cfg.getSkydropxClientSecret() != null && !cfg.getSkydropxClientSecret().isBlank(),
                cfg.getSkydropxOriginStreet(),
                cfg.getSkydropxOriginPostalCode(),
                cfg.getSkydropxOriginCity(),
                cfg.getSkydropxOriginState(),
                cfg.getSkydropxOriginCountry(),
                cfg.getSkydropxSenderName(),
                cfg.getSkydropxSenderEmail(),
                cfg.getSkydropxSenderPhone(),
                cfg.getSkydropxDefaultWeight(),
                cfg.getSkydropxDefaultLength(),
                cfg.getSkydropxDefaultWidth(),
                cfg.getSkydropxDefaultHeight(),
                cfg.isSkydropxSandbox(),
                cfg.getWhatsappNumber()
        );
    }

    @Transactional
    public ShippingConfigAdminResponse updateConfig(ShippingConfigRequest request) {
        ShippingConfig cfg = getOrCreate();

        if (request.nationalEnabled() != null) cfg.setNationalEnabled(request.nationalEnabled());
        if (request.pickupEnabled() != null) cfg.setPickupEnabled(request.pickupEnabled());
        if (request.nationalBasePrice() != null) cfg.setNationalBasePrice(request.nationalBasePrice());
        if (request.nationalPricePerKm() != null) cfg.setNationalPricePerKm(request.nationalPricePerKm());
        if (request.originAddress() != null) cfg.setOriginAddress(request.originAddress());
        if (request.googleMapsApiKey() != null) cfg.setGoogleMapsApiKey(request.googleMapsApiKey());
        if (request.pickupCost() != null) cfg.setPickupCost(request.pickupCost());
        // Skydropx
        boolean credentialsChanged = false;
        if (request.skydropxClientId() != null) { cfg.setSkydropxClientId(request.skydropxClientId()); credentialsChanged = true; }
        if (request.skydropxClientSecret() != null) { cfg.setSkydropxClientSecret(request.skydropxClientSecret()); credentialsChanged = true; }
        if (request.skydropxSandbox() != null) credentialsChanged = true;
        if (request.skydropxOriginStreet() != null) cfg.setSkydropxOriginStreet(request.skydropxOriginStreet());
        if (request.skydropxOriginPostalCode() != null) cfg.setSkydropxOriginPostalCode(request.skydropxOriginPostalCode());
        if (request.skydropxOriginCity() != null) cfg.setSkydropxOriginCity(request.skydropxOriginCity());
        if (request.skydropxOriginState() != null) cfg.setSkydropxOriginState(request.skydropxOriginState());
        if (request.skydropxOriginCountry() != null) cfg.setSkydropxOriginCountry(request.skydropxOriginCountry());
        if (request.skydropxSenderName() != null) cfg.setSkydropxSenderName(request.skydropxSenderName());
        if (request.skydropxSenderEmail() != null) cfg.setSkydropxSenderEmail(request.skydropxSenderEmail());
        if (request.skydropxSenderPhone() != null) cfg.setSkydropxSenderPhone(request.skydropxSenderPhone());
        if (request.skydropxDefaultWeight() != null) cfg.setSkydropxDefaultWeight(request.skydropxDefaultWeight());
        if (request.skydropxDefaultLength() != null) cfg.setSkydropxDefaultLength(request.skydropxDefaultLength());
        if (request.skydropxDefaultWidth() != null) cfg.setSkydropxDefaultWidth(request.skydropxDefaultWidth());
        if (request.skydropxDefaultHeight() != null) cfg.setSkydropxDefaultHeight(request.skydropxDefaultHeight());
        if (request.skydropxSandbox() != null) cfg.setSkydropxSandbox(request.skydropxSandbox());
        if (request.whatsappNumber() != null) cfg.setWhatsappNumber(request.whatsappNumber().isBlank() ? null : request.whatsappNumber().trim());

        repository.save(cfg);
        if (credentialsChanged) skydropxService.invalidateTokenCache();
        return getAdminConfig();
    }

    public ShippingCalculateResponse calculateNational(String address, String city, String state,
                                                        String zipCode, String country) {
        ShippingConfig cfg = getOrCreate();
        if (cfg.getGoogleMapsApiKey() != null && !cfg.getGoogleMapsApiKey().isBlank()
                && cfg.getOriginAddress() != null && !cfg.getOriginAddress().isBlank()) {
            String destination = address + ", " + city + ", " + state + ", " + zipCode + ", " + country;
            double km = googleMapsService.calculateDistanceKm(cfg.getOriginAddress(), destination, cfg.getGoogleMapsApiKey());
            BigDecimal price = cfg.getNationalBasePrice()
                    .add(cfg.getNationalPricePerKm().multiply(BigDecimal.valueOf(km)))
                    .setScale(2, RoundingMode.HALF_UP);
            return new ShippingCalculateResponse(km, price);
        }
        // No Google Maps key — return flat base price
        return new ShippingCalculateResponse(0, cfg.getNationalBasePrice().setScale(2, RoundingMode.HALF_UP));
    }
}
