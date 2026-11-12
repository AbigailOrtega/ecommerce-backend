package com.ecommerce.service;

import com.ecommerce.tenant.TenantContext;

import com.ecommerce.repository.PageViewRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * page_views gets a row per page a visitor opens and would otherwise grow forever. Every night the
 * rows older than the retention are deleted. The default keeps two full calendar years, so the
 * analytics page can still compare this year with the last one.
 */
@Slf4j
@Component
public class PageViewCleanupJob {

    private final PageViewRepository pageViewRepository;
    private final Clock clock;

    @Value("${app.analytics.page-views.retention:P760D}")
    private Duration retention = Duration.ofDays(760);

    @Value("${app.analytics.page-views.cleanup-enabled:true}")
    private boolean enabled = true;

    @Autowired
    public PageViewCleanupJob(PageViewRepository pageViewRepository) {
        this(pageViewRepository, Clock.systemDefaultZone());
    }

    PageViewCleanupJob(PageViewRepository pageViewRepository, Clock clock) {
        this.pageViewRepository = pageViewRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.analytics.page-views.cleanup-cron:0 30 3 * * *}")
    public void scheduledRun() {
        // one retention for every store
        TenantContext.runAs(TenantContext.ROOT, this::run);
    }

    @Transactional
    public int run() {
        if (!enabled) return 0;
        // timestamps are store-time wall-clock values (see EcommerceApplication), like LocalDateTime.now(clock)
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(retention);
        int deleted = pageViewRepository.deleteOlderThan(cutoff);
        if (deleted > 0) log.info("Deleted {} page views older than {}", deleted, cutoff.toLocalDate());
        return deleted;
    }
}
