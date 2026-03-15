package com.ecommerce.service;

import com.ecommerce.dto.request.PickupLocationRequest;
import com.ecommerce.dto.request.PickupTimeSlotRequest;
import com.ecommerce.dto.response.PickupLocationResponse;
import com.ecommerce.dto.response.PickupTimeSlotResponse;
import com.ecommerce.entity.PickupLocation;
import com.ecommerce.entity.PickupTimeSlot;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.PickupLocationRepository;
import com.ecommerce.repository.PickupTimeSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PickupLocationService {

    private final PickupLocationRepository locationRepository;
    private final PickupTimeSlotRepository timeSlotRepository;

    // ─── Public ──────────────────────────────────────────────────────────────

    public List<PickupLocationResponse> getActiveLocations() {
        return locationRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(loc -> mapToResponse(loc, true))
                .toList();
    }

    // ─── Admin ────────────────────────────────────────────────────────────────

    public List<PickupLocationResponse> getAllLocations() {
        return locationRepository.findAll().stream()
                .map(loc -> mapToResponse(loc, false))
                .toList();
    }

    @Transactional
    public PickupLocationResponse create(PickupLocationRequest request) {
        PickupLocation loc = PickupLocation.builder()
                .name(request.name())
                .address(request.address())
                .city(request.city())
                .state(request.state())
                .build();
        return mapToResponse(locationRepository.save(loc), false);
    }

    @Transactional
    public PickupLocationResponse update(Long id, PickupLocationRequest request) {
        PickupLocation loc = findById(id);
        loc.setName(request.name());
        loc.setAddress(request.address());
        loc.setCity(request.city());
        loc.setState(request.state());
        return mapToResponse(locationRepository.save(loc), false);
    }

    @Transactional
    public void delete(Long id) {
        PickupLocation loc = findById(id);
        locationRepository.delete(loc);
    }

    @Transactional
    public PickupLocationResponse toggle(Long id) {
        PickupLocation loc = findById(id);
        loc.setActive(!loc.isActive());
        return mapToResponse(locationRepository.save(loc), false);
    }

    // ─── Time slots ──────────────────────────────────────────────────────────

    @Transactional
    public PickupTimeSlotResponse addTimeSlot(Long locationId, PickupTimeSlotRequest request) {
        PickupLocation loc = findById(locationId);
        PickupTimeSlot slot = PickupTimeSlot.builder()
                .location(loc)
                .label(request.label())
                .build();
        return mapSlotToResponse(timeSlotRepository.save(slot));
    }

    @Transactional
    public PickupTimeSlotResponse updateTimeSlot(Long slotId, PickupTimeSlotRequest request) {
        PickupTimeSlot slot = findSlotById(slotId);
        slot.setLabel(request.label());
        return mapSlotToResponse(timeSlotRepository.save(slot));
    }

    @Transactional
    public void deleteTimeSlot(Long slotId) {
        PickupTimeSlot slot = findSlotById(slotId);
        timeSlotRepository.delete(slot);
    }

    @Transactional
    public PickupTimeSlotResponse toggleTimeSlot(Long slotId) {
        PickupTimeSlot slot = findSlotById(slotId);
        slot.setActive(!slot.isActive());
        return mapSlotToResponse(timeSlotRepository.save(slot));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    public PickupLocation findById(Long id) {
        return locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PickupLocation", "id", id));
    }

    public PickupLocation findActiveById(Long id) {
        PickupLocation loc = findById(id);
        if (!loc.isActive()) {
            throw new BadRequestException("Pickup location is not active.");
        }
        return loc;
    }

    private PickupTimeSlot findSlotById(Long id) {
        return timeSlotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PickupTimeSlot", "id", id));
    }

    private PickupLocationResponse mapToResponse(PickupLocation loc, boolean activeOnly) {
        List<PickupTimeSlotResponse> slots = loc.getTimeSlots().stream()
                .filter(s -> !activeOnly || s.isActive())
                .map(this::mapSlotToResponse)
                .toList();
        return new PickupLocationResponse(
                loc.getId(), loc.getName(), loc.getAddress(),
                loc.getCity(), loc.getState(), loc.isActive(), slots);
    }

    private PickupTimeSlotResponse mapSlotToResponse(PickupTimeSlot slot) {
        return new PickupTimeSlotResponse(slot.getId(), slot.getLabel(), slot.isActive());
    }
}
