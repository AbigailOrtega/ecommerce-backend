package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pickup_locations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PickupLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String address;
    private String city;
    private String state;

    @Builder.Default
    private boolean active = true;

    @OneToMany(mappedBy = "location", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    private List<PickupTimeSlot> timeSlots = new ArrayList<>();
}
