package com.ecommerce.service;

import com.ecommerce.dto.request.StoreInfoRequest;
import com.ecommerce.dto.response.StoreInfoResponse;
import com.ecommerce.entity.StoreImage;
import com.ecommerce.entity.StoreInfo;
import com.ecommerce.repository.StoreImageRepository;
import com.ecommerce.repository.StoreInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StoreInfoService")
class StoreInfoServiceTest {

    @Mock StoreInfoRepository storeInfoRepository;
    @Mock StoreImageRepository storeImageRepository;
    @InjectMocks StoreInfoService service;

    private StoreInfo testInfo;

    @BeforeEach
    void setUp() {
        testInfo = StoreInfo.builder()
                .id(1L)
                .name("Mi Tienda")
                .aboutText("Descripción")
                .mission("Misión")
                .vision("Visión")
                .phone("+52 55 1234 5678")
                .build();
    }

    // ─── getOrCreate ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getOrCreate")
    class GetOrCreate {

        @Test
        @DisplayName("returns existing record when id=1 exists")
        void getOrCreate_existing() {
            when(storeInfoRepository.findById(1L)).thenReturn(Optional.of(testInfo));

            StoreInfo result = service.getOrCreate();

            assertThat(result).isEqualTo(testInfo);
            verify(storeInfoRepository, never()).save(any());
        }

        @Test
        @DisplayName("creates and saves new record when none exists")
        void getOrCreate_createsNew() {
            when(storeInfoRepository.findById(1L)).thenReturn(Optional.empty());
            when(storeInfoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            StoreInfo result = service.getOrCreate();

            assertThat(result.getId()).isEqualTo(1L);
            verify(storeInfoRepository).save(any(StoreInfo.class));
        }
    }

    // ─── getPublic ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getPublic")
    class GetPublic {

        @Test
        @DisplayName("returns StoreInfoResponse with info fields and active images")
        void getPublic_returnsAllData() {
            StoreImage img = StoreImage.builder().id(1L).url("https://img.url").displayOrder(1).active(true).build();
            when(storeInfoRepository.findById(1L)).thenReturn(Optional.of(testInfo));
            when(storeImageRepository.findAllByActiveTrueOrderByDisplayOrderAsc()).thenReturn(List.of(img));

            StoreInfoResponse response = service.getPublic();

            assertThat(response.name()).isEqualTo("Mi Tienda");
            assertThat(response.aboutText()).isEqualTo("Descripción");
            assertThat(response.mission()).isEqualTo("Misión");
            assertThat(response.vision()).isEqualTo("Visión");
            assertThat(response.phone()).isEqualTo("+52 55 1234 5678");
            assertThat(response.images()).hasSize(1);
            assertThat(response.images().get(0).getUrl()).isEqualTo("https://img.url");
        }

        @Test
        @DisplayName("returns empty images list when no active images")
        void getPublic_noImages() {
            when(storeInfoRepository.findById(1L)).thenReturn(Optional.of(testInfo));
            when(storeImageRepository.findAllByActiveTrueOrderByDisplayOrderAsc()).thenReturn(List.of());

            StoreInfoResponse response = service.getPublic();

            assertThat(response.images()).isEmpty();
        }
    }

    // ─── update ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("updates only non-null fields and leaves others unchanged")
        void update_onlyNonNullFields() {
            when(storeInfoRepository.findById(1L)).thenReturn(Optional.of(testInfo));
            when(storeInfoRepository.save(any())).thenReturn(testInfo);
            when(storeImageRepository.findAllByActiveTrueOrderByDisplayOrderAsc()).thenReturn(List.of());

            service.update(new StoreInfoRequest("Nuevo Nombre", null, null, null, null, null, null));

            verify(storeInfoRepository).save(argThat(info ->
                    info.getName().equals("Nuevo Nombre") &&
                    info.getAboutText().equals("Descripción") &&
                    info.getMission().equals("Misión")
            ));
        }

