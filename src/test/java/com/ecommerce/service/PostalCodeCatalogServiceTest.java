package com.ecommerce.service;

import com.ecommerce.dto.response.PostalCodeResponse;
import com.ecommerce.entity.PostalCode;
import com.ecommerce.repository.PostalCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("PostalCodeCatalogService")
class PostalCodeCatalogServiceTest {

    private final PostalCodeRepository repository = mock(PostalCodeRepository.class);
    private final SepomexClient client = mock(SepomexClient.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private PostalCodeCatalogService service;

    private static PostalCode row(String code, String settlement, String municipality, String city) {
        return PostalCode.builder().code(code).settlement(settlement).settlementType("Colonia")
                .municipality(municipality).state("Ciudad de México").city(city).build();
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        doAnswer(inv -> { ((Consumer<Object>) inv.getArgument(0)).accept(null); return null; })
                .when(tx).executeWithoutResult(any());
        service = new PostalCodeCatalogService(repository, client, jdbc, tx, true);
    }

    @Test
    @DisplayName("lookup: state, municipality, city and every colonia of the code")
    void lookup() {
        when(repository.findByCodeOrderBySettlementAsc("06700"))
                .thenReturn(List.of(row("06700", "Roma Norte", "Cuauhtémoc", "Ciudad de México")));

        PostalCodeResponse found = service.lookup("06700").orElseThrow();

        assertThat(found.state()).isEqualTo("Ciudad de México");
        assertThat(found.municipality()).isEqualTo("Cuauhtémoc");
        assertThat(found.city()).isEqualTo("Ciudad de México");
        assertThat(found.settlements()).extracting(PostalCodeResponse.Settlement::name).containsExactly("Roma Norte");
    }

    @Test
    @DisplayName("lookup: without a city in the catalog, the municipality stands in")
    void cityFallsBackToMunicipality() {
        when(repository.findByCodeOrderBySettlementAsc("56530"))
                .thenReturn(List.of(row("56530", "San Mateo Huitzilzingo", "Chalco", null)));
        assertThat(service.lookup("56530").orElseThrow().city()).isEqualTo("Chalco");
    }

    @Test
    @DisplayName("lookup: unknown or malformed codes find nothing, without querying junk")
    void notFound() {
        assertThat(service.lookup("99999")).isEmpty();
        assertThat(service.lookup("6700")).isEmpty();
        assertThat(service.lookup("abcde")).isEmpty();
        verify(repository, never()).findByCodeOrderBySettlementAsc("abcde");
    }

    @Test
    @DisplayName("refresh: replaces the table with the download, in batches")
    @SuppressWarnings("unchecked")
    void refreshReplaces() throws Exception {
        List<PostalCode> download = new ArrayList<>();
        IntStream.range(0, PostalCodeCatalogService.MIN_ROWS + 1)
                .forEach(i -> download.add(row(String.format("%05d", i % 100_000), "C" + i, "M", null)));
        when(client.download()).thenReturn(download);

        service.refresh();

        verify(jdbc).update("DELETE FROM postal_codes");
        verify(jdbc, times(51)).batchUpdate(startsWith("INSERT INTO postal_codes"), anyList(), anyInt(),
                any(ParameterizedPreparedStatementSetter.class));
        assertThat(service.status().lastRefreshAt()).isNotNull();
        assertThat(service.status().lastRefreshError()).isNull();
    }

    @Test
    @DisplayName("refresh: a short download (broken file) keeps the current catalog")
    void shortDownloadKeepsCatalog() throws Exception {
        when(client.download()).thenReturn(List.of(row("06700", "Roma Norte", "Cuauhtémoc", null)));

        service.refresh();

        verify(jdbc, never()).update(anyString());
        assertThat(service.status().lastRefreshError()).contains("only 1 settlements");
    }

    @Test
    @DisplayName("refresh: a failed download keeps the current catalog and records why")
    void failedDownloadKeepsCatalog() throws Exception {
        when(client.download()).thenThrow(new IOException("Correos de México export page answered 503"));

        service.refresh();

        verify(jdbc, never()).update(anyString());
        assertThat(service.status().lastRefreshError()).contains("503");
        assertThat(service.status().refreshing()).isFalse();
    }

    @Test
    @DisplayName("at startup: downloads only when the table is empty and auto-download is on")
    void startup() throws Exception {
        when(repository.count()).thenReturn(159_493L);
        service.loadIfEmpty();

        PostalCodeCatalogService off = new PostalCodeCatalogService(repository, client, jdbc, tx, false);
        when(repository.count()).thenReturn(0L);
        off.loadIfEmpty();

        verify(client, never()).download();
    }
}
