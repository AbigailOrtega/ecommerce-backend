package com.ecommerce.service;

import com.ecommerce.dto.response.PostalCodeResponse;
import com.ecommerce.entity.PostalCode;
import com.ecommerce.repository.PostalCodeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mexico's postal code catalog, so the checkout can fill in state, municipality and colonia from
 * the postal code. The app downloads it from Correos de México itself (when the table is empty at
 * startup, then monthly) because its terms forbid redistributing it, so it is never in the repo.
 * A failed download keeps the catalog already loaded.
 */
@Slf4j
@Service
public class PostalCodeCatalogService {

    /** A real download has ~160k settlements; far fewer means a broken or truncated file. */
    static final int MIN_ROWS = 100_000;
    private static final int BATCH = 2_000;

    private final PostalCodeRepository repository;
    private final SepomexClient client;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final boolean autoDownload;

    private final AtomicBoolean refreshing = new AtomicBoolean();
    private volatile LocalDateTime lastRefreshAt;
    private volatile String lastRefreshError;

    public PostalCodeCatalogService(PostalCodeRepository repository, SepomexClient client, JdbcTemplate jdbc,
                                    TransactionTemplate tx,
                                    @Value("${app.postal-codes.auto-download:true}") boolean autoDownload) {
        this.repository = repository;
        this.client = client;
        this.jdbc = jdbc;
        this.tx = tx;
        this.autoDownload = autoDownload;
    }

    public Optional<PostalCodeResponse> lookup(String code) {
        if (code == null || !code.matches("\\d{5}")) return Optional.empty();
        List<PostalCode> rows = repository.findByCodeOrderBySettlementAsc(code);
        if (rows.isEmpty()) return Optional.empty();
        PostalCode first = rows.get(0);
        return Optional.of(new PostalCodeResponse(
                code,
                first.getState(),
                first.getMunicipality(),
                first.getCity() != null ? first.getCity() : first.getMunicipality(),
                rows.stream().map(r -> new PostalCodeResponse.Settlement(r.getSettlement(), r.getSettlementType())).toList()));
    }

    public record Status(long rows, boolean refreshing, LocalDateTime lastRefreshAt, String lastRefreshError) {}

    public Status status() {
        return new Status(repository.count(), refreshing.get(), lastRefreshAt, lastRefreshError);
    }

    /** First start (or a wiped table): load the catalog in the background, not holding up startup. */
    @EventListener(ApplicationReadyEvent.class)
    public void loadIfEmpty() {
        if (!autoDownload || repository.count() > 0) return;
        startRefresh();
    }

    @Scheduled(cron = "${app.postal-codes.refresh-cron:0 0 4 1 * *}", zone = "America/Mexico_City")
    public void monthlyRefresh() {
        if (autoDownload) refresh();
    }

    /** Starts a refresh in the background; false when one is already running. */
    public boolean startRefresh() {
        if (refreshing.get()) return false;
        Thread.ofVirtual().name("postal-code-catalog").start(this::refresh);
        return true;
    }

    /** Downloads the catalog and replaces the table with it, in one transaction. */
    public void refresh() {
        if (!refreshing.compareAndSet(false, true)) return;
        try {
            List<PostalCode> rows = client.download();
            if (rows.size() < MIN_ROWS) {
                throw new IllegalStateException("only " + rows.size() + " settlements in the download");
            }
            tx.executeWithoutResult(status -> replaceAll(rows));
            lastRefreshAt = LocalDateTime.now();
            lastRefreshError = null;
            log.info("Postal code catalog loaded: {} settlements", rows.size());
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            lastRefreshError = e.getMessage();
            log.error("Postal code catalog refresh failed, keeping the current one: {}", e.getMessage());
        } finally {
            refreshing.set(false);
        }
    }

    private void replaceAll(List<PostalCode> rows) {
        jdbc.update("DELETE FROM postal_codes");
        for (int from = 0; from < rows.size(); from += BATCH) {
            List<PostalCode> chunk = rows.subList(from, Math.min(from + BATCH, rows.size()));
            jdbc.batchUpdate(
                    "INSERT INTO postal_codes (code, settlement, settlement_type, municipality, state, city) VALUES (?, ?, ?, ?, ?, ?)",
                    chunk, chunk.size(), (ps, r) -> {
                        ps.setString(1, r.getCode());
                        ps.setString(2, r.getSettlement());
                        ps.setString(3, r.getSettlementType());
                        ps.setString(4, r.getMunicipality());
                        ps.setString(5, r.getState());
                        ps.setString(6, r.getCity());
                    });
        }
    }
}
