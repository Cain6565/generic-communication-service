package org.argela.genericcommunicationservice.repository;

import org.argela.genericcommunicationservice.entity.WebSocketEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface WebSocketRepository extends JpaRepository<WebSocketEntity, Long> {

    // Key ile aktif websocket bulma
    Optional<WebSocketEntity> findByKeyAndIsActiveTrue(String key);

    // Key ile bulma (aktif/pasif fark etmez)
    Optional<WebSocketEntity> findByKey(String key);

    // Tüm aktif websocket'leri listeleme
    List<WebSocketEntity> findByIsActiveTrueOrderByKey();

    // Primary websocket bulma
    Optional<WebSocketEntity> findByIsPrimaryTrueAndIsActiveTrue();

    // WebSocket var mı kontrolü
    boolean existsByKeyAndIsActiveTrue(String key);

    // WebSocket'ler için özel query'ler
    @Query("SELECT w FROM WebSocketEntity w WHERE w.isActive = true ORDER BY w.key")
    List<WebSocketEntity> findActiveWebSockets();

    // WebSocket istatistikleri
    @Query("SELECT COUNT(w) FROM WebSocketEntity w WHERE w.isActive = true")
    Long countActiveWebSockets();
}