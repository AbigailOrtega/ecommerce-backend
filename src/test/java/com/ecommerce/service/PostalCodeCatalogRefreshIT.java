package com.ecommerce.service;

import com.ecommerce.repository.PostalCodeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** The refresh against a real database: a broken download must leave the loaded catalog as it was. */
@SpringBootTest
@ActiveProfiles("e2e")
@DisplayName("Postal code catalog refresh — Integration")
class PostalCodeCatalogRefreshIT {

    @Autowired PostalCodeCatalogService service;
    @Autowired PostalCodeRepository repository;
    @MockitoBean SepomexClient client;

    @Test
    @DisplayName("a short download rolls back: the colonias already loaded stay")
    void shortDownloadKeepsCatalog() throws Exception {
        long before = repository.count();
        assertThat(before).isPositive();   // seeded by TestDataInitializer
        when(client.downloadZip()).thenReturn(SepomexClientTest.catalogZip(List.of(
                "01000|San Ángel|Colonia|Álvaro Obregón|Ciudad de México|Ciudad de México|01001")));

        service.refresh();

        assertThat(service.status().lastRefreshError()).contains("only 1 settlements");
        assertThat(repository.count()).isEqualTo(before);
        assertThat(service.lookup("06700")).isPresent();
    }
}
