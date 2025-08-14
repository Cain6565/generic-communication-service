package org.argela.genericcommunicationservice.repository;

import org.argela.genericcommunicationservice.entity.RabbitMQBrokerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RabbitMQBrokerRepository extends JpaRepository<RabbitMQBrokerEntity, Long> {

    // Broker key ile aktif broker bulma
    Optional<RabbitMQBrokerEntity> findByBrokerKeyAndIsActiveTrue(String brokerKey);

    // Broker key ile bulma (aktif/pasif fark etmez)
    Optional<RabbitMQBrokerEntity> findByBrokerKey(String brokerKey);

    // Tüm aktif broker'ları listeleme
    List<RabbitMQBrokerEntity> findByIsActiveTrueOrderByBrokerKey();

    // Primary broker bulma
    Optional<RabbitMQBrokerEntity> findByIsPrimaryTrueAndIsActiveTrue();

    // Docker ile yönetilen broker'ları listeleme
    List<RabbitMQBrokerEntity> findByIsDockerManagedTrueAndIsActiveTrue();

    // Container ID ile broker bulma
    Optional<RabbitMQBrokerEntity> findByDockerContainerIdAndIsActiveTrue(String dockerContainerId);

    // Container name ile broker bulma
    Optional<RabbitMQBrokerEntity> findByDockerContainerNameAndIsActiveTrue(String dockerContainerName);

    // Broker var mı kontrolü
    boolean existsByBrokerKeyAndIsActiveTrue(String brokerKey);

    // RabbitMQ broker'ları için özel query'ler
    @Query("SELECT b FROM RabbitMQBrokerEntity b WHERE b.isActive = true ORDER BY b.brokerKey")
    List<RabbitMQBrokerEntity> findActiveRabbitMQBrokers();

    // Broker istatistikleri
    @Query("SELECT COUNT(b) FROM RabbitMQBrokerEntity b WHERE b.isActive = true")
    Long countActiveBrokers();
}