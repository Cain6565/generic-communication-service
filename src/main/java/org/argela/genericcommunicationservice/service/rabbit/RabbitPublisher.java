package org.argela.genericcommunicationservice.service.rabbit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.argela.genericcommunicationservice.dto.RabbitSendDto;
import org.argela.genericcommunicationservice.entity.RabbitMQBrokerEntity;
import org.argela.genericcommunicationservice.service.RabbitMQBrokerService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Sadeleştirilmiş RabbitMQ publisher
 * Database'den broker bilgisini alır, just-in-time connection oluşturur
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitPublisher {

    private final RabbitMQBrokerService rabbitMQBrokerService;

    /**
     * Mesajı belirtilen broker'a gönderir
     * Database'den broker bilgisini alarak just-in-time connection yapar
     */
    public RabbitSendResult publish(RabbitSendDto dto) {
        log.info("📤 RabbitMQ mesajı gönderiliyor: broker={}, queue={}", dto.getBroker(), dto.getQueue());

        try {
            // 1. Database'den broker bilgisini al
            RabbitMQBrokerEntity broker = rabbitMQBrokerService.findActiveBrokerByKey(dto.getBroker());
            log.debug("✅ RabbitMQ broker bulundu: {}:{}", broker.getHost(), broker.getPort());

            // 2. Just-in-time RabbitTemplate oluştur
            RabbitTemplate template = rabbitMQBrokerService.createRabbitTemplate(broker);

            // ✅ ÇÖZÜM: Debug için message converter kontrol et
            log.debug("🔧 RabbitTemplate message converter: {}",
                    template.getMessageConverter().getClass().getSimpleName());

            // 3. Mesajı gönder - DTO objesini tam olarak gönder
            if (dto.getExchange() != null && !dto.getExchange().trim().isEmpty()) {
                // Exchange + routing key ile gönder
                String routingKey = dto.getRoutingKey() != null ? dto.getRoutingKey() : "";
                template.convertAndSend(dto.getExchange(), routingKey, dto);
                log.debug("✅ Exchange'e gönderildi: {} -> {}/{}", dto.getBroker(), dto.getExchange(), routingKey);
            } else if (dto.getQueue() != null && !dto.getQueue().trim().isEmpty()) {
                // ✅ MEVCUT YAPI: Tüm DTO'yu gönder (B seçeneği)
                template.convertAndSend(dto.getQueue(), dto);
                log.debug("✅ Queue'ya gönderildi: {} -> {} (DTO object)", dto.getBroker(), dto.getQueue());
            } else {
                return RabbitSendResult.failure("Exchange veya queue belirtilmeli");
            }

            // 4. Broker health durumunu güncelle
            rabbitMQBrokerService.updateBrokerHealth(dto.getBroker(), RabbitMQBrokerEntity.HealthStatus.ONLINE);

            log.info("✅ RabbitMQ mesajı başarıyla gönderildi: broker={}, queue={}", dto.getBroker(), dto.getQueue());
            return RabbitSendResult.success();

        } catch (RabbitMQBrokerService.BrokerNotFoundException e) {
            log.error("❌ RabbitMQ broker bulunamadı: {}", e.getMessage());
            return RabbitSendResult.failure(e.getMessage());

        } catch (Exception e) {
            log.error("❌ RabbitMQ mesaj gönderme hatası: broker={}, error={}", dto.getBroker(), e.getMessage(), e);

            // Broker health durumunu güncelle (eğer broker bulunduysa)
            try {
                rabbitMQBrokerService.updateBrokerHealth(dto.getBroker(), RabbitMQBrokerEntity.HealthStatus.ERROR);
            } catch (Exception ignored) {
                // Broker bulunamadıysa ignore et
            }

            return RabbitSendResult.failure("RabbitMQ gönderim hatası: " + e.getMessage());
        }
    }

    /**
     * Broker durumu kontrol et
     */
    public boolean isBrokerAvailable(String brokerKey) {
        try {
            RabbitMQBrokerEntity broker = rabbitMQBrokerService.findActiveBrokerByKey(brokerKey);
            boolean available = rabbitMQBrokerService.testBrokerConnection(broker);

            // Health durumunu güncelle
            RabbitMQBrokerEntity.HealthStatus status = available ?
                    RabbitMQBrokerEntity.HealthStatus.ONLINE : RabbitMQBrokerEntity.HealthStatus.OFFLINE;
            rabbitMQBrokerService.updateBrokerHealth(brokerKey, status);

            return available;
        } catch (Exception e) {
            log.debug("⚠️ RabbitMQ broker erişilemez: {} -> {}", brokerKey, e.getMessage());
            return false;
        }
    }

    /**
     * RabbitMQ gönderim sonucu
     */
    public static class RabbitSendResult {
        private final boolean success;
        private final String errorMessage;

        private RabbitSendResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public static RabbitSendResult success() {
            return new RabbitSendResult(true, null);
        }

        public static RabbitSendResult failure(String errorMessage) {
            return new RabbitSendResult(false, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }
}