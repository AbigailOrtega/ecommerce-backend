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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PickupLocationService")
class PickupLocationServiceTest {

    @Mock private PickupLocationRepository locationRepository;
    @Mock private PickupTimeSlotRepository timeSlotRepository;

    @InjectMocks private PickupLocationService pickupLocationService;

    private PickupLocation activeLocation;
    private PickupLocation inactiveLocation;
    private PickupTimeSlot activeSlot;
    private PickupTimeSlot inactiveSlot;

    @BeforeEach
    void setUp() {
        activeSlot = PickupTimeSlot.builder()
                .id(10L)
                .label("10:00 - 12:00")
                .active(true)
                .build();

        inactiveSlot = PickupTimeSlot.builder()
                .id(11L)
                .label("14:00 - 16:00")
                .active(false)
                .build();

        activeLocation = PickupLocation.builder()
                .id(1L)
                .name("Main Store")
                .address("Av. Reforma 100")
                .city("CDMX")
                .state("CDMX")
                .active(true)
                .timeSlots(new ArrayList<>(List.of(activeSlot, inactiveSlot)))
                .build();

        inactiveLocation = PickupLocation.builder()
                .id(2L)
                .name("Warehouse")
                .address("Calle Industrial 5")
                .city("Monterrey")
                .state("NL")
                .active(false)
                .timeSlots(new ArrayList<>())
                .build();
    }

    // ─── getActiveLocations ──────────────────────────────────────────────────

    @Nested
    @DisplayName("getActiveLocations")
    class GetActiveLocations {

