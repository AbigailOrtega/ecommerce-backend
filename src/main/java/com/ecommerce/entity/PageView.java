package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "page_views", indexes = {
    @Index(name = "idx_page_views_timestamp", columnList = "timestamp"),
    @Index(name = "idx_page_views_path", columnList = "path")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageView extends TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String path;

    @Column(length = 64)
    private String sessionId;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    /** utm_source or referring site of the visit, on its first page view; null = direct. */
    @Column(length = 50)
    private String source;
}