        @Test
        @DisplayName("updates all fields when all are provided")
        void update_allFields() {
            when(storeInfoRepository.findById(1L)).thenReturn(Optional.of(testInfo));
            when(storeInfoRepository.save(any())).thenReturn(testInfo);
            when(storeImageRepository.findAllByActiveTrueOrderByDisplayOrderAsc()).thenReturn(List.of());

            service.update(new StoreInfoRequest("A", "B", "C", "D", "E", null, null));

            verify(storeInfoRepository).save(argThat(info ->
                    info.getName().equals("A") &&
                    info.getAboutText().equals("B") &&
                    info.getMission().equals("C") &&
                    info.getVision().equals("D") &&
                    info.getPhone().equals("E")
            ));
        }

        @Test
        @DisplayName("returns StoreInfoResponse after saving")
        void update_returnsResponse() {
            when(storeInfoRepository.findById(1L)).thenReturn(Optional.of(testInfo));
            when(storeInfoRepository.save(any())).thenReturn(testInfo);
            when(storeImageRepository.findAllByActiveTrueOrderByDisplayOrderAsc()).thenReturn(List.of());

            StoreInfoResponse response = service.update(new StoreInfoRequest("X", null, null, null, null, null, null));

            assertThat(response).isNotNull();
            assertThat(response.images()).isEmpty();
        }
    }

    // ─── addImage ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("addImage")
    class AddImage {

        @Test
        @DisplayName("saves image with url and displayOrder = count + 1")
        void addImage_setsDisplayOrder() {
            when(storeImageRepository.count()).thenReturn(3L);
            when(storeImageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            StoreImage result = service.addImage("https://new.img");

            assertThat(result.getUrl()).isEqualTo("https://new.img");
            assertThat(result.getDisplayOrder()).isEqualTo(4);
            assertThat(result.isActive()).isTrue();
        }

        @Test
        @DisplayName("sets displayOrder=1 when no images exist yet")
        void addImage_firstImage() {
            when(storeImageRepository.count()).thenReturn(0L);
            when(storeImageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            StoreImage result = service.addImage("https://first.img");

            assertThat(result.getDisplayOrder()).isEqualTo(1);
        }

        @Test
        @DisplayName("saved image is always active")
        void addImage_alwaysActive() {
            when(storeImageRepository.count()).thenReturn(0L);
            when(storeImageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            StoreImage result = service.addImage("https://any.img");

            assertThat(result.isActive()).isTrue();
        }
    }

    // ─── deleteImage ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteImage")
    class DeleteImage {

        @Test
        @DisplayName("calls deleteById with the given id")
        void deleteImage_callsRepository() {
            service.deleteImage(5L);

            verify(storeImageRepository).deleteById(5L);
        }
    }

    // ─── reorderImages ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("reorderImages")
    class ReorderImages {

        @Test
        @DisplayName("updates displayOrder of each image by list index")
        void reorderImages_updatesOrder() {
            StoreImage imgA = StoreImage.builder().id(10L).displayOrder(3).build();
            StoreImage imgB = StoreImage.builder().id(20L).displayOrder(1).build();
            when(storeImageRepository.findById(10L)).thenReturn(Optional.of(imgA));
            when(storeImageRepository.findById(20L)).thenReturn(Optional.of(imgB));
            when(storeImageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.reorderImages(List.of(10L, 20L));

            assertThat(imgA.getDisplayOrder()).isEqualTo(0);
            assertThat(imgB.getDisplayOrder()).isEqualTo(1);
            verify(storeImageRepository, times(2)).save(any());
        }

        @Test
        @DisplayName("ignores ids with no matching image")
        void reorderImages_skipsNotFound() {
            when(storeImageRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatNoException().isThrownBy(() -> service.reorderImages(List.of(99L)));
            verify(storeImageRepository, never()).save(any());
        }

        @Test
        @DisplayName("does nothing for empty id list")
        void reorderImages_emptyList() {
            assertThatNoException().isThrownBy(() -> service.reorderImages(List.of()));
            verify(storeImageRepository, never()).findById(any());
        }
    }
}
