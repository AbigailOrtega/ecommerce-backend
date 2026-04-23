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

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
            int maxWidth;
            int maxHeight;
            String folder;

            switch (type) {
                case "product" -> { maxWidth = productMaxWidth; maxHeight = productMaxHeight; folder = "products"; }
                case "banner"  -> { maxWidth = bannerMaxWidth;  maxHeight = bannerMaxHeight;  folder = "banners";  }
                default        -> { maxWidth = 0;               maxHeight = 0;                folder = "uploads";  }
            }

            byte[] bytes = (maxWidth > 0)
                    ? resizeImage(file.getBytes(), maxWidth, maxHeight)
                    : file.getBytes();

            Map<String, Object> options = new HashMap<>();
            options.put("folder", folder);
            options.put("resource_type", "image");
            options.put("format", "webp");
            options.put("transformation", new Transformation().quality("auto"));

            Map result = cloudinary.uploader().upload(bytes, options);
            String url = (String) result.get("secure_url");
            log.info("Image uploaded to Cloudinary [type={}, folder={}]: {}", type, folder, url);
            return url;
        } catch (IOException e) {
            log.error("Cloudinary upload error: {}", e.getMessage());
            throw new RuntimeException("Failed to upload image: " + e.getMessage());
        }
    }

    private byte[] resizeImage(byte[] originalBytes, int maxWidth, int maxHeight) throws IOException {
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(originalBytes));
        if (original == null) {
            return originalBytes;
        }

        int origWidth  = original.getWidth();
        int origHeight = original.getHeight();

        if (origWidth <= maxWidth && origHeight <= maxHeight) {
            return originalBytes;
        }

        double scale = Math.min((double) maxWidth / origWidth, (double) maxHeight / origHeight);
        int newWidth  = (int) (origWidth  * scale);
        int newHeight = (int) (origHeight * scale);

        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.85f);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(resized, null, null), param);
        }
        writer.dispose();

        log.debug("Image resized from {}x{} to {}x{}", origWidth, origHeight, newWidth, newHeight);
        return out.toByteArray();
    }
}
