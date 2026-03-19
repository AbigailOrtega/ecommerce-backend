package com.ecommerce.service;

import com.ecommerce.dto.request.StoreInfoRequest;
import com.ecommerce.dto.response.StoreInfoResponse;
import com.ecommerce.entity.StoreImage;
import com.ecommerce.entity.StoreInfo;
import com.ecommerce.repository.StoreImageRepository;
import com.ecommerce.repository.StoreInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StoreInfoService {

    private final StoreInfoRepository storeInfoRepository;
    private final StoreImageRepository storeImageRepository;

    @Transactional
    public StoreInfo getOrCreate() {
        return storeInfoRepository.findById(1L).orElseGet(() -> {
            StoreInfo info = StoreInfo.builder().id(1L).build();
            return storeInfoRepository.save(info);
        });
    }

    public StoreInfoResponse getPublic() {
        StoreInfo info = getOrCreate();
        List<StoreImage> images = storeImageRepository.findAllByActiveTrueOrderByDisplayOrderAsc();
        return new StoreInfoResponse(
                info.getName(),
                info.getAboutText(),
                info.getMission(),
                info.getVision(),
                info.getPhone(),
                info.getLogoUrl(),
                info.getThemeKey(),
                images,
                info.getInstagramUrl(),
                info.getFacebookUrl()
        );
    }

    @Transactional
    public StoreInfoResponse update(StoreInfoRequest request) {
        StoreInfo info = getOrCreate();
        if (request.name() != null) info.setName(request.name());
        if (request.aboutText() != null) info.setAboutText(request.aboutText());
        if (request.mission() != null) info.setMission(request.mission());
        if (request.vision() != null) info.setVision(request.vision());
        if (request.phone() != null) info.setPhone(request.phone());
        if (request.logoUrl() != null) info.setLogoUrl(request.logoUrl().isBlank() ? null : request.logoUrl());
        if (request.themeKey() != null) info.setThemeKey(request.themeKey());
        if (request.instagramUrl() != null) info.setInstagramUrl(request.instagramUrl().isBlank() ? null : request.instagramUrl());
        if (request.facebookUrl() != null) info.setFacebookUrl(request.facebookUrl().isBlank() ? null : request.facebookUrl());
        storeInfoRepository.save(info);
        return getPublic();
    }

    @Transactional
    public StoreImage addImage(String url) {
        long count = storeImageRepository.count();
        StoreImage image = StoreImage.builder()
                .url(url)
                .displayOrder((int) count + 1)
                .active(true)
                .build();
        return storeImageRepository.save(image);
    }

    @Transactional
    public void deleteImage(Long id) {
        storeImageRepository.deleteById(id);
    }

    @Transactional
    public void reorderImages(List<Long> ids) {
        for (int i = 0; i < ids.size(); i++) {
            final int order = i;
            storeImageRepository.findById(ids.get(i)).ifPresent(img -> {
                img.setDisplayOrder(order);
                storeImageRepository.save(img);
            });
        }
    }
}
