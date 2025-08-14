package org.argela.genericcommunicationservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RabbitMQ broker konfigürasyonu ve otomatik Docker oluşturma DTO'su.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "RabbitMQ broker konfigürasyonu ve Docker otomatik oluşturma")
public class RabbitMQBrokerConfigDto {

    @Schema(
            description = "RabbitMQ broker için unique key (container adı olarak da kullanılır)",
            example = "docker-rabbit-1",
            required = true
    )
    @NotBlank(message = "Broker key boş olamaz")
    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9-_]*$", message = "Broker key sadece harf, rakam, tire ve alt çizgi içerebilir")
    private String brokerKey;

    @Schema(
            description = "RabbitMQ host adresi (manuel konfigürasyon için)",
            example = "localhost"
    )
    private String host;

    @Schema(
            description = "RabbitMQ port numarası",
            example = "5672"
    )
    @Min(value = 1, message = "Port 1'den küçük olamaz")
    @Max(value = 65535, message = "Port 65535'den büyük olamaz")
    private Integer port;

    @Schema(
            description = "RabbitMQ kullanıcı adı",
            example = "admin"
    )
    private String username;

    @Schema(
            description = "RabbitMQ şifresi",
            example = "secret123"
    )
    private String password;

    @Schema(
            description = "RabbitMQ virtual host",
            example = "/",
            defaultValue = "/"
    )
    private String virtualHost = "/";

    @Schema(
            description = "Otomatik Docker container oluşturulsun mu?",
            example = "true",
            defaultValue = "false"
    )
    private boolean autoCreate = false;

    @Schema(
            description = "Docker image versiyonu",
            example = "rabbitmq:3-management",
            defaultValue = "rabbitmq:3-management"
    )
    private String dockerImage = "rabbitmq:3-management";

    @Schema(
            description = "Container memory limiti (MB)",
            example = "512",
            defaultValue = "512"
    )
    @Min(value = 128, message = "Memory en az 128MB olmalı")
    @Max(value = 8192, message = "Memory en fazla 8GB olabilir")
    private Integer memoryLimitMb = 512;

    @Schema(
            description = "Management UI port (Web UI için)",
            example = "15672"
    )
    @Min(value = 1, message = "Management port 1'den küçük olamaz")
    @Max(value = 65535, message = "Management port 65535'den büyük olamaz")
    private Integer managementPort;

    @Schema(
            description = "Container otomatik başlatılsın mı? (restart policy)",
            example = "true",
            defaultValue = "true"
    )
    private boolean autoRestart = true;

    @Schema(
            description = "Ek environment variables (JSON formatında)",
            example = "{\"RABBITMQ_DEFAULT_VHOST\": \"/test\"}"
    )
    private String environmentVariables;
}