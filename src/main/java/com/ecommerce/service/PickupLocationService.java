package com.ecommerce.service;

import com.ecommerce.dto.request.PickupAvailabilityRequest;
import com.ecommerce.dto.request.PickupExceptionRequest;
import com.ecommerce.dto.request.PickupLocationRequest;
import com.ecommerce.dto.response.PickupAvailabilityResponse;
import com.ecommerce.dto.response.PickupExceptionResponse;
import com.ecommerce.dto.response.PickupLocationResponse;
import com.ecommerce.entity.AvailabilityType;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderStatus;
import com.ecommerce.entity.PickupAvailability;
import com.ecommerce.entity.PickupException;
import com.ecommerce.entity.PickupLocation;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.PickupAvailabilityRepository;
import com.ecommerce.repository.PickupExceptionRepository;
import com.ecommerce.repository.PickupLocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class PickupLocationService {

    private final PickupLocationRepository locationRepository;
    private final PickupAvailabilityRepository availabilityRepository;
    private final PickupExceptionRepository pickupExceptionRepository;
    private final OrderRepository orderRepository;
    private final EmailService emailService;

    private static final List<OrderStatus> EXCLUDED_STATUSES =
            List.of(OrderStatus.CANCELLED, OrderStatus.REFUNDED);

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

    // ─── Availability ─────────────────────────────────────────────────────────

    public List<PickupAvailabilityResponse> getAvailabilities(Long locationId) {
        findById(locationId); // validate location exists
        return availabilityRepository.findByLocationIdAndActiveTrue(locationId).stream()
                .map(this::mapAvailabilityToResponse)
                .toList();
    }

    @Transactional
    public PickupAvailabilityResponse createAvailability(Long locationId, PickupAvailabilityRequest request) {
        PickupLocation loc = findById(locationId);
        validateAvailabilityRequest(request);
        PickupAvailability avail = PickupAvailability.builder()
                .location(loc)
                .type(request.type())
                .dayOfWeek(request.dayOfWeek())
                .specificDate(request.specificDate())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .maxCapacity(request.maxCapacity())
                .build();
        return mapAvailabilityToResponse(availabilityRepository.save(avail));
    }

    @Transactional
    public PickupAvailabilityResponse updateAvailability(Long locationId, Long aid, PickupAvailabilityRequest request) {
        PickupAvailability avail = availabilityRepository.findByIdAndLocationId(aid, locationId)
                .orElseThrow(() -> new ResourceNotFoundException("PickupAvailability", "id", aid));
        validateAvailabilityRequest(request);
        avail.setType(request.type());
        avail.setDayOfWeek(request.dayOfWeek());
        avail.setSpecificDate(request.specificDate());
        avail.setStartTime(request.startTime());
        avail.setEndTime(request.endTime());
        avail.setMaxCapacity(request.maxCapacity());
        return mapAvailabilityToResponse(availabilityRepository.save(avail));
    }

    @Transactional
    public void deleteAvailability(Long locationId, Long aid) {
        PickupAvailability avail = availabilityRepository.findByIdAndLocationId(aid, locationId)
                .orElseThrow(() -> new ResourceNotFoundException("PickupAvailability", "id", aid));
        availabilityRepository.delete(avail);
    }

    @Transactional
    public PickupAvailabilityResponse toggleAvailability(Long locationId, Long aid) {
        PickupAvailability avail = availabilityRepository.findByIdAndLocationId(aid, locationId)
                .orElseThrow(() -> new ResourceNotFoundException("PickupAvailability", "id", aid));
        avail.setActive(!avail.isActive());
        return mapAvailabilityToResponse(availabilityRepository.save(avail));
    }

    /**
     * Returns ISO date strings (YYYY-MM-DD) of dates within [from, to] that have at least one
     * rule with remaining capacity.
     */
    public List<String> getAvailableDates(Long locationId, LocalDate from, LocalDate to) {
        List<PickupAvailability> rules = availabilityRepository.findByLocationIdAndActiveTrue(locationId);
        TreeSet<LocalDate> result = new TreeSet<>();

        for (PickupAvailability rule : rules) {
            if (rule.getType() == AvailabilityType.RECURRING && rule.getDayOfWeek() != null) {
                LocalDate cursor = from;
                while (!cursor.isAfter(to)) {
                    if (cursor.getDayOfWeek() == rule.getDayOfWeek()
                            && !pickupExceptionRepository.existsByAvailabilityIdAndDate(rule.getId(), cursor)) {
                        long booked = orderRepository.countPickupOrdersForRule(locationId, cursor, rule.getId(), EXCLUDED_STATUSES);
                        if (booked < rule.getMaxCapacity()) {
                            result.add(cursor);
                        }
                    }
                    cursor = cursor.plusDays(1);
                }
            } else if (rule.getType() == AvailabilityType.SPECIFIC_DATE && rule.getSpecificDate() != null) {
                LocalDate d = rule.getSpecificDate();
                if (!d.isBefore(from) && !d.isAfter(to)
                        && !pickupExceptionRepository.existsByAvailabilityIdAndDate(rule.getId(), d)) {
                    long booked = orderRepository.countPickupOrdersForRule(locationId, d, rule.getId(), EXCLUDED_STATUSES);
                    if (booked < rule.getMaxCapacity()) {
                        result.add(d);
                    }
                }
            }
        }

        return result.stream().map(d -> d.format(DateTimeFormatter.ISO_LOCAL_DATE)).toList();
    }

    /**
     * Returns availability rules for the given date that still have remaining capacity.
     * Used by checkout to show selectable time slots.
     */
    public List<PickupAvailabilityResponse> getAvailableSlotsForDate(Long locationId, LocalDate date) {
        List<PickupAvailability> rules = getAvailabilityRulesForDate(locationId, date);
        return rules.stream()
                .filter(rule -> {
                    long booked = orderRepository.countPickupOrdersForRule(locationId, date, rule.getId(), EXCLUDED_STATUSES);
                    return booked < rule.getMaxCapacity();
                })
                .map(this::mapAvailabilityToResponse)
                .toList();
    }

    /**
     * Returns the active availability rules that cover the given date,
     * excluding rules that have a PickupException for that date.
     */
    public List<PickupAvailability> getAvailabilityRulesForDate(Long locationId, LocalDate date) {
        List<PickupAvailability> rules = availabilityRepository.findByLocationIdAndActiveTrue(locationId);
        return rules.stream().filter(rule -> {
            if (rule.getType() == AvailabilityType.RECURRING) {
                if (rule.getDayOfWeek() == null || rule.getDayOfWeek() != date.getDayOfWeek()) return false;
            } else {
                if (!date.equals(rule.getSpecificDate())) return false;
            }
            return !pickupExceptionRepository.existsByAvailabilityIdAndDate(rule.getId(), date);
        }).toList();
    }

    /**
     * Returns "HH:mm – HH:mm" label for the active rule covering the given date.
     */
    public String getAvailabilityTimeLabel(Long locationId, LocalDate date) {
        List<PickupAvailability> rules = availabilityRepository.findByLocationIdAndActiveTrue(locationId);
        for (PickupAvailability rule : rules) {
            if (rule.getType() == AvailabilityType.RECURRING
                    && rule.getDayOfWeek() != null
                    && rule.getDayOfWeek() == date.getDayOfWeek()) {
                return formatTimeLabel(rule);
            } else if (rule.getType() == AvailabilityType.SPECIFIC_DATE
                    && date.equals(rule.getSpecificDate())) {
                return formatTimeLabel(rule);
            }
        }
        return "";
    }

    public String formatTimeLabelPublic(PickupAvailability rule) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
        return rule.getStartTime().format(fmt) + " – " + rule.getEndTime().format(fmt);
    }

    private String formatTimeLabel(PickupAvailability rule) {
        return formatTimeLabelPublic(rule);
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

    private void validateAvailabilityRequest(PickupAvailabilityRequest request) {
        if (request.type() == AvailabilityType.RECURRING && request.dayOfWeek() == null) {
            throw new BadRequestException("dayOfWeek is required for RECURRING availability.");
        }
        if (request.type() == AvailabilityType.SPECIFIC_DATE && request.specificDate() == null) {
            throw new BadRequestException("specificDate is required for SPECIFIC_DATE availability.");
        }
    }

    private PickupLocationResponse mapToResponse(PickupLocation loc, boolean activeOnly) {
        return new PickupLocationResponse(
                loc.getId(), loc.getName(), loc.getAddress(),
                loc.getCity(), loc.getState(), loc.isActive());
    }

    private PickupAvailabilityResponse mapAvailabilityToResponse(PickupAvailability avail) {
        List<PickupExceptionResponse> exceptions = pickupExceptionRepository
                .findByAvailabilityId(avail.getId()).stream()
                .map(this::mapExceptionToResponse)
                .toList();
        return new PickupAvailabilityResponse(
                avail.getId(), avail.getType(), avail.getDayOfWeek(),
                avail.getSpecificDate(), avail.getStartTime(), avail.getEndTime(),
                avail.getMaxCapacity(), avail.isActive(), exceptions);
    }

    // ─── Exceptions ───────────────────────────────────────────────────────────

    public List<PickupExceptionResponse> getExceptions(Long locationId, Long aid) {
        availabilityRepository.findByIdAndLocationId(aid, locationId)
                .orElseThrow(() -> new ResourceNotFoundException("PickupAvailability", "id", aid));
        return pickupExceptionRepository.findByAvailabilityId(aid).stream()
                .map(this::mapExceptionToResponse)
                .toList();
    }

    @Transactional
    public PickupExceptionResponse createException(Long locationId, Long aid, PickupExceptionRequest req) {
        PickupAvailability avail = availabilityRepository.findByIdAndLocationId(aid, locationId)
                .orElseThrow(() -> new ResourceNotFoundException("PickupAvailability", "id", aid));

        if (pickupExceptionRepository.existsByAvailabilityIdAndDate(aid, req.date())) {
            throw new BadRequestException("Ya existe una excepción para esta regla en esa fecha.");
        }

        PickupException exception = PickupException.builder()
                .availability(avail)
                .date(req.date())
                .reason(req.reason())
                .build();
        PickupException saved = pickupExceptionRepository.save(exception);

        // Mark affected orders as cancelled and notify each customer
        List<Order> affected = orderRepository.findAffectedPickupOrders(aid, req.date(), EXCLUDED_STATUSES);
        for (Order order : affected) {
            order.setPickupCancelled(true);
            orderRepository.save(order);

            String toEmail;
            String firstName;
            if (order.getUser() != null) {
                toEmail = order.getUser().getEmail();
                firstName = order.getUser().getFirstName();
            } else {
                toEmail = order.getGuestEmail();
                firstName = order.getGuestFirstName();
            }
            if (toEmail != null) {
                emailService.sendPickupRescheduleEmail(
                        toEmail, firstName, order.getOrderNumber(),
                        order.getPickupLocationName(), req.date(),
                        order.getPickupTimeSlotLabel(), req.reason());
            }
        }

        return mapExceptionToResponse(saved);
    }

    @Transactional
    public void deleteException(Long locationId, Long aid, Long eid) {
        availabilityRepository.findByIdAndLocationId(aid, locationId)
                .orElseThrow(() -> new ResourceNotFoundException("PickupAvailability", "id", aid));
        PickupException exception = pickupExceptionRepository.findByIdAndAvailabilityId(eid, aid)
                .orElseThrow(() -> new ResourceNotFoundException("PickupException", "id", eid));
        pickupExceptionRepository.delete(exception);
    }

    private PickupExceptionResponse mapExceptionToResponse(PickupException e) {
        return new PickupExceptionResponse(e.getId(), e.getDate(), e.getReason(), e.getCreatedAt());
    }
}
