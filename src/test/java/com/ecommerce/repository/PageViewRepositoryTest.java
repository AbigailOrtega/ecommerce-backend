package com.ecommerce.repository;

import com.ecommerce.entity.PageView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The reporting queries, on H2 (dev and tests); production runs them on PostgreSQL. */
@DataJpaTest
@DisplayName("PageViewRepository")
class PageViewRepositoryTest {

    @Autowired PageViewRepository repository;

    private static final LocalDateTime DAY = LocalDateTime.of(2026, 11, 8, 0, 0);
    private static final LocalDateTime FROM = DAY;
    private static final LocalDateTime TO = DAY.plusDays(1);

    private void view(String path, String session, String source, int hour) {
        repository.save(PageView.builder().path(path).sessionId(session).source(source).timestamp(DAY.plusHours(hour)).build());
    }

    @BeforeEach
    void seed() {
        // visit A: arrived from instagram, saw 3 pages
        view("/", "A", "instagram.com", 10);
        view("/products/cojin", "A", null, 10);
        view("/products/cojin", "A", null, 11);
        // visit B: direct, 1 page; an old row that still has its query string
        view("/products/cojin?utm_source=x", "B", null, 11);
    }

    @Test
    @DisplayName("page views count rows, visits count distinct sessions")
    void viewsAndVisits() {
        assertThat(repository.countByTimestampBetween(FROM, TO)).isEqualTo(4);
        assertThat(repository.countVisitsBetween(FROM, TO)).isEqualTo(2);
        assertThat(repository.countVisits()).isEqualTo(2);
    }

    @Test
    @DisplayName("by day: label, views, visits")
    void byDay() {
        List<Object[]> rows = repository.countByDay(FROM, TO);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)[0].toString()).isEqualTo("2026-11-08");
        assertThat(((Number) rows.get(0)[1]).longValue()).isEqualTo(4);
        assertThat(((Number) rows.get(0)[2]).longValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("by page: query strings of old rows are folded into the page")
    void byPage() {
        List<Object[]> rows = repository.countByPage(FROM, TO);
        assertThat(rows.get(0)[0].toString()).isEqualTo("/products/cojin");
        assertThat(((Number) rows.get(0)[1]).longValue()).isEqualTo(3);
        assertThat(((Number) rows.get(0)[2]).longValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("by source: each visit counted once, under the source of its first page; direct as an empty label")
    void bySource() {
        List<Object[]> rows = repository.countVisitsBySource(FROM, TO);
        assertThat(rows).extracting(r -> r[0].toString() + "=" + ((Number) r[1]).longValue())
                .containsExactlyInAnyOrder("instagram.com=1", "=1");
    }
}