        @Test
        @DisplayName("returns only active locations")
        void getActiveLocations_returnsActiveLocations() {
            when(locationRepository.findAllByActiveTrueOrderByNameAsc())
                    .thenReturn(List.of(activeLocation));

            List<PickupLocationResponse> result = pickupLocationService.getActiveLocations();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).name()).isEqualTo("Main Store");
            assertThat(result.get(0).active()).isTrue();
        }

        @Test
        @DisplayName("returns empty list when no active locations exist")
        void getActiveLocations_emptyWhenNoneActive() {
            when(locationRepository.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of());

            assertThat(pickupLocationService.getActiveLocations()).isEmpty();
        }

        @Test
        @DisplayName("filters time slots to active-only in public response")
        void getActiveLocations_filtersInactiveTimeSlots() {
            when(locationRepository.findAllByActiveTrueOrderByNameAsc())
                    .thenReturn(List.of(activeLocation));

            List<PickupLocationResponse> result = pickupLocationService.getActiveLocations();

            List<PickupTimeSlotResponse> slots = result.get(0).timeSlots();
            assertThat(slots).hasSize(1);
            assertThat(slots.get(0).active()).isTrue();
            assertThat(slots.get(0).label()).isEqualTo("10:00 - 12:00");
        }

        @Test
        @DisplayName("returns empty timeSlots list when location has no active slots")
        void getActiveLocations_emptyTimeSlotsWhenAllInactive() {
            activeLocation.setTimeSlots(new ArrayList<>(List.of(inactiveSlot)));
            when(locationRepository.findAllByActiveTrueOrderByNameAsc())
                    .thenReturn(List.of(activeLocation));

            List<PickupLocationResponse> result = pickupLocationService.getActiveLocations();

            assertThat(result.get(0).timeSlots()).isEmpty();
        }

        @Test
        @DisplayName("maps all location fields correctly")
        void getActiveLocations_mapsAllFields() {
            when(locationRepository.findAllByActiveTrueOrderByNameAsc())
                    .thenReturn(List.of(activeLocation));

            PickupLocationResponse response = pickupLocationService.getActiveLocations().get(0);

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("Main Store");
            assertThat(response.address()).isEqualTo("Av. Reforma 100");
            assertThat(response.city()).isEqualTo("CDMX");
            assertThat(response.state()).isEqualTo("CDMX");
        }
    }

    // ─── getAllLocations ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAllLocations")
    class GetAllLocations {

        @Test
        @DisplayName("returns all locations including inactive ones")
        void getAllLocations_includesInactive() {
            when(locationRepository.findAll()).thenReturn(List.of(activeLocation, inactiveLocation));

            List<PickupLocationResponse> result = pickupLocationService.getAllLocations();

            assertThat(result).hasSize(2);
            assertThat(result).extracting(PickupLocationResponse::active)
                    .containsExactlyInAnyOrder(true, false);
        }

        @Test
        @DisplayName("includes both active and inactive time slots in admin response")
        void getAllLocations_includesAllTimeSlots() {
            when(locationRepository.findAll()).thenReturn(List.of(activeLocation));

            List<PickupLocationResponse> result = pickupLocationService.getAllLocations();

            assertThat(result.get(0).timeSlots()).hasSize(2);
        }

        @Test
        @DisplayName("returns empty list when no locations exist")
        void getAllLocations_emptyWhenNoneExist() {
            when(locationRepository.findAll()).thenReturn(List.of());

            assertThat(pickupLocationService.getAllLocations()).isEmpty();
        }

        @Test
        @DisplayName("includes inactive time slots that are excluded from public view")
        void getAllLocations_inactiveSlotsVisible() {
            when(locationRepository.findAll()).thenReturn(List.of(activeLocation));

            List<PickupTimeSlotResponse> slots = pickupLocationService.getAllLocations()
                    .get(0).timeSlots();

            assertThat(slots).extracting(PickupTimeSlotResponse::active)
                    .containsExactlyInAnyOrder(true, false);
        }

        @Test
        @DisplayName("maps inactive location fields correctly")
        void getAllLocations_mapsInactiveLocationFields() {
            when(locationRepository.findAll()).thenReturn(List.of(inactiveLocation));

            PickupLocationResponse response = pickupLocationService.getAllLocations().get(0);

            assertThat(response.id()).isEqualTo(2L);
            assertThat(response.name()).isEqualTo("Warehouse");
            assertThat(response.active()).isFalse();
        }
    }

    // ─── create ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("saves a new location and returns its response")
        void create_success() {
            PickupLocationRequest request = new PickupLocationRequest(
                    "New Branch", "Blvd. Insurgentes 50", "Tijuana", "BC");
            PickupLocation saved = PickupLocation.builder()
                    .id(5L).name("New Branch").address("Blvd. Insurgentes 50")
                    .city("Tijuana").state("BC").active(true)
                    .timeSlots(new ArrayList<>()).build();
            when(locationRepository.save(any(PickupLocation.class))).thenReturn(saved);

            PickupLocationResponse response = pickupLocationService.create(request);

            assertThat(response.id()).isEqualTo(5L);
            assertThat(response.name()).isEqualTo("New Branch");
            assertThat(response.city()).isEqualTo("Tijuana");
        }

        @Test
        @DisplayName("builds entity from all request fields before saving")
        void create_buildsEntityFromRequest() {
            PickupLocationRequest request = new PickupLocationRequest(
                    "Branch A", "Calle 5", "GDL", "JAL");
            when(locationRepository.save(any(PickupLocation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            pickupLocationService.create(request);

            ArgumentCaptor<PickupLocation> captor = ArgumentCaptor.forClass(PickupLocation.class);
            verify(locationRepository).save(captor.capture());
            PickupLocation entity = captor.getValue();
            assertThat(entity.getName()).isEqualTo("Branch A");
            assertThat(entity.getAddress()).isEqualTo("Calle 5");
            assertThat(entity.getCity()).isEqualTo("GDL");
            assertThat(entity.getState()).isEqualTo("JAL");
        }

        @Test
        @DisplayName("new location has empty timeSlots list")
        void create_hasEmptyTimeSlots() {
            PickupLocationRequest request = new PickupLocationRequest(
                    "Empty", "No slots", "City", "State");
            when(locationRepository.save(any(PickupLocation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            PickupLocationResponse response = pickupLocationService.create(request);

            assertThat(response.timeSlots()).isEmpty();
        }

        @Test
        @DisplayName("calls locationRepository.save exactly once")
        void create_savesExactlyOnce() {
            PickupLocationRequest request = new PickupLocationRequest("X", "Y", "Z", "W");
            when(locationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            pickupLocationService.create(request);

            verify(locationRepository, times(1)).save(any());
        }

        @Test
        @DisplayName("does not interact with timeSlotRepository during create")
        void create_doesNotTouchTimeSlotRepository() {
            PickupLocationRequest request = new PickupLocationRequest("X", "Y", "Z", "W");
            when(locationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            pickupLocationService.create(request);

            verifyNoInteractions(timeSlotRepository);
        }
    }

    // ─── update ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("updates all fields and returns updated response")
        void update_success() {
            PickupLocationRequest request = new PickupLocationRequest(
                    "Updated Name", "Updated Address", "Puebla", "PUE");
            when(locationRepository.findById(1L)).thenReturn(Optional.of(activeLocation));
            when(locationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PickupLocationResponse response = pickupLocationService.update(1L, request);

            assertThat(response.name()).isEqualTo("Updated Name");
            assertThat(response.address()).isEqualTo("Updated Address");
            assertThat(response.city()).isEqualTo("Puebla");
            assertThat(response.state()).isEqualTo("PUE");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when location does not exist")
        void update_notFound() {
            PickupLocationRequest request = new PickupLocationRequest("A", "B", "C", "D");
            when(locationRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pickupLocationService.update(999L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("PickupLocation");

            verify(locationRepository, never()).save(any());
        }

        @Test
        @DisplayName("mutates the existing entity, preserving its id")
        void update_preservesId() {
            PickupLocationRequest request = new PickupLocationRequest("New", "Addr", "City", "State");
            when(locationRepository.findById(1L)).thenReturn(Optional.of(activeLocation));
            when(locationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PickupLocationResponse response = pickupLocationService.update(1L, request);

            assertThat(response.id()).isEqualTo(1L);
        }

        @Test
        @DisplayName("preserves active status after update")
        void update_preservesActiveStatus() {
            PickupLocationRequest request = new PickupLocationRequest("New", "Addr", "City", "State");
            when(locationRepository.findById(1L)).thenReturn(Optional.of(activeLocation));
            when(locationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PickupLocationResponse response = pickupLocationService.update(1L, request);

            assertThat(response.active()).isTrue();
        }

        @Test
        @DisplayName("includes all time slots (both active/inactive) in updated response")
        void update_includesAllTimeSlotsInResponse() {
            PickupLocationRequest request = new PickupLocationRequest("New", "Addr", "City", "State");
            when(locationRepository.findById(1L)).thenReturn(Optional.of(activeLocation));
            when(locationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PickupLocationResponse response = pickupLocationService.update(1L, request);

            assertThat(response.timeSlots()).hasSize(2);
        }
    }

    // ─── delete ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("deletes the location when it exists")
        void delete_success() {
            when(locationRepository.findById(1L)).thenReturn(Optional.of(activeLocation));

            pickupLocationService.delete(1L);

            verify(locationRepository).delete(activeLocation);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when location does not exist")
        void delete_notFound() {
            when(locationRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pickupLocationService.delete(999L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(locationRepository, never()).delete(any());
        }

        @Test
        @DisplayName("passes the exact entity instance to repository.delete")
        void delete_passesCorrectEntity() {
            when(locationRepository.findById(2L)).thenReturn(Optional.of(inactiveLocation));

            pickupLocationService.delete(2L);

            verify(locationRepository).delete(inactiveLocation);
        }

        @Test
        @DisplayName("calls locationRepository.delete exactly once")
        void delete_deletesExactlyOnce() {
            when(locationRepository.findById(1L)).thenReturn(Optional.of(activeLocation));

            pickupLocationService.delete(1L);

            verify(locationRepository, times(1)).delete(any());
        }

        @Test
        @DisplayName("never calls locationRepository.save during delete")
        void delete_doesNotSave() {
            when(locationRepository.findById(1L)).thenReturn(Optional.of(activeLocation));

            pickupLocationService.delete(1L);

            verify(locationRepository, never()).save(any());
        }
    }

    // ─── toggle ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toggle")
    class Toggle {

        @Test
        @DisplayName("sets inactive when location is currently active")
        void toggle_activeToInactive() {
            when(locationRepository.findById(1L)).thenReturn(Optional.of(activeLocation));
            when(locationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PickupLocationResponse response = pickupLocationService.toggle(1L);

            assertThat(response.active()).isFalse();
        }

        @Test
        @DisplayName("sets active when location is currently inactive")
        void toggle_inactiveToActive() {
            when(locationRepository.findById(2L)).thenReturn(Optional.of(inactiveLocation));
            when(locationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PickupLocationResponse response = pickupLocationService.toggle(2L);

            assertThat(response.active()).isTrue();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when location does not exist")
        void toggle_notFound() {
            when(locationRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pickupLocationService.toggle(999L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(locationRepository, never()).save(any());
        }

        @Test
        @DisplayName("saves the entity after toggling")
        void toggle_savesCalled() {
            when(locationRepository.findById(1L)).thenReturn(Optional.of(activeLocation));
            when(locationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            pickupLocationService.toggle(1L);

            verify(locationRepository).save(activeLocation);
        }

        @Test
        @DisplayName("does not alter fields other than active")
        void toggle_doesNotAlterOtherFields() {
            when(locationRepository.findById(1L)).thenReturn(Optional.of(activeLocation));
            when(locationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PickupLocationResponse response = pickupLocationService.toggle(1L);

            assertThat(response.name()).isEqualTo("Main Store");
            assertThat(response.city()).isEqualTo("CDMX");
        }
    }

    // ─── addTimeSlot ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("addTimeSlot")
    class AddTimeSlot {

        @Test
        @DisplayName("adds a new slot to an existing location and returns it")
        void addTimeSlot_success() {
            PickupTimeSlotRequest request = new PickupTimeSlotRequest("08:00 - 10:00");
            PickupTimeSlot savedSlot = PickupTimeSlot.builder()
                    .id(20L).label("08:00 - 10:00").active(true).location(activeLocation).build();
            when(locationRepository.findById(1L)).thenReturn(Optional.of(activeLocation));
            when(timeSlotRepository.save(any(PickupTimeSlot.class))).thenReturn(savedSlot);

            PickupTimeSlotResponse response = pickupLocationService.addTimeSlot(1L, request);

            assertThat(response.id()).isEqualTo(20L);
            assertThat(response.label()).isEqualTo("08:00 - 10:00");
            assertThat(response.active()).isTrue();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when location does not exist")
        void addTimeSlot_locationNotFound() {
            PickupTimeSlotRequest request = new PickupTimeSlotRequest("08:00 - 10:00");
            when(locationRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pickupLocationService.addTimeSlot(999L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("PickupLocation");

            verify(timeSlotRepository, never()).save(any());
        }

        @Test
        @DisplayName("sets the correct location on the new slot entity")
        void addTimeSlot_setsLocationOnSlot() {
            PickupTimeSlotRequest request = new PickupTimeSlotRequest("18:00 - 20:00");
            when(locationRepository.findById(1L)).thenReturn(Optional.of(activeLocation));
            when(timeSlotRepository.save(any(PickupTimeSlot.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            pickupLocationService.addTimeSlot(1L, request);

            ArgumentCaptor<PickupTimeSlot> captor = ArgumentCaptor.forClass(PickupTimeSlot.class);
            verify(timeSlotRepository).save(captor.capture());
            assertThat(captor.getValue().getLocation()).isEqualTo(activeLocation);
            assertThat(captor.getValue().getLabel()).isEqualTo("18:00 - 20:00");
        }

        @Test
        @DisplayName("calls timeSlotRepository.save exactly once")
        void addTimeSlot_savesExactlyOnce() {
            PickupTimeSlotRequest request = new PickupTimeSlotRequest("09:00 - 11:00");
            when(locationRepository.findById(1L)).thenReturn(Optional.of(activeLocation));
            when(timeSlotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            pickupLocationService.addTimeSlot(1L, request);

            verify(timeSlotRepository, times(1)).save(any());
        }

        @Test
        @DisplayName("can add a slot to an inactive location")
        void addTimeSlot_worksForInactiveLocation() {
            PickupTimeSlotRequest request = new PickupTimeSlotRequest("11:00 - 13:00");
            when(locationRepository.findById(2L)).thenReturn(Optional.of(inactiveLocation));
            when(timeSlotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThatNoException().isThrownBy(() ->
                    pickupLocationService.addTimeSlot(2L, request));
        }
    }

    // ─── updateTimeSlot ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateTimeSlot")
    class UpdateTimeSlot {

        @Test
        @DisplayName("updates the slot label and returns updated response")
        void updateTimeSlot_success() {
            PickupTimeSlotRequest request = new PickupTimeSlotRequest("16:00 - 18:00");
            when(timeSlotRepository.findById(10L)).thenReturn(Optional.of(activeSlot));
            when(timeSlotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PickupTimeSlotResponse response = pickupLocationService.updateTimeSlot(10L, request);

            assertThat(response.label()).isEqualTo("16:00 - 18:00");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when slot does not exist")
        void updateTimeSlot_notFound() {
            PickupTimeSlotRequest request = new PickupTimeSlotRequest("16:00 - 18:00");
            when(timeSlotRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pickupLocationService.updateTimeSlot(999L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("PickupTimeSlot");

            verify(timeSlotRepository, never()).save(any());
        }

        @Test
        @DisplayName("mutates the existing slot entity label")
        void updateTimeSlot_mutatesEntity() {
            PickupTimeSlotRequest request = new PickupTimeSlotRequest("New Label");
            when(timeSlotRepository.findById(10L)).thenReturn(Optional.of(activeSlot));
            when(timeSlotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            pickupLocationService.updateTimeSlot(10L, request);

            ArgumentCaptor<PickupTimeSlot> captor = ArgumentCaptor.forClass(PickupTimeSlot.class);
            verify(timeSlotRepository).save(captor.capture());
            assertThat(captor.getValue().getLabel()).isEqualTo("New Label");
        }

        @Test
        @DisplayName("preserves slot id after update")
        void updateTimeSlot_preservesId() {
            PickupTimeSlotRequest request = new PickupTimeSlotRequest("Changed");
            when(timeSlotRepository.findById(10L)).thenReturn(Optional.of(activeSlot));
            when(timeSlotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PickupTimeSlotResponse response = pickupLocationService.updateTimeSlot(10L, request);

            assertThat(response.id()).isEqualTo(10L);
        }

        @Test
        @DisplayName("preserves slot active status after update")
        void updateTimeSlot_preservesActiveStatus() {
            PickupTimeSlotRequest request = new PickupTimeSlotRequest("Changed");
            when(timeSlotRepository.findById(10L)).thenReturn(Optional.of(activeSlot));
            when(timeSlotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PickupTimeSlotResponse response = pickupLocationService.updateTimeSlot(10L, request);

            assertThat(response.active()).isTrue();
        }
    }

    // ─── deleteTimeSlot ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteTimeSlot")
    class DeleteTimeSlot {

        @Test
        @DisplayName("deletes the time slot when it exists")
        void deleteTimeSlot_success() {
            when(timeSlotRepository.findById(10L)).thenReturn(Optional.of(activeSlot));

            pickupLocationService.deleteTimeSlot(10L);

            verify(timeSlotRepository).delete(activeSlot);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when slot does not exist")
        void deleteTimeSlot_notFound() {
            when(timeSlotRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pickupLocationService.deleteTimeSlot(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("PickupTimeSlot");

            verify(timeSlotRepository, never()).delete(any());
        }

        @Test
        @DisplayName("passes the correct entity to timeSlotRepository.delete")
        void deleteTimeSlot_passesCorrectEntity() {
            when(timeSlotRepository.findById(11L)).thenReturn(Optional.of(inactiveSlot));

            pickupLocationService.deleteTimeSlot(11L);

            verify(timeSlotRepository).delete(inactiveSlot);
        }

        @Test
        @DisplayName("calls delete exactly once")
        void deleteTimeSlot_deletesExactlyOnce() {
            when(timeSlotRepository.findById(10L)).thenReturn(Optional.of(activeSlot));

            pickupLocationService.deleteTimeSlot(10L);

            verify(timeSlotRepository, times(1)).delete(any());
        }

        @Test
        @DisplayName("never calls locationRepository during slot deletion")
        void deleteTimeSlot_doesNotTouchLocationRepository() {
            when(timeSlotRepository.findById(10L)).thenReturn(Optional.of(activeSlot));

            pickupLocationService.deleteTimeSlot(10L);

            verify(locationRepository, never()).delete(any());
            verify(locationRepository, never()).save(any());
        }
    }

    // ─── toggleTimeSlot ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("toggleTimeSlot")
    class ToggleTimeSlot {

        @Test
        @DisplayName("sets inactive when slot is currently active")
        void toggleTimeSlot_activeToInactive() {
            when(timeSlotRepository.findById(10L)).thenReturn(Optional.of(activeSlot));
            when(timeSlotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PickupTimeSlotResponse response = pickupLocationService.toggleTimeSlot(10L);

            assertThat(response.active()).isFalse();
        }

        @Test
        @DisplayName("sets active when slot is currently inactive")
        void toggleTimeSlot_inactiveToActive() {
            when(timeSlotRepository.findById(11L)).thenReturn(Optional.of(inactiveSlot));
            when(timeSlotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PickupTimeSlotResponse response = pickupLocationService.toggleTimeSlot(11L);

            assertThat(response.active()).isTrue();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when slot does not exist")
        void toggleTimeSlot_notFound() {
            when(timeSlotRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pickupLocationService.toggleTimeSlot(999L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(timeSlotRepository, never()).save(any());
        }

        @Test
        @DisplayName("calls save after toggling")
        void toggleTimeSlot_savesCalled() {
            when(timeSlotRepository.findById(10L)).thenReturn(Optional.of(activeSlot));
            when(timeSlotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            pickupLocationService.toggleTimeSlot(10L);

            verify(timeSlotRepository).save(activeSlot);
        }

        @Test
        @DisplayName("preserves slot label after toggle")
        void toggleTimeSlot_preservesLabel() {
            when(timeSlotRepository.findById(10L)).thenReturn(Optional.of(activeSlot));
            when(timeSlotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PickupTimeSlotResponse response = pickupLocationService.toggleTimeSlot(10L);

            assertThat(response.label()).isEqualTo("10:00 - 12:00");
        }
    }

    // ─── findById ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("returns the location when found")
        void findById_success() {
            when(locationRepository.findById(1L)).thenReturn(Optional.of(activeLocation));

            PickupLocation result = pickupLocationService.findById(1L);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("Main Store");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when id does not exist")
        void findById_notFound() {
            when(locationRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pickupLocationService.findById(404L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("PickupLocation")
                    .hasMessageContaining("404");
        }

        @Test
        @DisplayName("returns inactive location when it exists")
        void findById_returnsInactiveLocation() {
            when(locationRepository.findById(2L)).thenReturn(Optional.of(inactiveLocation));

            PickupLocation result = pickupLocationService.findById(2L);

            assertThat(result.isActive()).isFalse();
        }

        @Test
        @DisplayName("queries the repository with the exact id provided")
        void findById_queriesCorrectId() {
            when(locationRepository.findById(1L)).thenReturn(Optional.of(activeLocation));

            pickupLocationService.findById(1L);

            verify(locationRepository).findById(1L);
        }

        @Test
        @DisplayName("exception message contains the field name and value")
        void findById_exceptionContainsFieldInfo() {
            when(locationRepository.findById(7L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pickupLocationService.findById(7L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("id")
                    .hasMessageContaining("7");
        }
    }

    // ─── findActiveById ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("findActiveById")
    class FindActiveById {

        @Test
        @DisplayName("returns location when it exists and is active")
        void findActiveById_success() {
            when(locationRepository.findById(1L)).thenReturn(Optional.of(activeLocation));

            PickupLocation result = pickupLocationService.findActiveById(1L);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.isActive()).isTrue();
        }

        @Test
        @DisplayName("throws BadRequestException when location is inactive")
        void findActiveById_throwsWhenInactive() {
            when(locationRepository.findById(2L)).thenReturn(Optional.of(inactiveLocation));

            assertThatThrownBy(() -> pickupLocationService.findActiveById(2L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("not active");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when location does not exist")
        void findActiveById_throwsWhenNotFound() {
            when(locationRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pickupLocationService.findActiveById(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("does not throw when location is active")
        void findActiveById_noExceptionForActive() {
            when(locationRepository.findById(1L)).thenReturn(Optional.of(activeLocation));

            assertThatNoException().isThrownBy(() -> pickupLocationService.findActiveById(1L));
        }

        @Test
        @DisplayName("BadRequestException message mentions pickup location not active")
        void findActiveById_exceptionMessageIsDescriptive() {
            when(locationRepository.findById(2L)).thenReturn(Optional.of(inactiveLocation));

            assertThatThrownBy(() -> pickupLocationService.findActiveById(2L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Pickup location is not active.");
        }
    }
}
