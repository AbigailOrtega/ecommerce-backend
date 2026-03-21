package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "pickup_availability")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PickupAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    @ToString.Exclude
    private PickupLocation location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AvailabilityType type;

    @Enumerated(EnumType.STRING)
    private DayOfWeek dayOfWeek;   // null for SPECIFIC_DATE

    private LocalDate specificDate; // null for RECURRING

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Builder.Default
    private int maxCapacity = 10;

    @Builder.Default
    private boolean active = true;
}
