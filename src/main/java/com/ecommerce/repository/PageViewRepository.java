package com.ecommerce.repository;

import com.ecommerce.entity.PageView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PageViewRepository extends JpaRepository<PageView, Long> {

    long countByTimestampBetween(LocalDateTime from, LocalDateTime to);

    // Visits grouped by hour (0-23)
    @Query(value = "SELECT EXTRACT(HOUR FROM timestamp)::int AS label, COUNT(*) AS count " +
                   "FROM page_views WHERE timestamp BETWEEN :from AND :to " +
                   "GROUP BY label ORDER BY label", nativeQuery = true)
    List<Object[]> countByHour(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // Visits grouped by day
    @Query(value = "SELECT TO_CHAR(timestamp, 'YYYY-MM-DD') AS label, COUNT(*) AS count " +
                   "FROM page_views WHERE timestamp BETWEEN :from AND :to " +
                   "GROUP BY label ORDER BY label", nativeQuery = true)
    List<Object[]> countByDay(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // Visits grouped by week (YYYY-WW)
    @Query(value = "SELECT TO_CHAR(timestamp, 'IYYY-IW') AS label, COUNT(*) AS count " +
                   "FROM page_views WHERE timestamp BETWEEN :from AND :to " +
                   "GROUP BY label ORDER BY label", nativeQuery = true)
    List<Object[]> countByWeek(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // Visits grouped by month
    @Query(value = "SELECT TO_CHAR(timestamp, 'YYYY-MM') AS label, COUNT(*) AS count " +
                   "FROM page_views WHERE timestamp BETWEEN :from AND :to " +
                   "GROUP BY label ORDER BY label", nativeQuery = true)
    List<Object[]> countByMonth(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // Visits grouped by year
    @Query(value = "SELECT EXTRACT(YEAR FROM timestamp)::int::text AS label, COUNT(*) AS count " +
                   "FROM page_views WHERE timestamp BETWEEN :from AND :to " +
                   "GROUP BY label ORDER BY label", nativeQuery = true)
    List<Object[]> countByYear(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // Top pages
    @Query(value = "SELECT path AS label, COUNT(*) AS count " +
                   "FROM page_views WHERE timestamp BETWEEN :from AND :to " +
                   "GROUP BY path ORDER BY count DESC LIMIT 20", nativeQuery = true)
    List<Object[]> countByPage(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
