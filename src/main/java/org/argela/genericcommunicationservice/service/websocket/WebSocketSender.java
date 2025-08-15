package org.argela.genericcommunicationservice.service.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.argela.genericcommunicationservice.dto.WebSocketSendDto;
import org.argela.genericcommunicationservice.entity.WebSocketEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket mesaj gönderimi servisi
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketSender {

    private final SimpMessagingTemplate simpMessagingTemplate;
    private final WebSocketService webSocketService;

    /**
     * WebSocket mesajını gönderir
     */
    public WebSocketSendResult send(WebSocketSendDto dto) {
        log.info("📡 WebSocket mesajı gönderiliyor: websocket={}, destination={}",
                dto.getWebsocket(), dto.getDestination());

        try {
            // 1. WebSocket doğrulama (database'den)
            WebSocketEntity webSocket = webSocketService.findActiveByKey(dto.getWebsocket());
            log.debug("✅ WebSocket bulundu: {}", webSocket.getEndpointUrl());

            // 2. Mesaj tipine göre gönderim
            switch (dto.getMessageType() != null ? dto.getMessageType().toLowerCase() : "broadcast") {
                case "user-specific" -> sendToUser(dto);
                case "topic", "broadcast" -> sendToTopic(dto);
                default -> sendToDestination(dto);
            }

            // 3. WebSocket health durumunu güncelle
            webSocketService.updateHealth(dto.getWebsocket(), WebSocketEntity.HealthStatus.ONLINE);

            log.info("✅ WebSocket mesajı başarıyla gönderildi: destination={}", dto.getDestination());
            return WebSocketSendResult.success();

        } catch (WebSocketService.WebSocketNotFoundException e) {
            log.error("❌ WebSocket bulunamadı: {}", e.getMessage());
            return WebSocketSendResult.failure(e.getMessage());
        } catch (Exception e) {
            log.error("❌ WebSocket mesaj gönderme hatası: destination={}, error={}",
                    dto.getDestination(), e.getMessage(), e);

            // WebSocket health durumunu güncelle
            try {
                webSocketService.updateHealth(dto.getWebsocket(), WebSocketEntity.HealthStatus.ERROR);
            } catch (Exception ignored) {
                // WebSocket bulunamadıysa ignore et
            }

            return WebSocketSendResult.failure("WebSocket gönderim hatası: " + e.getMessage());
        }
    }

    /**
     * Topic'e broadcast mesaj gönder
     */
    private void sendToTopic(WebSocketSendDto dto) {
        // Destination başında /topic yoksa ekle
        String destination = dto.getDestination().startsWith("/topic") ?
                dto.getDestination() : "/topic" + dto.getDestination();

        // Headers'ı message olarak gönder
        Map<String, Object> messageWithHeaders = createMessageWithHeaders(dto);

        simpMessagingTemplate.convertAndSend(destination, messageWithHeaders);
        log.debug("📤 Topic mesajı gönderildi: {}", destination);
    }

    /**
     * Belirli kullanıcıya mesaj gönder
     */
    private void sendToUser(WebSocketSendDto dto) {
        // Headers'dan userId'yi al
        String userId = dto.getHeaders() != null ? dto.getHeaders().get("userId") : null;
        if (userId == null) {
            throw new IllegalArgumentException("User-specific mesaj için userId header'ı gerekli");
        }

        String destination = dto.getDestination().startsWith("/user") ?
                dto.getDestination() : dto.getDestination();

        // Headers'ı message olarak gönder
        Map<String, Object> messageWithHeaders = createMessageWithHeaders(dto);

        simpMessagingTemplate.convertAndSendToUser(userId, destination, messageWithHeaders);
        log.debug("👤 User mesajı gönderildi: userId={}, destination={}", userId, destination);
    }

    /**
     * Raw destination'a mesaj gönder
     */
    private void sendToDestination(WebSocketSendDto dto) {
        // Headers'ı message olarak gönder
        Map<String, Object> messageWithHeaders = createMessageWithHeaders(dto);

        simpMessagingTemplate.convertAndSend(dto.getDestination(), messageWithHeaders);
        log.debug("🎯 Raw mesaj gönderildi: {}", dto.getDestination());
    }

    /**
     * Payload ve headers'ı birleştirip mesaj objesi oluştur
     */
    private Map<String, Object> createMessageWithHeaders(WebSocketSendDto dto) {
        Map<String, Object> message = new HashMap<>();

        // Ana payload'ı parse et
        try {
            if (dto.getPayload() != null && !dto.getPayload().trim().isEmpty()) {
                // JSON string'i object olarak parse etmeye çalış
                // Eğer parse edilemezse raw string olarak ekle
                message.put("payload", dto.getPayload());
            }
        } catch (Exception e) {
            // Parse edilemezse raw string olarak ekle
            message.put("payload", dto.getPayload());
        }

        // Headers'ı ekle
        if (dto.getHeaders() != null && !dto.getHeaders().isEmpty()) {
            message.put("headers", dto.getHeaders());
        }

        // Meta bilgiler ekle
        message.put("sender", dto.getSender());
        message.put("groupId", dto.getGroupId());
        message.put("messageType", dto.getMessageType());
        message.put("timestamp", java.time.Instant.now().toString());

        return message;
    }

    /**
     * WebSocket gönderim sonucu
     */
    public static class WebSocketSendResult {
        private final boolean success;
        private final String errorMessage;

        private WebSocketSendResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public static WebSocketSendResult success() {
            return new WebSocketSendResult(true, null);
        }

        public static WebSocketSendResult failure(String errorMessage) {
            return new WebSocketSendResult(false, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }
}