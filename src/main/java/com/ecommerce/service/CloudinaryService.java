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
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class CloudinaryService {

    @Value("${app.cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${app.cloudinary.api-key:}")
    private String apiKey;

    @Value("${app.cloudinary.api-secret:}")
    private String apiSecret;

    @Value("${app.cloudinary.image.product-max-width:1200}")
    private int productMaxWidth;

    @Value("${app.cloudinary.image.product-max-height:1200}")
    private int productMaxHeight;

    @Value("${app.cloudinary.image.banner-max-width:2560}")
    private int bannerMaxWidth;

    @Value("${app.cloudinary.image.banner-max-height:1440}")
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
            options.put("transformation", new Transformation().quality("auto:best"));

            Map result = cloudinary.uploader().upload(bytes, options);
            String url = (String) result.get("secure_url");
            log.info("Image uploaded to Cloudinary [type={}, folder={}]: {}", type, folder, url);
            return url;
        } catch (IOException e) {
            log.error("Cloudinary upload error: {}", e.getMessage());
            throw new RuntimeException("Failed to upload image: " + e.getMessage());
        }
    }

    /**
     * Deletes an image this store uploaded. URLs from other hosts or other Cloudinary accounts
     * are ignored, so seed data or external links are never touched.
     */
    public void delete(String url) {
        if (!isConfigured()) return;
        publicIdOf(url).ifPresent(publicId -> {
            try {
                Map result = cloudinary.uploader().destroy(publicId,
                        ObjectUtils.asMap("resource_type", "image", "invalidate", true));
                log.info("Image deleted from Cloudinary [{}]: {}", publicId, result.get("result"));
            } catch (Exception e) {
                log.error("Cloudinary delete error for {}: {}", publicId, e.getMessage());
            }
        });
    }

    public record StoredImage(String publicId, Instant createdAt) {}

    /** Every image stored under {@code folder} (Cloudinary Admin API, 500 per page). */
    public List<StoredImage> listImages(String folder) throws Exception {
        List<StoredImage> images = new ArrayList<>();
        String cursor = null;
        do {
            Map<String, Object> options = new HashMap<>(ObjectUtils.asMap(
                    "type", "upload", "resource_type", "image", "prefix", folder + "/", "max_results", 500));
            if (cursor != null) options.put("next_cursor", cursor);
            Map response = cloudinary.api().resources(options);
            for (Object o : (List<?>) response.getOrDefault("resources", List.of())) {
                Map<?, ?> r = (Map<?, ?>) o;
                images.add(new StoredImage((String) r.get("public_id"), Instant.parse((String) r.get("created_at"))));
            }
            cursor = (String) response.get("next_cursor");
        } while (cursor != null);
        return images;
    }

    /** Deletes by public id, 100 per request (the Admin API limit). */
    public void deleteByPublicIds(List<String> publicIds) throws Exception {
        for (int i = 0; i < publicIds.size(); i += 100) {
            List<String> chunk = publicIds.subList(i, Math.min(i + 100, publicIds.size()));
            cloudinary.api().deleteResources(chunk, ObjectUtils.asMap("resource_type", "image", "invalidate", true));
        }
    }

    /** "https://res.cloudinary.com/{cloud}/image/upload/v123/products/abc.webp" → "products/abc". */
    public Optional<String> publicIdOf(String url) {
        if (url == null || cloudName == null || cloudName.isBlank()) return Optional.empty();
        String prefix = "https://res.cloudinary.com/" + cloudName + "/image/upload/";
        if (!url.startsWith(prefix)) return Optional.empty();
        String path = url.substring(prefix.length());
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        Matcher version = Pattern.compile("(?:^|/)v\\d+/").matcher(path);
        if (version.find()) path = path.substring(version.end());
        int dot = path.lastIndexOf('.');
        if (dot > path.lastIndexOf('/')) path = path.substring(0, dot);
        return path.isBlank() ? Optional.empty() : Optional.of(path);
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
        int targetWidth  = (int) (origWidth  * scale);
        int targetHeight = (int) (origHeight * scale);

        BufferedImage resized = progressiveDownscale(original, targetWidth, targetHeight);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(resized, "png", out);

        log.debug("Image resized from {}x{} to {}x{}", origWidth, origHeight, targetWidth, targetHeight);
        return out.toByteArray();
    }

    private BufferedImage progressiveDownscale(BufferedImage src, int targetWidth, int targetHeight) {
        int currentWidth  = src.getWidth();
        int currentHeight = src.getHeight();
        BufferedImage current = src;

        while (currentWidth > targetWidth * 2 || currentHeight > targetHeight * 2) {
            currentWidth  = Math.max(currentWidth  / 2, targetWidth);
            currentHeight = Math.max(currentHeight / 2, targetHeight);
            current = scaleStep(current, currentWidth, currentHeight);
        }

        return scaleStep(current, targetWidth, targetHeight);
    }

    private BufferedImage scaleStep(BufferedImage src, int width, int height) {
        int type = src.getTransparency() == Transparency.OPAQUE
                ? BufferedImage.TYPE_INT_RGB
                : BufferedImage.TYPE_INT_ARGB;
        BufferedImage result = new BufferedImage(width, height, type);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, width, height, null);
        g.dispose();
        return result;
    }
}
