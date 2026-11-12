package com.ecommerce.tenant;

import jakarta.persistence.*;
import lombok.*;

/** A host name that serves a store, e.g. "mareatextiles.com". Stored lower-case, without "www." or port. */
@Entity
@Table(name = "tenant_domains")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantDomain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String domain;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
}
