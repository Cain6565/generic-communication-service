package org.argela.genericcommunicationservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * WebSocket mesaj gönderimi için DTO.
 * STOMP protokolü terminolojisine uygun parametreler.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "WebSocket mesaj gönderme formatı")
public class WebSocketSendDto {

    @Schema(
            description = "Hedef WebSocket broker key'i",
            example = "websocket-local",
            required = true
    )
    @NotBlank(message = "Broker key boş olamaz")
    private String broker;

    @Schema(
            description = "STOMP destination path'i",
            example = "/topic/notifications",
            required = true
    )
    @NotBlank(message = "Destination boş olamaz")
    private String destination;

    @Schema(
            description = "WebSocket/STOMP headers",
            example = """
                {
                  "message-type": "notification",
                  "priority": "high",
                  "content-type": "application/json",
                  "userId": "123"
                }
                """
    )
    private Map<String, String> headers;

    @Schema(
            description = "Gönderilecek mesaj içeriği (JSON string formatında)",
            example = """
                {
                  "type": "user-notification",
                  "userId": 123,
                  "message": "Yeni mesajınız var!",
                  "timestamp": "2024-01-01T12:00:00Z",
                  "data": {
                    "notificationId": 456,
                    "category": "system"
                  }
                }
                """
    )
    private String payload;

    @Schema(
            description = "Mesajı gönderen kaynak/istemci ID",
            example = "websocket-client-1"
    )
    private String sender;

    @Schema(
            description = "Opsiyonel grup kimliği",
            example = "notification-group"
    )
    private String groupId;

    @Schema(
            description = "Mesaj tipi (broadcast, user-specific, topic)",
            example = "broadcast",
            allowableValues = {"broadcast", "user-specific", "topic"}
    )
    private String messageType;
}