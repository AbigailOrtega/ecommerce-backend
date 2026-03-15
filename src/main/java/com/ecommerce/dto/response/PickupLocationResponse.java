package com.ecommerce.dto.response;

import java.util.List;

public record PickupLocationResponse(
    Long id,
    String name,
    String address,
    String city,
    String state,
    boolean active,
    List<PickupTimeSlotResponse> timeSlots
) {}
