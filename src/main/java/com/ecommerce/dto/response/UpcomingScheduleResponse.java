package com.ecommerce.dto.response;

import java.util.List;

public record UpcomingScheduleResponse(
        List<OrderResponse> shipments,
        List<PickupGroupResponse> pickups
) {
    public record PickupGroupResponse(
            String locationName,
            List<OrderResponse> orders
    ) {}
}
