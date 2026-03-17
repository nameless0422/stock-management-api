package com.stockmanagement.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.Base64;

/**
 * Configuration for TossPayments API integration.
 *
 * <p>Reads {@code toss.*} properties and exposes a pre-configured {@link RestClient} bean
 * with Base64-encoded Basic Auth header applied to every request.
 *
 * <p>TossPayments Basic Auth format: {@code Base64({secretKey}:)}
 * — the secret key is followed by a colon with an empty password field.
 */
@Configuration
@ConfigurationProperties(prefix = "toss")
@Getter
@Setter
public class TossPaymentsConfig {

    /** Secret key for server-to-server API calls (keep secret – never expose to clients). */
    private String secretKey;

    /** Client key for browser-side checkout widget initialization (safe to expose). */
    private String clientKey;

    /** TossPayments Core API base URL. */
    private String baseUrl = "https://api.tosspayments.com/v1";

    /** Connection timeout (ms). */
    private int connectTimeoutMs = 5000;

    /** Read timeout (ms). */
    private int readTimeoutMs = 30000;

    /**
     * Pre-configured {@link RestClient} bean for TossPayments API calls.
     * Applies Basic Auth, Content-Type headers, and HTTP timeouts.
     */
    @Bean
    public RestClient tossRestClient() {
        String credentials = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes());

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);

        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Basic " + credentials)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
