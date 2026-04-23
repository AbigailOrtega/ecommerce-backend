package com.ecommerce.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import com.ecommerce.exception.BadRequestException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class CloudinaryService {

    @Value("${app.cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${app.cloudinary.api-key:}")
    private String apiKey;

    @Value("${app.cloudinary.api-secret:}")
    private String apiSecret;

    @Value("${app.cloudinary.image.product-max-width:800}")
    private int productMaxWidth;

    @Value("${app.cloudinary.image.product-max-height:800}")
    private int productMaxHeight;

    @Value("${app.cloudinary.image.banner-max-width:1920}")
    private int bannerMaxWidth;

    @Value("${app.cloudinary.image.banner-max-height:600}")
    private int bannerMaxHeight;

    private Cloudinary cloudinary;

    @PostConstruct
    public void init() {
        if (!cloudName.isBlank() && !apiKey.isBlank() && !apiSecret.isBlank()) {
            cloudinary = new Cloudinary(ObjectUtils.asMap(
                    "cloud_name", cloudName,
                    "api_key", apiKey,
                    "api_secret", apiSecret,
                    "secure", true
            ));
            log.info("Cloudinary initialized for cloud: {}", cloudName);
        } else {
            log.warn("Cloudinary is not configured. Set CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET.");
        }
    }

    public boolean isConfigured() {
        return cloudinary != null;
    }

    public String upload(MultipartFile file, String type) {
        if (!isConfigured()) {
            throw new BadRequestException("Cloudinary is not configured.");
        }
        try {
            Map<String, Object> options = new HashMap<>();
            options.put("resource_type", "image");

            switch (type) {
                case "product" -> {
                    options.put("folder", "products");
                    options.put("transformation", new Transformation()
                            .width(productMaxWidth).height(productMaxHeight)
                            .crop("limit").quality("auto").fetchFormat("auto"));
                }
                case "banner" -> {
                    options.put("folder", "banners");
                    options.put("transformation", new Transformation()
                            .width(bannerMaxWidth).height(bannerMaxHeight)
                            .crop("limit").quality("auto").fetchFormat("auto"));
                }
                default -> {
                    options.put("folder", "uploads");
                    options.put("transformation", new Transformation()
                            .quality("auto").fetchFormat("auto"));
                }
            }

            Map result = cloudinary.uploader().upload(file.getBytes(), options);
            String url = (String) result.get("secure_url");
            log.info("Image uploaded to Cloudinary [type={}]: {}", type, url);
            return url;
        } catch (IOException e) {
            log.error("Cloudinary upload error: {}", e.getMessage());
            throw new RuntimeException("Failed to upload image: " + e.getMessage());
        }
    }
}
