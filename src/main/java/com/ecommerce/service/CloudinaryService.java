package com.ecommerce.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.ecommerce.exception.BadRequestException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

    public String upload(MultipartFile file) {
        if (!isConfigured()) {
            throw new BadRequestException("Cloudinary is not configured.");
        }
        try {
            Map result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", "products",
                    "resource_type", "image"
            ));
            String url = (String) result.get("secure_url");
            log.info("Image uploaded to Cloudinary: {}", url);
            return url;
        } catch (IOException e) {
            log.error("Cloudinary upload error: {}", e.getMessage());
            throw new RuntimeException("Failed to upload image: " + e.getMessage());
        }
    }
}
