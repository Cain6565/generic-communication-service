package org.argela.genericcommunicationservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.argela.genericcommunicationservice.dto.RabbitMQBrokerConfigDto;
import org.argela.genericcommunicationservice.dto.RabbitSendDto;
import org.argela.genericcommunicationservice.entity.MessageEntity;
import org.argela.genericcommunicationservice.enums.MessageStatus;
import org.argela.genericcommunicationservice.enums.ProtocolType;
import org.argela.genericcommunicationservice.service.MessageService;
import org.argela.genericcommunicationservice.service.RabbitMQBrokerService;
import org.argela.genericcommunicationservice.service.rabbit.RabbitPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/rabbitmq")
@Tag(name = "RabbitMQ Messages", description = "🐰 Database Tabanlı RabbitMQ Broker Yönetimi")
@RequiredArgsConstructor
public class RabbitMQController {

    private final MessageService messageService;
    private final RabbitMQBrokerService rabbitMQBrokerService;  // ✅ Tek service
    private final RabbitPublisher rabbitPublisher;

    @PostMapping("/publish")
    @Operation(summary = "🚀 RabbitMQ mesajı gönder",
            description = "Database'den broker bilgisini alarak mesaj gönderir. Default: 'rabbitmq-local'",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "RabbitMQ mesaj formatı",
                    content = @Content(
                            examples = {
                                    @ExampleObject(
                                            name = "Primary broker (localhost:5672)",
                                            value = """
                                   {
                                     "broker": "rabbitmq-local",
                                     "queue": "notifications",
                                     "payload": "{\\"userId\\": 123, \\"message\\": \\"Hello World\\"}",
                                     "sender": "api-client-1",
                                     "groupId": "notification-group"
                                   }
                                   """
                                    ),
                                    @ExampleObject(
                                            name = "Docker broker",
                                            value = """
                                   {
                                     "broker": "docker-rabbit-1",
                                     "queue": "notifications", 
                                     "payload": "{\\"action\\": \\"notify\\", \\"data\\": \\"test\\"}",
                                     "sender": "docker-client"
                                   }
                                   """
                                    ),
                                    @ExampleObject(
                                            name = "Exchange ile gönderim",
                                            value = """
                                   {
                                     "broker": "rabbitmq-local",
                                     "exchange": "direct-exchange",
                                     "routingKey": "notify.user",
                                     "queue": "user-notifications",
                                     "payload": "{\\"level\\": \\"info\\", \\"message\\": \\"Exchange test\\"}",
                                     "sender": "exchange-client"
                                   }
                                   """
                                    )
                            }
                    )
            ))
    public ResponseEntity<MessageEntity> publish(@Valid @RequestBody RabbitSendDto dto) {

        // Default broker - artık "rabbitmq-local"
        String brokerKey = dto.getBroker() != null ? dto.getBroker() : "rabbitmq-local";
        dto.setBroker(brokerKey);

        // 1️⃣ İlk kayıt - QUEUED status ile
        MessageEntity savedMessage = messageService.saveRabbitMessage(dto, MessageStatus.QUEUED);

        // 2️⃣ Database'den broker'a gönder (yeni sistem)
        RabbitPublisher.RabbitSendResult result = rabbitPublisher.publish(dto);

        // 3️⃣ Başarısızsa durumu güncelle
        if (!result.isSuccess()) {
            MessageEntity updatedMessage = messageService.updateMessageStatus(
                    savedMessage.getId(), MessageStatus.FAILED);

            String originalBody = updatedMessage.getBody() != null ? updatedMessage.getBody() : "";
            String errorDetails = String.format("%s\n\n❌ PUBLISH ERROR: %s",
                    originalBody, result.getErrorMessage());
            updatedMessage.setBody(errorDetails);
            messageService.updateMessage(updatedMessage);

            return ResponseEntity.ok(updatedMessage);
        }

        return ResponseEntity.ok(savedMessage);
    }

    @GetMapping("/messages")
    @Operation(summary = "📋 RabbitMQ mesajlarını listele")
    public ResponseEntity<Page<MessageEntity>> list(
            @Parameter(hidden = true)
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(messageService.getMessagesByProtocol(ProtocolType.RABBITMQ.name(), pageable));
    }

    // === 🏗️ BROKER YÖNETİMİ ===

    @PostMapping("/brokers")
    @Operation(summary = "🐳 Yeni RabbitMQ broker oluştur",
            description = "Database'e broker ekler. autoCreate=true ise Docker container da oluşturur.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            examples = {
                                    @ExampleObject(
                                            name = "Otomatik Docker oluştur",
                                            value = """
                                   {
                                     "brokerKey": "docker-rabbit-1",
                                     "autoCreate": true,
                                     "port": 5673,
                                     "managementPort": 15673,
                                     "username": "admin",
                                     "password": "secret123",
                                     "memoryLimitMb": 512
                                   }
                                   """
                                    ),
                                    @ExampleObject(
                                            name = "Mevcut broker'ı ekle",
                                            value = """
                                   {
                                     "brokerKey": "external-rabbit",
                                     "host": "192.168.1.100",
                                     "port": 5672,
                                     "username": "guest",
                                     "password": "guest",
                                     "autoCreate": false
                                   }
                                   """
                                    )
                            }
                    )
            ))
    public ResponseEntity<?> addBroker(@Valid @RequestBody RabbitMQBrokerConfigDto config) {

        // RabbitMQBrokerService ile oluştur (Docker + Database)
        RabbitMQBrokerService.BrokerCreationResult result = rabbitMQBrokerService.createBroker(config);

        if (result.isSuccess()) {
            return ResponseEntity.ok(Map.of(
                    "message", "RabbitMQ broker başarıyla oluşturuldu",
                    "brokerKey", result.getBroker().getBrokerKey(),
                    "host", result.getBroker().getHost(),
                    "port", result.getBroker().getPort(),
                    "dockerCreated", result.isDockerCreated(),
                    "managementUI", result.isDockerCreated() && result.getManagementPort() != null ?
                            "http://localhost:" + result.getManagementPort() : null,
                    "status", "Database'e kaydedildi ve bağlantı test edildi"
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "RabbitMQ broker oluşturulamadı",
                    "message", result.getErrorMessage(),
                    "brokerKey", config.getBrokerKey()
            ));
        }
    }

    @DeleteMapping("/brokers/{brokerKey}")
    @Operation(summary = "🗑️ RabbitMQ broker sil",
            description = "Database'den broker'ı siler. Docker container varsa onu da siler.")
    public ResponseEntity<?> removeBroker(@PathVariable String brokerKey) {

        // Primary broker koruması
        if ("rabbitmq-local".equals(brokerKey)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Primary RabbitMQ broker silinemez",
                    "brokerKey", brokerKey
            ));
        }

        boolean removed = rabbitMQBrokerService.removeBroker(brokerKey);

        if (removed) {
            return ResponseEntity.ok(Map.of(
                    "message", "RabbitMQ broker başarıyla silindi",
                    "brokerKey", brokerKey,
                    "status", "Database'den silindi ve Docker container temizlendi"
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "RabbitMQ broker silinemedi",
                    "brokerKey", brokerKey
            ));
        }
    }

    @GetMapping("/brokers/debug")
    @Operation(summary = "🐛 Debug - Basit RabbitMQ broker listesi")
    public ResponseEntity<?> debugBrokers() {
        try {
            List<String> brokerKeys = rabbitMQBrokerService.getAvailableBrokerKeys();
            Long totalCount = rabbitMQBrokerService.getBrokerStatistics().get("totalBrokers") != null ?
                    (Long) rabbitMQBrokerService.getBrokerStatistics().get("totalBrokers") : 0L;

            return ResponseEntity.ok(Map.of(
                    "brokerKeys", brokerKeys,
                    "totalCount", totalCount,
                    "status", "success"
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "error", e.getMessage(),
                    "stackTrace", e.getClass().getSimpleName(),
                    "status", "error"
            ));
        }
    }

    @GetMapping("/brokers")
    @Operation(summary = "📋 Tüm RabbitMQ broker'ları listele",
            description = "Database'deki tüm aktif RabbitMQ broker'ları Docker durumları ile birlikte listeler")
    public ResponseEntity<Map<String, Object>> listBrokers() {
        try {
            Map<String, Object> result = rabbitMQBrokerService.listBrokersDetailed();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("❌ RabbitMQ broker listeleme hatası: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "error", "RabbitMQ broker listeleme hatası: " + e.getMessage(),
                    "brokers", Map.of(),
                    "statistics", Map.of("totalBrokers", 0)
            ));
        }
    }

    @GetMapping("/brokers/{brokerKey}/status")
    @Operation(summary = "🔍 RabbitMQ broker durumu kontrol et",
            description = "Database + Docker + Connection durumunu kontrol eder")
    public ResponseEntity<?> getBrokerStatus(@PathVariable String brokerKey) {

        try {
            boolean available = rabbitMQBrokerService.isBrokerAvailable(brokerKey);

            return ResponseEntity.ok(Map.of(
                    "brokerKey", brokerKey,
                    "available", available,
                    "isPrimary", "rabbitmq-local".equals(brokerKey),
                    "timestamp", java.time.Instant.now(),
                    "status", available ? "ONLINE" : "OFFLINE",
                    "source", "Database + Connection Test"
            ));

        } catch (RabbitMQBrokerService.BrokerNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/brokers/stats")
    @Operation(summary = "📊 RabbitMQ broker istatistikleri",
            description = "Database'deki RabbitMQ broker sayıları ve durumları")
    public ResponseEntity<Map<String, Object>> getBrokerStats() {
        return ResponseEntity.ok(rabbitMQBrokerService.getBrokerStatistics());
    }

    @GetMapping("/brokers/available")
    @Operation(summary = "📝 Kullanılabilir RabbitMQ broker listesi",
            description = "Mesaj gönderimi için kullanılabilir RabbitMQ broker key'leri")
    public ResponseEntity<Map<String, Object>> getAvailableBrokers() {
        return ResponseEntity.ok(Map.of(
                "availableBrokers", rabbitMQBrokerService.getAvailableBrokerKeys(),
                "defaultBroker", "rabbitmq-local",
                "total", rabbitMQBrokerService.getAvailableBrokerKeys().size()
        ));
    }
}