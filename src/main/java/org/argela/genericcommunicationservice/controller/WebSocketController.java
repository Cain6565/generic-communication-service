package org.argela.genericcommunicationservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.argela.genericcommunicationservice.dto.WebSocketSendDto;
import org.argela.genericcommunicationservice.entity.MessageEntity;
import org.argela.genericcommunicationservice.enums.MessageStatus;
import org.argela.genericcommunicationservice.enums.ProtocolType;
import org.argela.genericcommunicationservice.service.MessageService;
import org.argela.genericcommunicationservice.service.websocket.WebSocketBrokerService;
import org.argela.genericcommunicationservice.service.websocket.WebSocketSender;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/websocket")
@Tag(name = "WebSocket Messages", description = "🌐 WebSocket/STOMP Mesaj Yönetimi")
@RequiredArgsConstructor
public class WebSocketController {

    private final MessageService messageService;
    private final WebSocketBrokerService webSocketBrokerService;
    private final WebSocketSender webSocketSender;

    @PostMapping("/publish")
    @Operation(summary = "🚀 WebSocket mesajı gönder",
            description = "STOMP protokolü ile WebSocket mesajı gönderir. Topic veya user-specific mesajları destekler.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "WebSocket mesaj formatı",
                    content = @Content(
                            examples = {
                                    @ExampleObject(
                                            name = "Topic Broadcast",
                                            value = """
                                   {
                                     "broker": "websocket-local",
                                     "destination": "/topic/notifications",
                                     "headers": {
                                       "message-type": "system-notification",
                                       "priority": "high"
                                     },
                                     "payload": "{\\"type\\": \\"broadcast\\", \\"message\\": \\"Sistem bakımı 5 dakika sonra başlayacak\\", \\"timestamp\\": \\"2024-01-01T12:00:00Z\\"}",
                                     "sender": "websocket-client-1",
                                     "groupId": "system-notifications",
                                     "messageType": "broadcast"
                                   }
                                   """
                                    ),
                                    @ExampleObject(
                                            name = "User-specific Message",
                                            value = """
                                   {
                                     "broker": "websocket-local",
                                     "destination": "/user/notifications",
                                     "headers": {
                                       "userId": "123",
                                       "message-type": "personal-notification"
                                     },
                                     "payload": "{\\"type\\": \\"personal\\", \\"message\\": \\"Yeni mesajınız var!\\", \\"data\\": {\\"messageId\\": 456}}",
                                     "sender": "notification-service",
                                     "messageType": "user-specific"
                                   }
                                   """
                                    ),
                                    @ExampleObject(
                                            name = "Chat Message",
                                            value = """
                                   {
                                     "broker": "websocket-local",
                                     "destination": "/topic/chat/room-1",
                                     "headers": {
                                       "chat-room": "room-1",
                                       "message-type": "chat"
                                     },
                                     "payload": "{\\"userId\\": 123, \\"username\\": \\"john_doe\\", \\"message\\": \\"Merhaba herkese!\\", \\"timestamp\\": \\"2024-01-01T12:00:00Z\\"}",
                                     "sender": "chat-client",
                                     "groupId": "chat-room-1",
                                     "messageType": "topic"
                                   }
                                   """
                                    )
                            }
                    )
            ))
    public ResponseEntity<MessageEntity> publish(@Valid @RequestBody WebSocketSendDto dto) {

        // Default broker
        String brokerKey = dto.getBroker() != null ? dto.getBroker() : "websocket-local";
        dto.setBroker(brokerKey);

        // 1️⃣ İlk kayıt - QUEUED status ile
        MessageEntity savedMessage = messageService.saveWebSocketMessage(dto, MessageStatus.QUEUED);

        // 2️⃣ WebSocket'e gönder
        WebSocketSender.WebSocketSendResult result = webSocketSender.send(dto);

        // 3️⃣ Başarısızsa durumu güncelle
        if (!result.isSuccess()) {
            MessageEntity updatedMessage = messageService.updateMessageStatus(
                    savedMessage.getId(), MessageStatus.FAILED);

            String originalBody = updatedMessage.getBody() != null ? updatedMessage.getBody() : "";
            String errorDetails = String.format("%s\n\n❌ WEBSOCKET ERROR: %s",
                    originalBody, result.getErrorMessage());
            updatedMessage.setBody(errorDetails);
            messageService.updateMessage(updatedMessage);

            return ResponseEntity.ok(updatedMessage);
        }

        // 4️⃣ Başarılı ise DELIVERED olarak güncelle
        MessageEntity deliveredMessage = messageService.updateMessageStatus(savedMessage.getId(), MessageStatus.DELIVERED);

        return ResponseEntity.ok(deliveredMessage);
    }

    @GetMapping("/messages")
    @Operation(summary = "📋 WebSocket mesajlarını listele")
    public ResponseEntity<Page<MessageEntity>> list(
            @Parameter(hidden = true)
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(messageService.getMessagesByProtocol(ProtocolType.WEBSOCKET.name(), pageable));
    }

    // === 🏗️ BROKER YÖNETİMİ ===

    @GetMapping("/brokers")
    @Operation(summary = "📋 WebSocket broker'larını listele",
            description = "Database'deki tüm aktif WebSocket broker'larını listeler")
    public ResponseEntity<Map<String, Object>> listBrokers() {
        try {
            Map<String, Object> result = Map.of(
                    "brokers", webSocketBrokerService.listActiveBrokers().stream()
                            .map(broker -> Map.of(
                                    "brokerKey", broker.getBrokerKey(),
                                    "endpointUrl", broker.getEndpointUrl(),
                                    "protocolType", broker.getProtocolType(),
                                    "maxConnections", broker.getMaxConnections(),
                                    "isPrimary", broker.getIsPrimary(),
                                    "healthStatus", broker.getHealthStatus().name(),
                                    "lastHealthCheck", broker.getLastHealthCheck()
                            )).toList(),
                    "statistics", webSocketBrokerService.getBrokerStatistics()
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("❌ WebSocket broker listeleme hatası: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "error", "WebSocket broker listeleme hatası: " + e.getMessage(),
                    "brokers", Map.of(),
                    "statistics", Map.of("totalBrokers", 0)
            ));
        }
    }

    @GetMapping("/brokers/{brokerKey}/status")
    @Operation(summary = "🔍 WebSocket broker durumu kontrol et")
    public ResponseEntity<Map<String, Object>> getBrokerStatus(@PathVariable String brokerKey) {
        try {
            webSocketBrokerService.findActiveBrokerByKey(brokerKey);

            return ResponseEntity.ok(Map.of(
                    "brokerKey", brokerKey,
                    "available", true,
                    "isPrimary", "websocket-local".equals(brokerKey),
                    "timestamp", java.time.Instant.now(),
                    "status", "ONLINE",
                    "protocol", "WebSocket/STOMP"
            ));

        } catch (WebSocketBrokerService.BrokerNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/brokers/available")
    @Operation(summary = "📝 Kullanılabilir WebSocket broker listesi")
    public ResponseEntity<Map<String, Object>> getAvailableBrokers() {
        return ResponseEntity.ok(Map.of(
                "availableBrokers", webSocketBrokerService.getAvailableBrokerKeys(),
                "defaultBroker", "websocket-local",
                "total", webSocketBrokerService.getAvailableBrokerKeys().size(),
                "protocol", "WebSocket/STOMP"
        ));
    }

    @GetMapping("/brokers/stats")
    @Operation(summary = "📊 WebSocket broker istatistikleri")
    public ResponseEntity<Map<String, Object>> getBrokerStats() {
        return ResponseEntity.ok(webSocketBrokerService.getBrokerStatistics());
    }
}