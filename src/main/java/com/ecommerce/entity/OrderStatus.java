package com.ecommerce.entity;

public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REFUNDED,
    SHIPMENT_PENDING,
    DELIVERY_ATTEMPT,
    DELIVERY_EXCEPTION
}
