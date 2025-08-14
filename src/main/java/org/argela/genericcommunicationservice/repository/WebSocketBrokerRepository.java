package org.argela.genericcommunicationservice.repository;

import org.argela.genericcommunicationservice.entity.WebSocketBrokerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface WebSocketBrokerRepository extends JpaRepository<WebSocketBrokerEntity, Long> {

    // Broker key ile aktif broker bulma
    Optional<WebSocketBrokerEntity> findByBrokerKeyAndIsActiveTrue(String brokerKey);

    // Broker key ile bulma (aktif/pasif fark etmez)
    Optional<WebSocketBrokerEntity> findByBrokerKey(String brokerKey);

    // Tüm aktif broker'ları listeleme
    List<WebSocketBrokerEntity> findByIsActiveTrueOrderByBrokerKey();

    // Primary broker bulma
    Optional<WebSocketBrokerEntity> findByIsPrimaryTrueAndIsActiveTrue();

    // Broker var mı kontrolü
    boolean existsByBrokerKeyAndIsActiveTrue(String brokerKey);

    // WebSocket broker'ları için özel query'ler
    @Query("SELECT b FROM WebSocketBrokerEntity b WHERE b.isActive = true ORDER BY b.brokerKey")
    List<WebSocketBrokerEntity> findActiveWebSocketBrokers();

    // Broker istatistikleri
    @Query("SELECT COUNT(b) FROM WebSocketBrokerEntity b WHERE b.isActive = true")
    Long countActiveBrokers();
}