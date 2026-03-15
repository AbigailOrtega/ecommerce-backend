package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "pickup_time_slots")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PickupTimeSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    @ToString.Exclude
    private PickupLocation location;

    private String label;

    @Builder.Default
    private boolean active = true;
}
