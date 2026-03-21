package com.ecommerce.service;

import com.ecommerce.dto.request.PickupLocationRequest;
import com.ecommerce.dto.response.PickupLocationResponse;
import com.ecommerce.entity.PickupLocation;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.PickupLocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PickupLocationService")
class PickupLocationServiceTest {

    @Mock private PickupLocationRepository locationRepository;

    @InjectMocks private PickupLocationService pickupLocationService;

    private PickupLocation activeLocation;
    private PickupLocation inactiveLocation;

    @BeforeEach
    void setUp() {
        activeLocation = PickupLocation.builder()
                .id(1L)
                .name("Main Store")
                .address("Av. Reforma 100")
                .city("CDMX")
                .state("CDMX")
                .active(true)
                .build();

        inactiveLocation = PickupLocation.builder()
                .id(2L)
                .name("Warehouse")
                .address("Calle Industrial 5")
                .city("Monterrey")
                .state("NL")
                .active(false)
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
        @DisplayName("returns empty list when no locations exist")
        void getAllLocations_emptyWhenNoneExist() {
            when(locationRepository.findAll()).thenReturn(List.of());

            assertThat(pickupLocationService.getAllLocations()).isEmpty();
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
                    .build();
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
        @DisplayName("calls locationRepository.save exactly once")
        void create_savesExactlyOnce() {
            PickupLocationRequest request = new PickupLocationRequest("X", "Y", "Z", "W");
            when(locationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            pickupLocationService.create(request);

            verify(locationRepository, times(1)).save(any());
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
