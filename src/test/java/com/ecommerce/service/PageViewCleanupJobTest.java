package com.ecommerce.service;

import com.ecommerce.entity.PageView;
import com.ecommerce.repository.PageViewRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("PageViewCleanupJob")
class PageViewCleanupJobTest {

    private static final ZoneId ZONE = ZoneId.of("America/Mexico_City");
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-11-08T18:00:00Z"), ZONE); // 12:00 in Mexico City

    @Autowired PageViewRepository repository;

    private void view(LocalDateTime when) {
        repository.save(PageView.builder().path("/").sessionId("s").timestamp(when).build());
    }

    @Test
    @DisplayName("deletes page views older than the retention and keeps the rest")
    void deletesOldRows() {
        view(LocalDateTime.of(2024, 10, 1, 9, 0));   // older than 760 days
        view(LocalDateTime.of(2024, 10, 9, 11, 0));  // just older
        view(LocalDateTime.of(2024, 10, 9, 13, 0));  // just inside
        view(LocalDateTime.of(2026, 11, 8, 11, 0));  // today

        int deleted = new PageViewCleanupJob(repository, NOW).run();

        assertThat(deleted).isEqualTo(2);
        assertThat(repository.findAll()).extracting(PageView::getTimestamp)
                .containsExactlyInAnyOrder(LocalDateTime.of(2024, 10, 9, 13, 0), LocalDateTime.of(2026, 11, 8, 11, 0));
    }

    @Test
    @DisplayName("the retention is configurable and the job can be switched off")
    void configurable() {
        view(LocalDateTime.of(2026, 10, 1, 9, 0));
        PageViewCleanupJob job = new PageViewCleanupJob(repository, NOW);

        ReflectionTestUtils.setField(job, "enabled", false);
        assertThat(job.run()).isZero();

        ReflectionTestUtils.setField(job, "enabled", true);
        ReflectionTestUtils.setField(job, "retention", Duration.ofDays(30));
        assertThat(job.run()).isEqualTo(1);
    }
}
