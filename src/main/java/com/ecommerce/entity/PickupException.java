package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pickup_exceptions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PickupException {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "availability_id", nullable = false)
    private PickupAvailability availability;

    @Column(nullable = false)
    private LocalDate date;

    private String reason;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
