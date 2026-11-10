package com.ecommerce.service;

import com.ecommerce.dto.response.PostalCodeResponse;
import com.ecommerce.entity.PostalCode;
import com.ecommerce.repository.PostalCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.List;
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

    private static byte[] catalogWith(int rows) throws Exception {
        return SepomexClientTest.catalogZip(IntStream.range(0, rows)
                .mapToObj(i -> String.format("%05d|Colonia %d|Colonia|Municipio|Estado||", i % 100_000, i)).toList());
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // runs the callback like the real template; an exception from it is what rolls back
        when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<Object>) inv.getArgument(0)).doInTransaction(null));
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
    @DisplayName("refresh: replaces the table while reading the file, in batches of 2,000")
    @SuppressWarnings("unchecked")
    void refreshReplaces() throws Exception {
        when(client.downloadZip()).thenReturn(catalogWith(PostalCodeCatalogService.MIN_ROWS + 1));

        service.refresh();

        verify(jdbc).update("DELETE FROM postal_codes");
        verify(jdbc, times(51)).batchUpdate(startsWith("INSERT INTO postal_codes"), anyList(), anyInt(),
                any(ParameterizedPreparedStatementSetter.class));
        assertThat(service.status().lastRefreshAt()).isNotNull();
        assertThat(service.status().lastRefreshError()).isNull();
    }

    @Test
    @DisplayName("refresh: a short download (broken file) fails inside the transaction, so it rolls back")
    void shortDownloadRollsBack() throws Exception {
        when(client.downloadZip()).thenReturn(catalogWith(10));

        service.refresh();

        assertThat(service.status().lastRefreshError()).contains("only 10 settlements");
        assertThat(service.status().lastRefreshAt()).isNull();
        assertThat(service.status().refreshing()).isFalse();
    }

    @Test
    @DisplayName("refresh: a failed download changes nothing and records why")
    void failedDownloadKeepsCatalog() throws Exception {
        when(client.downloadZip()).thenThrow(new IOException("Correos de México export page answered 503"));

        service.refresh();

        verifyNoInteractions(jdbc);
        assertThat(service.status().lastRefreshError()).contains("503");
    }

    @Test
    @DisplayName("at startup: downloads only when the table is empty and auto-download is on")
    void startup() throws Exception {
        when(repository.count()).thenReturn(159_493L);
        service.loadIfEmpty();

        PostalCodeCatalogService off = new PostalCodeCatalogService(repository, client, jdbc, tx, false);
        when(repository.count()).thenReturn(0L);
        off.loadIfEmpty();

        verify(client, never()).downloadZip();
    }
}
