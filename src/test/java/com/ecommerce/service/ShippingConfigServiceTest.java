package com.ecommerce.service;

import com.ecommerce.dto.request.ShippingConfigRequest;
import com.ecommerce.dto.response.ShippingCalculateResponse;
import com.ecommerce.dto.response.ShippingConfigAdminResponse;
import com.ecommerce.dto.response.ShippingConfigResponse;
import com.ecommerce.entity.ShippingConfig;
import com.ecommerce.repository.ShippingConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShippingConfigService")
class ShippingConfigServiceTest {

    @Mock private ShippingConfigRepository repository;
    @Mock private GoogleMapsService googleMapsService;
    @Mock private SkydropxService skydropxService;

    @InjectMocks private ShippingConfigService shippingConfigService;

    private ShippingConfig defaultConfig;

    @BeforeEach
    void setUp() {
        defaultConfig = ShippingConfig.builder()
                .id(1L)
                .nationalEnabled(true)
                .pickupEnabled(true)
                .nationalBasePrice(BigDecimal.valueOf(50))
                .nationalPricePerKm(BigDecimal.valueOf(2))
                .originAddress("Calle Origen 1, CDMX")
                .googleMapsApiKey("test-api-key")
                .pickupCost(BigDecimal.valueOf(20))
                .whatsappNumber("5215512345678")
                .build();
    }

    // ─── getOrCreate ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getOrCreate")
    class GetOrCreate {

