package com.ecommerce.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * Rows that belong to one store. Hibernate fills tenant_id on insert and adds
 * "tenant_id = current store" to every query on these entities (see com.ecommerce.tenant.TenantContext).
 * Native SQL queries are not covered and must filter by tenant_id themselves.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class TenantScoped {

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;
}
