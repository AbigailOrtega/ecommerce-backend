package com.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class EcommerceApplication {

    /**
     * The store's time zone. LocalDate.now() decides when a coupon or promotion starts and ends, and
     * every LocalDateTime column holds Mexico City wall-clock time (see V3__timestamps_to_mexico_city).
     * Set here rather than trusting the server's zone, which is UTC on Render.
     */
    public static final String STORE_TIME_ZONE = "America/Mexico_City";

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(System.getenv().getOrDefault("APP_TIME_ZONE", STORE_TIME_ZONE)));
        SpringApplication.run(EcommerceApplication.class, args);
    }
}
