package org.argela.genericcommunicationservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RabbitMQ mesaj gönderimi için DTO.
 * RabbitMQ terminolojisine uygun parametreler.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "RabbitMQ mesaj gönderme formatı")
public class RabbitSendDto {

    @Schema(
            description = "Hedef RabbitMQ broker key'i",
            example = "primary",
            required = true
    )
    @NotBlank(message = "Broker key boş olamaz")
    private String broker;

    @Schema(
            description = "Hedef queue adı",
            example = "notifications",
            required = true
    )
    @NotBlank(message = "Queue adı boş olamaz")
    private String queue;

    @Schema(
            description = "Exchange adı (boş ise default exchange kullanılır)",
            example = "direct-exchange"
    )
    private String exchange;

    @Schema(
            description = "Routing key (exchange kullanılıyorsa gerekli)",
            example = "notify.user"
    )
    private String routingKey;

    @Schema(
            description = "Gönderilecek mesaj içeriği (JSON string formatında)",
            example = "{\"userId\": 123, \"message\": \"Hello World\", \"timestamp\": \"2024-01-01T12:00:00Z\"}"
    )
    private String payload;

    @Schema(
            description = "Mesajı gönderen kaynak/istemci ID",
            example = "rabbit-client-1"
    )
    private String sender;

    @Schema(
            description = "Opsiyonel grup kimliği",
            example = "notification-group"
    )
    private String groupId;
}