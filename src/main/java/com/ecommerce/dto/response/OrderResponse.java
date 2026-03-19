package com.ecommerce.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderResponse {
    private Long id;
    private String orderNumber;
    private UserResponse user;
    private List<OrderItemResponse> items;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private String couponCode;
    private BigDecimal shippingCost;
    private String shippingMethodName;
    private String status;
    private String paymentMethod;
    private String paymentId;
    private String shippingAddress;
    private String shippingCity;
    private String shippingState;
    private String shippingZipCode;
    private String shippingCountry;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String shippingType;
    private String pickupLocationName;
    private String pickupTimeSlotLabel;
    private String skydropxRateId;
    private String skydropxShipmentId;
    private String trackingNumber;
    private String carrierName;
    private String labelUrl;
    private String shipmentStatus;
    private String guestEmail;
    private String guestFirstName;
    private String guestLastName;
    private String guestPhone;
}
