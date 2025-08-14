package org.argela.genericcommunicationservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket ve STOMP protokol konfigürasyonu.
 *
 * Endpoints:
 * - /ws: WebSocket connection endpoint
 * - /topic/*: Broadcast mesajları
 * - /user/*: User-specific mesajları
 * - /app/*: Client-to-server mesajları
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Simple in-memory message broker kullan
        // Prodüksiyon için RabbitMQ veya Redis kullanılabilir
        config.enableSimpleBroker("/topic", "/user");

        // Client-to-server mesajları için prefix
        config.setApplicationDestinationPrefixes("/app");

        // User-specific mesajlar için prefix
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket endpoint'i kaydet
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")  // CORS - prodüksiyon için kısıtla
                .withSockJS();  // SockJS fallback desteği

        // Raw WebSocket (SockJS olmadan)
        registry.addEndpoint("/websocket")
                .setAllowedOriginPatterns("*");
    }
}