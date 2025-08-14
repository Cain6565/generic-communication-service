package org.argela.genericcommunicationservice.service.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.argela.genericcommunicationservice.entity.WebSocketBrokerEntity;
import org.argela.genericcommunicationservice.repository.WebSocketBrokerRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * WebSocket broker yönetimi servisi
 * Database CRUD + Health monitoring
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketBrokerService {

    private final WebSocketBrokerRepository webSocketBrokerRepository;

    // =============== DATABASE OPERATIONS ===============

    /**
     * Broker key ile aktif broker bulma
     */
    public WebSocketBrokerEntity findActiveBrokerByKey(String brokerKey) {
        log.debug("🔍 WebSocket broker aranıyor: {}", brokerKey);

        return webSocketBrokerRepository.findByBrokerKeyAndIsActiveTrue(brokerKey)
                .orElseThrow(() -> {
                    List<String> availableBrokers = getAvailableBrokerKeys();
                    log.error("❌ WebSocket broker bulunamadı: {}. Mevcut broker'lar: {}", brokerKey, availableBrokers);
                    return new BrokerNotFoundException(
                            "WebSocket broker bulunamadı: " + brokerKey +
                                    ". Mevcut broker'lar: " + String.join(", ", availableBrokers)
                    );
                });
    }

    /**
     * Broker kaydetme
     */
    public WebSocketBrokerEntity save(WebSocketBrokerEntity broker) {
        log.info("💾 WebSocket broker kaydediliyor: {}", broker.getBrokerKey());
        return webSocketBrokerRepository.save(broker);
    }

    /**
     * Tüm aktif broker'ları listeleme
     */
    public List<WebSocketBrokerEntity> listActiveBrokers() {
        return webSocketBrokerRepository.findByIsActiveTrueOrderByBrokerKey();
    }

    /**
     * Mevcut broker key'lerini string olarak alma
     */
    public List<String> getAvailableBrokerKeys() {
        return listActiveBrokers().stream()
                .map(WebSocketBrokerEntity::getBrokerKey)
                .toList();
    }

    /**
     * Broker var mı kontrolü
     */
    public boolean brokerExists(String brokerKey) {
        return webSocketBrokerRepository.existsByBrokerKeyAndIsActiveTrue(brokerKey);
    }

    /**
     * Broker health durumu güncelleme
     */
    public void updateBrokerHealth(String brokerKey, WebSocketBrokerEntity.HealthStatus status) {
        webSocketBrokerRepository.findByBrokerKey(brokerKey).ifPresent(broker -> {
            broker.setHealthStatus(status);
            broker.setLastHealthCheck(Instant.now());
            save(broker);
        });
    }

    // =============== STATISTICS ===============

    /**
     * WebSocket broker istatistikleri
     */
    public Map<String, Object> getBrokerStatistics() {
        Long totalCount = webSocketBrokerRepository.countActiveBrokers();

        return Map.of(
                "totalBrokers", totalCount != null ? totalCount : 0L,
                "onlineBrokers", listActiveBrokers().stream()
                        .filter(broker -> broker.getHealthStatus() == WebSocketBrokerEntity.HealthStatus.ONLINE)
                        .count()
        );
    }

    /**
     * Debug için tüm broker'ları getir (aktif + pasif)
     */
    public List<WebSocketBrokerEntity> getAllBrokers() {
        return webSocketBrokerRepository.findAll();
    }

    /**
     * Primary broker bulma
     */
    public WebSocketBrokerEntity findPrimaryBroker() {
        return webSocketBrokerRepository.findByIsPrimaryTrueAndIsActiveTrue()
                .orElseThrow(() -> new BrokerNotFoundException("Primary WebSocket broker bulunamadı"));
    }

    // Custom Exception
    public static class BrokerNotFoundException extends RuntimeException {
        public BrokerNotFoundException(String message) {
            super(message);
        }
    }
}