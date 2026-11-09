package com.ecommerce.repository;

import com.ecommerce.entity.PageView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PageViewRepository extends JpaRepository<PageView, Long> {

    long countByTimestampBetween(LocalDateTime from, LocalDateTime to);

    /** One bulk DELETE (no entity loading); returns how many rows went. */
    @Modifying
    @Query("DELETE FROM PageView p WHERE p.timestamp < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);

    /** Visits: distinct browsing sessions (a session lasts while the shopper's tab is open). */
    @Query("SELECT COUNT(DISTINCT p.sessionId) FROM PageView p WHERE p.timestamp BETWEEN :from AND :to")
    long countVisitsBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(DISTINCT p.sessionId) FROM PageView p")
    long countVisits();

    // Each grouping returns label, page views, visits (distinct sessions)

    @Query(value = "SELECT CAST(EXTRACT(HOUR FROM timestamp) AS int) AS label, COUNT(*) AS views, COUNT(DISTINCT session_id) AS visits " +
                   "FROM page_views WHERE timestamp BETWEEN :from AND :to " +
                   "GROUP BY label ORDER BY label", nativeQuery = true)
    List<Object[]> countByHour(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = "SELECT TO_CHAR(timestamp, 'YYYY-MM-DD') AS label, COUNT(*) AS views, COUNT(DISTINCT session_id) AS visits " +
                   "FROM page_views WHERE timestamp BETWEEN :from AND :to " +
                   "GROUP BY label ORDER BY label", nativeQuery = true)
    List<Object[]> countByDay(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = "SELECT TO_CHAR(timestamp, 'IYYY-IW') AS label, COUNT(*) AS views, COUNT(DISTINCT session_id) AS visits " +
                   "FROM page_views WHERE timestamp BETWEEN :from AND :to " +
                   "GROUP BY label ORDER BY label", nativeQuery = true)
    List<Object[]> countByWeek(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = "SELECT TO_CHAR(timestamp, 'YYYY-MM') AS label, COUNT(*) AS views, COUNT(DISTINCT session_id) AS visits " +
                   "FROM page_views WHERE timestamp BETWEEN :from AND :to " +
                   "GROUP BY label ORDER BY label", nativeQuery = true)
    List<Object[]> countByMonth(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = "SELECT CAST(CAST(EXTRACT(YEAR FROM timestamp) AS int) AS text) AS label, COUNT(*) AS views, COUNT(DISTINCT session_id) AS visits " +
                   "FROM page_views WHERE timestamp BETWEEN :from AND :to " +
                   "GROUP BY label ORDER BY label", nativeQuery = true)
    List<Object[]> countByYear(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // Top pages. Rows recorded before paths were normalized may still carry a query string.
    @Query(value = "SELECT CASE WHEN POSITION('?' IN path) > 0 THEN SUBSTRING(path, 1, POSITION('?' IN path) - 1) ELSE path END AS label, " +
                   "COUNT(*) AS views, COUNT(DISTINCT session_id) AS visits " +
                   "FROM page_views WHERE timestamp BETWEEN :from AND :to " +
                   "GROUP BY label ORDER BY views DESC LIMIT 20", nativeQuery = true)
    List<Object[]> countByPage(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // Where visits come from. Only a visit's first page carries the source, so each session takes
    // its own source first; a session without one is direct (empty label).
    @Query(value = "SELECT COALESCE(src, '') AS label, COUNT(*) AS visits FROM (" +
                   "  SELECT session_id, MAX(source) AS src FROM page_views " +
                   "  WHERE timestamp BETWEEN :from AND :to AND session_id IS NOT NULL GROUP BY session_id" +
                   ") v GROUP BY label ORDER BY visits DESC LIMIT 15", nativeQuery = true)
    List<Object[]> countVisitsBySource(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
