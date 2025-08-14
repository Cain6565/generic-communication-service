package org.argela.genericcommunicationservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

/**
 * Basit CORS yapılandırması.
 * app.cors.allowed-origins: "*" veya "http://localhost:3000,http://127.0.0.1:5500" gibi virgüllü liste.
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration cfg = new CorsConfiguration();
        if ("*".equals(allowedOrigins)) {
            cfg.addAllowedOriginPattern("*");
        } else {
            Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .forEach(cfg::addAllowedOrigin);
        }
        cfg.setAllowCredentials(false);
        cfg.addAllowedHeader("*");
        cfg.setMaxAge(3600L);
        cfg.setAllowedMethods(Arrays.asList("GET","POST","PATCH","DELETE","OPTIONS"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return new CorsFilter(source);
    }
}
