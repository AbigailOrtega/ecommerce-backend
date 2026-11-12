package com.ecommerce.entity;

/**
 * CUSTOMER buys; ADMIN runs their own store; SUPER_ADMIN runs the platform (every store) and is also
 * an admin of store #1. Super admins exist only in store #1 and are set by hand in the database.
 */
public enum Role {
    CUSTOMER,
    ADMIN,
    SUPER_ADMIN;

    /** Whether this role runs its store (an admin or a super admin). */
    public boolean isStoreAdmin() {
        return this == ADMIN || this == SUPER_ADMIN;
    }
}