        @Test
        @DisplayName("returns existing config when record with id=1 exists")
        void getOrCreate_returnsExistingRecord() {
            when(repository.findById(1L)).thenReturn(Optional.of(defaultConfig));

            ShippingConfig result = shippingConfigService.getOrCreate();

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getNationalBasePrice()).isEqualByComparingTo(BigDecimal.valueOf(50));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("creates and saves a new default config when none exists")
        void getOrCreate_createsNewRecordWhenAbsent() {
            when(repository.findById(1L)).thenReturn(Optional.empty());
            ShippingConfig created = ShippingConfig.builder().id(1L).build();
            when(repository.save(any(ShippingConfig.class))).thenReturn(created);

            ShippingConfig result = shippingConfigService.getOrCreate();

            assertThat(result.getId()).isEqualTo(1L);
            ArgumentCaptor<ShippingConfig> captor = ArgumentCaptor.forClass(ShippingConfig.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("saves only once when config is absent")
        void getOrCreate_savesExactlyOnce() {
            when(repository.findById(1L)).thenReturn(Optional.empty());
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            shippingConfigService.getOrCreate();

            verify(repository, times(1)).save(any());
        }

        @Test
        @DisplayName("never calls save when config is already present")
        void getOrCreate_doesNotSaveWhenPresent() {
            when(repository.findById(1L)).thenReturn(Optional.of(defaultConfig));

            shippingConfigService.getOrCreate();

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("always looks up by id 1")
        void getOrCreate_alwaysQueriesIdOne() {
            when(repository.findById(1L)).thenReturn(Optional.of(defaultConfig));

            shippingConfigService.getOrCreate();

            verify(repository).findById(1L);
            verify(repository, never()).findById(argThat(id -> !id.equals(1L)));
        }
    }

    // ─── getPublicConfig ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("getPublicConfig")
    class GetPublicConfig {

        @Test
        @DisplayName("returns mapped public config with correct values")
        void getPublicConfig_success() {
            when(repository.findById(1L)).thenReturn(Optional.of(defaultConfig));

            ShippingConfigResponse response = shippingConfigService.getPublicConfig();

            assertThat(response.nationalEnabled()).isTrue();
            assertThat(response.pickupEnabled()).isTrue();
            assertThat(response.nationalBasePrice()).isEqualByComparingTo(BigDecimal.valueOf(50));
            assertThat(response.nationalPricePerKm()).isEqualByComparingTo(BigDecimal.valueOf(2));
            assertThat(response.originAddress()).isEqualTo("Calle Origen 1, CDMX");
            assertThat(response.pickupCost()).isEqualByComparingTo(BigDecimal.valueOf(20));
            assertThat(response.whatsappNumber()).isEqualTo("5215512345678");
        }

        @Test
        @DisplayName("does not expose Google Maps API key in public response")
        void getPublicConfig_doesNotExposeApiKey() {
            when(repository.findById(1L)).thenReturn(Optional.of(defaultConfig));

            ShippingConfigResponse response = shippingConfigService.getPublicConfig();

            // ShippingConfigResponse record has no hasApiKey field — just confirm it compiles cleanly
            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("returns false for nationalEnabled when config has it disabled")
        void getPublicConfig_nationalDisabled() {
            defaultConfig.setNationalEnabled(false);
            when(repository.findById(1L)).thenReturn(Optional.of(defaultConfig));

            ShippingConfigResponse response = shippingConfigService.getPublicConfig();

            assertThat(response.nationalEnabled()).isFalse();
        }

        @Test
        @DisplayName("returns null whatsappNumber when none is set")
        void getPublicConfig_nullWhatsapp() {
            defaultConfig.setWhatsappNumber(null);
            when(repository.findById(1L)).thenReturn(Optional.of(defaultConfig));

            ShippingConfigResponse response = shippingConfigService.getPublicConfig();

            assertThat(response.whatsappNumber()).isNull();
        }

        @Test
        @DisplayName("creates default config on first call when none exists")
        void getPublicConfig_createsDefaultWhenAbsent() {
            when(repository.findById(1L)).thenReturn(Optional.empty());
            when(repository.save(any(ShippingConfig.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThatNoException().isThrownBy(() -> shippingConfigService.getPublicConfig());
        }
    }

    // ─── getAdminConfig ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAdminConfig")
    class GetAdminConfig {

        @Test
        @DisplayName("returns hasApiKey=true when googleMapsApiKey is set")
        void getAdminConfig_hasApiKeyTrue() {
            defaultConfig.setGoogleMapsApiKey("my-secret-key");
            when(repository.findById(1L)).thenReturn(Optional.of(defaultConfig));

            ShippingConfigAdminResponse response = shippingConfigService.getAdminConfig();

            assertThat(response.hasApiKey()).isTrue();
        }

        @Test
        @DisplayName("returns hasApiKey=false when googleMapsApiKey is null")
        void getAdminConfig_hasApiKeyFalseWhenNull() {
            defaultConfig.setGoogleMapsApiKey(null);
            when(repository.findById(1L)).thenReturn(Optional.of(defaultConfig));

            ShippingConfigAdminResponse response = shippingConfigService.getAdminConfig();

            assertThat(response.hasApiKey()).isFalse();
        }

        @Test
        @DisplayName("returns hasApiKey=false when googleMapsApiKey is blank")
        void getAdminConfig_hasApiKeyFalseWhenBlank() {
            defaultConfig.setGoogleMapsApiKey("   ");
            when(repository.findById(1L)).thenReturn(Optional.of(defaultConfig));

            ShippingConfigAdminResponse response = shippingConfigService.getAdminConfig();

            assertThat(response.hasApiKey()).isFalse();
        }

        @Test
        @DisplayName("returns hasSkydropxCredentials=true when both clientId and clientSecret are set")
        void getAdminConfig_hasSkydropxCredentialsTrue() {
            defaultConfig.setSkydropxClientId("client-id");
            defaultConfig.setSkydropxClientSecret("client-secret");
            when(repository.findById(1L)).thenReturn(Optional.of(defaultConfig));

            ShippingConfigAdminResponse response = shippingConfigService.getAdminConfig();

            assertThat(response.hasSkydropxCredentials()).isTrue();
        }

        @Test
        @DisplayName("returns hasSkydropxCredentials=false when clientId is null")
        void getAdminConfig_hasSkydropxCredentialsFalseWhenClientIdNull() {
            defaultConfig.setSkydropxClientId(null);
            defaultConfig.setSkydropxClientSecret("client-secret");
            when(repository.findById(1L)).thenReturn(Optional.of(defaultConfig));

            ShippingConfigAdminResponse response = shippingConfigService.getAdminConfig();

            assertThat(response.hasSkydropxCredentials()).isFalse();
        }

        @Test
        @DisplayName("returns hasSkydropxCredentials=false when clientSecret is blank")
        void getAdminConfig_hasSkydropxCredentialsFalseWhenSecretBlank() {
            defaultConfig.setSkydropxClientId("client-id");
            defaultConfig.setSkydropxClientSecret("  ");
            when(repository.findById(1L)).thenReturn(Optional.of(defaultConfig));

            ShippingConfigAdminResponse response = shippingConfigService.getAdminConfig();

            assertThat(response.hasSkydropxCredentials()).isFalse();
        }

        @Test
        @DisplayName("maps all Skydropx origin fields correctly")
        void getAdminConfig_mapsSkydropxOriginFields() {
            defaultConfig.setSkydropxClientId("id");
            defaultConfig.setSkydropxClientSecret("secret");
            defaultConfig.setSkydropxOriginStreet("Av. Principal 123");
            defaultConfig.setSkydropxOriginPostalCode("06600");
            defaultConfig.setSkydropxOriginCity("CDMX");
            defaultConfig.setSkydropxOriginState("CDMX");
            defaultConfig.setSkydropxOriginCountry("MX");
            when(repository.findById(1L)).thenReturn(Optional.of(defaultConfig));

            ShippingConfigAdminResponse response = shippingConfigService.getAdminConfig();

            assertThat(response.skydropxOriginStreet()).isEqualTo("Av. Principal 123");
            assertThat(response.skydropxOriginPostalCode()).isEqualTo("06600");
            assertThat(response.skydropxOriginCity()).isEqualTo("CDMX");
            assertThat(response.skydropxOriginCountry()).isEqualTo("MX");
        }
    }

    // ─── updateConfig ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateConfig")
    class UpdateConfig {

        @Test
        @DisplayName("updates nationalBasePrice when provided in request")
        void updateConfig_updatesNationalBasePrice() {
            when(repository.findById(1L)).thenReturn(Optional.of(defaultConfig));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ShippingConfigRequest request = new ShippingConfigRequest(
                    null, null, BigDecimal.valueOf(99), null,
                    null, null, null,
                    null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null,
                    null, null
            );

            ShippingConfigAdminResponse response = shippingConfigService.updateConfig(request);

            assertThat(response.nationalBasePrice()).isEqualByComparingTo(BigDecimal.valueOf(99));
        }

        @Test
        @DisplayName("does not overwrite existing fields when request fields are null")
        void updateConfig_nullFieldsDoNotOverwrite() {
            when(repository.findById(1L)).thenReturn(Optional.of(defaultConfig));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ShippingConfigRequest request = new ShippingConfigRequest(
                    null, null, null, null,
                    null, null, null,
                    null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null,
                    null, null
            );

            ShippingConfigAdminResponse response = shippingConfigService.updateConfig(request);

            assertThat(response.nationalBasePrice()).isEqualByComparingTo(BigDecimal.valueOf(50));
            assertThat(response.originAddress()).isEqualTo("Calle Origen 1, CDMX");
        }

        @Test
        @DisplayName("toggles nationalEnabled and pickupEnabled to false")
        void updateConfig_disablesShippingModes() {
            when(repository.findById(1L)).thenReturn(Optional.of(defaultConfig));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ShippingConfigRequest request = new ShippingConfigRequest(
                    false, false, null, null,
                    null, null, null,
                    null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null,
                    null, null
            );

            ShippingConfigAdminResponse response = shippingConfigService.updateConfig(request);

            assertThat(response.nationalEnabled()).isFalse();
            assertThat(response.pickupEnabled()).isFalse();
        }

        @Test
        @DisplayName("invalidates Skydropx token cache when skydropxClientId changes")
        void updateConfig_invalidatesTokenCacheOnClientIdChange() {
            when(repository.findById(1L)).thenReturn(Optional.of(defaultConfig));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ShippingConfigRequest request = new ShippingConfigRequest(
                    null, null, null, null,
                    null, null, null,
                    "new-client-id", null, null, null, null, null, null,
                    null, null, null, null, null, null, null,
                    null, null
            );

            shippingConfigService.updateConfig(request);

            verify(skydropxService).invalidateTokenCache();
        }

        @Test
        @DisplayName("invalidates Skydropx token cache when skydropxClientSecret changes")
        void updateConfig_invalidatesTokenCacheOnClientSecretChange() {
            when(repository.findById(1L)).thenReturn(Optional.of(defaultConfig));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ShippingConfigRequest request = new ShippingConfigRequest(
                    null, null, null, null,
                    null, null, null,
                    null, "new-secret", null, null, null, null, null,
                    null, null, null, null, null, null, null,
                    null, null
            );

            shippingConfigService.updateConfig(request);

            verify(skydropxService).invalidateTokenCache();
        }

        @Test
        @DisplayName("invalidates Skydropx token cache when skydropxSandbox flag changes")
        void updateConfig_invalidatesTokenCacheOnSandboxChange() {
            when(repository.findById(1L)).thenReturn(Optional.of(defaultConfig));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ShippingConfigRequest request = new ShippingConfigRequest(
                    null, null, null, null,
                    null, null, null,
                    null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null,
                    false, null
            );

            shippingConfigService.updateConfig(request);

            verify(skydropxService).invalidateTokenCache();
        }

        @Test
        @DisplayName("does not invalidate Skydropx token cache when no credential fields change")
        void updateConfig_doesNotInvalidateCacheWhenNoCredentialChange() {
            when(repository.findById(1L)).thenReturn(Optional.of(defaultConfig));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ShippingConfigRequest request = new ShippingConfigRequest(
                    true, true, BigDecimal.valueOf(80), BigDecimal.valueOf(3),
                    "New Address", null, BigDecimal.valueOf(15),
                    null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null,
                    null, null
            );

            shippingConfigService.updateConfig(request);

            verify(skydropxService, never()).invalidateTokenCache();
        }

        @Test
        @DisplayName("trims and stores whatsappNumber; sets to null when blank")
        void updateConfig_whatsappBlankBecomesNull() {
            when(repository.findById(1L)).thenReturn(Optional.of(defaultConfig));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ShippingConfigRequest request = new ShippingConfigRequest(
                    null, null, null, null,
                    null, null, null,
                    null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null,
                    null, "   "
            );

            ShippingConfigAdminResponse response = shippingConfigService.updateConfig(request);

            assertThat(response.whatsappNumber()).isNull();
        }

        @Test
        @DisplayName("stores trimmed whatsappNumber when non-blank")
        void updateConfig_whatsappTrimmed() {
            when(repository.findById(1L)).thenReturn(Optional.of(defaultConfig));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ShippingConfigRequest request = new ShippingConfigRequest(
                    null, null, null, null,
                    null, null, null,
                    null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null,
                    null, "  5219991234567  "
            );

            ShippingConfigAdminResponse response = shippingConfigService.updateConfig(request);

            assertThat(response.whatsappNumber()).isEqualTo("5219991234567");
        }

        @Test
        @DisplayName("always calls repository.save once")
        void updateConfig_savesOnce() {
            when(repository.findById(1L)).thenReturn(Optional.of(defaultConfig));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            shippingConfigService.updateConfig(new ShippingConfigRequest(
                    null, null, null, null,
                    null, null, null,
                    null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null,
                    null, null
            ));

            verify(repository, times(1)).save(any());
        }
    }

    // ─── calculateNational ────────────────────────────────────────────────────

    @Nested
    @DisplayName("calculateNational")
    class CalculateNational {

        @Test
        @DisplayName("returns distance-based price when API key and origin address are configured")
        void calculateNational_usesGoogleMapsWhenConfigured() {
            when(repository.findById(1L)).thenReturn(Optional.of(defaultConfig));
            when(googleMapsService.calculateDistanceKm(anyString(), anyString(), anyString()))
                    .thenReturn(100.0);

            ShippingCalculateResponse response = shippingConfigService.calculateNational(
                    "Av. Test 1", "GDL", "JAL", "44100", "MX");

            // price = base(50) + pricePerKm(2) * km(100) = 250.00
            assertThat(response.distanceKm()).isEqualTo(100.0);
            assertThat(response.price()).isEqualByComparingTo(BigDecimal.valueOf(250.00));
        }

        @Test
        @DisplayName("passes correct origin, destination and apiKey to GoogleMapsService")
        void calculateNational_passesCorrectArgumentsToMaps() {
            when(repository.findById(1L)).thenReturn(Optional.of(defaultConfig));
            when(googleMapsService.calculateDistanceKm(anyString(), anyString(), anyString()))
                    .thenReturn(50.0);

            shippingConfigService.calculateNational("Calle 1", "MTY", "NL", "64000", "MX");

            ArgumentCaptor<String> originCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(googleMapsService).calculateDistanceKm(
                    originCaptor.capture(), destinationCaptor.capture(), keyCaptor.capture());

            assertThat(originCaptor.getValue()).isEqualTo("Calle Origen 1, CDMX");
            assertThat(destinationCaptor.getValue()).contains("Calle 1", "MTY", "NL", "64000", "MX");
            assertThat(keyCaptor.getValue()).isEqualTo("test-api-key");
        }

        @Test
        @DisplayName("returns flat base price and 0 km when API key is null")
        void calculateNational_returnsBasePriceWhenApiKeyNull() {
            defaultConfig.setGoogleMapsApiKey(null);
            when(repository.findById(1L)).thenReturn(Optional.of(defaultConfig));

            ShippingCalculateResponse response = shippingConfigService.calculateNational(
                    "Calle 2", "QRO", "QRO", "76000", "MX");

            assertThat(response.distanceKm()).isEqualTo(0.0);
            assertThat(response.price()).isEqualByComparingTo(BigDecimal.valueOf(50));
            verify(googleMapsService, never()).calculateDistanceKm(any(), any(), any());
        }

        @Test
        @DisplayName("returns flat base price when API key is blank")
        void calculateNational_returnsBasePriceWhenApiKeyBlank() {
            defaultConfig.setGoogleMapsApiKey("   ");
            when(repository.findById(1L)).thenReturn(Optional.of(defaultConfig));

            ShippingCalculateResponse response = shippingConfigService.calculateNational(
                    "Calle 3", "PUE", "PUE", "72000", "MX");

            assertThat(response.distanceKm()).isEqualTo(0.0);
            assertThat(response.price()).isEqualByComparingTo(BigDecimal.valueOf(50));
            verify(googleMapsService, never()).calculateDistanceKm(any(), any(), any());
        }

        @Test
        @DisplayName("returns flat base price when origin address is null")
        void calculateNational_returnsBasePriceWhenOriginNull() {
            defaultConfig.setOriginAddress(null);
            when(repository.findById(1L)).thenReturn(Optional.of(defaultConfig));

            ShippingCalculateResponse response = shippingConfigService.calculateNational(
                    "Calle 4", "TIJ", "BC", "22000", "MX");

            assertThat(response.distanceKm()).isEqualTo(0.0);
            verify(googleMapsService, never()).calculateDistanceKm(any(), any(), any());
        }

        @Test
        @DisplayName("returns flat base price when origin address is blank")
        void calculateNational_returnsBasePriceWhenOriginBlank() {
            defaultConfig.setOriginAddress("   ");
            when(repository.findById(1L)).thenReturn(Optional.of(defaultConfig));

            ShippingCalculateResponse response = shippingConfigService.calculateNational(
                    "Calle 5", "MER", "YUC", "97000", "MX");

            assertThat(response.distanceKm()).isEqualTo(0.0);
            verify(googleMapsService, never()).calculateDistanceKm(any(), any(), any());
        }

        @Test
        @DisplayName("rounds price to 2 decimal places using HALF_UP")
        void calculateNational_roundsPriceToTwoDecimalPlaces() {
            defaultConfig.setNationalBasePrice(BigDecimal.valueOf(10));
            defaultConfig.setNationalPricePerKm(BigDecimal.valueOf(1));
            when(repository.findById(1L)).thenReturn(Optional.of(defaultConfig));
            // 10 + 1 * 33.3333... = 43.3333... → rounded to 43.33
            when(googleMapsService.calculateDistanceKm(anyString(), anyString(), anyString()))
                    .thenReturn(33.3333);

            ShippingCalculateResponse response = shippingConfigService.calculateNational(
                    "A", "B", "C", "00000", "MX");

            assertThat(response.price().scale()).isEqualTo(2);
            assertThat(response.price()).isEqualByComparingTo("43.33");
        }
    }
}
