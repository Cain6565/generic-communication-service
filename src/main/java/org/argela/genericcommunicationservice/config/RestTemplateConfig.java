package org.argela.genericcommunicationservice.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate konfigürasyonu.
 * HTTP çağrıları için timeout ve diğer ayarları içerir.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(30))      // ✅ Yeni API
                .readTimeout(Duration.ofSeconds(60))         // ✅ Yeni API
                .build();
    }
}