package org.argela.genericcommunicationservice.service.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.argela.genericcommunicationservice.entity.WebSocketEntity;
import org.argela.genericcommunicationservice.repository.WebSocketRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * WebSocket yönetimi servisi
 * Database CRUD + Health monitoring
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketService {

    private final WebSocketRepository webSocketRepository;

    // =============== DATABASE OPERATIONS ===============

    /**
     * Key ile aktif websocket bulma
     */
    public WebSocketEntity findActiveByKey(String key) {
        log.debug("🔍 WebSocket aranıyor: {}", key);

        return webSocketRepository.findByKeyAndIsActiveTrue(key)
                .orElseThrow(() -> {
                    List<String> availableKeys = getAvailableKeys();
                    log.error("❌ WebSocket bulunamadı: {}. Mevcut WebSocket'ler: {}", key, availableKeys);
                    return new WebSocketNotFoundException(
                            "WebSocket bulunamadı: " + key +
                                    ". Mevcut WebSocket'ler: " + String.join(", ", availableKeys)
                    );
                });
    }

    /**
     * WebSocket kaydetme
     */
    public WebSocketEntity save(WebSocketEntity webSocket) {
        log.info("💾 WebSocket kaydediliyor: {}", webSocket.getKey());
        return webSocketRepository.save(webSocket);
    }

    /**
     * Tüm aktif websocket'leri listeleme
     */
    public List<WebSocketEntity> listActive() {
        return webSocketRepository.findByIsActiveTrueOrderByKey();
    }

    /**
     * Mevcut websocket key'lerini string olarak alma
     */
    public List<String> getAvailableKeys() {
        return listActive().stream()
                .map(WebSocketEntity::getKey)
                .toList();
    }

    /**
     * WebSocket var mı kontrolü
     */
    public boolean exists(String key) {
        return webSocketRepository.existsByKeyAndIsActiveTrue(key);
    }

    /**
     * WebSocket health durumu güncelleme
     */
    public void updateHealth(String key, WebSocketEntity.HealthStatus status) {
        webSocketRepository.findByKey(key).ifPresent(webSocket -> {
            webSocket.setHealthStatus(status);
            webSocket.setLastHealthCheck(Instant.now());
            save(webSocket);
        });
    }

    // =============== STATISTICS ===============

    /**
     * WebSocket istatistikleri
     */
    public Map<String, Object> getStatistics() {
        Long totalCount = webSocketRepository.countActiveWebSockets();

        return Map.of(
                "totalWebSockets", totalCount != null ? totalCount : 0L,
                "onlineWebSockets", listActive().stream()
                        .filter(ws -> ws.getHealthStatus() == WebSocketEntity.HealthStatus.ONLINE)
                        .count()
        );
    }

    /**
     * Debug için tüm websocket'leri getir (aktif + pasif)
     */
    public List<WebSocketEntity> getAll() {
        return webSocketRepository.findAll();
    }

    /**
     * Primary websocket bulma
     */
    public WebSocketEntity findPrimary() {
        return webSocketRepository.findByIsPrimaryTrueAndIsActiveTrue()
                .orElseThrow(() -> new WebSocketNotFoundException("Primary WebSocket bulunamadı"));
    }

    // Custom Exception
    public static class WebSocketNotFoundException extends RuntimeException {
        public WebSocketNotFoundException(String message) {
            super(message);
        }
    }
}