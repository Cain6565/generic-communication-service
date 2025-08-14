package org.argela.genericcommunicationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.argela.genericcommunicationservice.dto.RabbitMQBrokerConfigDto;
import org.argela.genericcommunicationservice.entity.RabbitMQBrokerEntity;
import org.argela.genericcommunicationservice.repository.RabbitMQBrokerRepository;
import org.argela.genericcommunicationservice.service.docker.DockerRabbitManager;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * RabbitMQ broker yönetimi service'i
 * Database CRUD + Docker işlemleri + Connection yönetimi
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitMQBrokerService {

    private final RabbitMQBrokerRepository rabbitMQBrokerRepository;
    private final DockerRabbitManager dockerManager;
    private final Jackson2JsonMessageConverter messageConverter = new Jackson2JsonMessageConverter();

    // =============== DATABASE OPERATIONS ===============

    /**
     * Broker key ile aktif broker bulma
     */
    public RabbitMQBrokerEntity findActiveBrokerByKey(String brokerKey) {
        log.debug("🔍 RabbitMQ broker aranıyor: {}", brokerKey);

        return rabbitMQBrokerRepository.findByBrokerKeyAndIsActiveTrue(brokerKey)
                .orElseThrow(() -> {
                    List<String> availableBrokers = getAvailableBrokerKeys();
                    log.error("❌ RabbitMQ broker bulunamadı: {}. Mevcut broker'lar: {}", brokerKey, availableBrokers);
                    return new BrokerNotFoundException(
                            "RabbitMQ broker bulunamadı: " + brokerKey + ". Mevcut broker'lar: " + String.join(", ", availableBrokers)
                    );
                });
    }

    /**
     * Broker kaydetme
     */
    public RabbitMQBrokerEntity save(RabbitMQBrokerEntity broker) {
        log.info("💾 RabbitMQ broker kaydediliyor: {}", broker.getBrokerKey());
        return rabbitMQBrokerRepository.save(broker);
    }

    /**
     * Tüm aktif broker'ları listeleme
     */
    public List<RabbitMQBrokerEntity> listActiveBrokers() {
        return rabbitMQBrokerRepository.findByIsActiveTrueOrderByBrokerKey();
    }

    /**
     * Mevcut broker key'lerini string olarak alma
     */
    public List<String> getAvailableBrokerKeys() {
        return listActiveBrokers().stream()
                .map(RabbitMQBrokerEntity::getBrokerKey)
                .toList();
    }

    /**
     * Broker var mı kontrolü
     */
    public boolean brokerExists(String brokerKey) {
        return rabbitMQBrokerRepository.existsByBrokerKeyAndIsActiveTrue(brokerKey);
    }

    /**
     * Broker silme (hard delete veya soft delete)
     */
    public void deactivateBroker(String brokerKey) {
        Optional<RabbitMQBrokerEntity> brokerOpt = rabbitMQBrokerRepository.findByBrokerKey(brokerKey);
        if (brokerOpt.isPresent()) {
            RabbitMQBrokerEntity broker = brokerOpt.get();

            // Docker managed broker'ları tamamen sil, diğerlerini soft delete yap
            if (broker.getIsDockerManaged()) {
                log.info("🗑️ Docker RabbitMQ broker tamamen siliniyor: {}", brokerKey);
                rabbitMQBrokerRepository.delete(broker); // Hard delete
            } else {
                log.info("🔄 Manuel RabbitMQ broker soft delete yapılıyor: {}", brokerKey);
                broker.setIsActive(false); // Soft delete
                save(broker);
            }
        }
    }

    // =============== CONNECTION OPERATIONS ===============

    /**
     * RabbitMQBrokerEntity'den RabbitMQ ConnectionFactory oluşturma
     */
    public CachingConnectionFactory createRabbitConnectionFactory(RabbitMQBrokerEntity broker) {
        log.debug("🔗 RabbitMQ connection factory oluşturuluyor: {}:{}", broker.getHost(), broker.getPort());

        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setHost(broker.getHost());
        factory.setPort(broker.getPort());
        factory.setUsername(broker.getUsername() != null ? broker.getUsername() : "guest");
        factory.setPassword(broker.getPassword() != null ? broker.getPassword() : "guest");
        factory.setVirtualHost(broker.getVirtualHost() != null ? broker.getVirtualHost() : "/");

        // Connection optimization
        factory.setConnectionTimeout(30000);
        factory.setChannelCacheSize(5);
        factory.setChannelCheckoutTimeout(5000);
        factory.setRequestedHeartBeat(60);
        factory.setConnectionNameStrategy(connectionFactory -> "GCS-" + broker.getBrokerKey());

        return factory;
    }

    /**
     * RabbitMQBrokerEntity'den RabbitTemplate oluşturma
     */
    public RabbitTemplate createRabbitTemplate(RabbitMQBrokerEntity broker) {
        CachingConnectionFactory factory = createRabbitConnectionFactory(broker);
        RabbitTemplate template = new RabbitTemplate(factory);
        template.setMessageConverter(messageConverter);
        template.setMandatory(true);

        template.setReturnsCallback(returned -> {
            log.warn("⚠️ RabbitMQ mesaj geri döndü: broker={}, queue={}, reason={}",
                    broker.getBrokerKey(), returned.getRoutingKey(), returned.getReplyText());
        });

        return template;
    }

    /**
     * Broker connection test
     */
    public boolean testBrokerConnection(RabbitMQBrokerEntity broker) {
        try {
            CachingConnectionFactory factory = createRabbitConnectionFactory(broker);
            factory.createConnection().close();
            log.debug("✅ RabbitMQ broker bağlantı testi başarılı: {}", broker.getBrokerKey());
            return true;
        } catch (Exception e) {
            log.error("❌ RabbitMQ broker bağlantı testi başarısız: {} -> {}", broker.getBrokerKey(), e.getMessage());
            return false;
        }
    }

    /**
     * Broker health durumu güncelleme
     */
    public void updateBrokerHealth(String brokerKey, RabbitMQBrokerEntity.HealthStatus status) {
        Optional<RabbitMQBrokerEntity> brokerOpt = rabbitMQBrokerRepository.findByBrokerKey(brokerKey);
        if (brokerOpt.isPresent()) {
            RabbitMQBrokerEntity broker = brokerOpt.get();
            broker.setHealthStatus(status);
            broker.setLastHealthCheck(Instant.now());
            save(broker);
        }
    }

    // =============== DOCKER BROKER MANAGEMENT ===============

    /**
     * Yeni RabbitMQ broker oluşturma (Docker ile veya manuel)
     */
    public BrokerCreationResult createBroker(RabbitMQBrokerConfigDto config) {
        log.info("🏗️ Yeni RabbitMQ broker oluşturuluyor: {}", config.getBrokerKey());

        try {
            // Broker zaten var mı kontrol et
            if (brokerExists(config.getBrokerKey())) {
                return BrokerCreationResult.failure("RabbitMQ broker zaten mevcut: " + config.getBrokerKey());
            }

            String host = config.getHost() != null ? config.getHost() : "localhost";
            int port = config.getPort() != null ? config.getPort() : 5672;
            boolean dockerCreated = false;
            String dockerContainerId = null;
            String dockerContainerName = null;

            // 🐳 Docker otomatik oluşturma
            if (config.isAutoCreate()) {
                log.info("🐳 RabbitMQ Docker container oluşturuluyor: {}", config.getBrokerKey());

                DockerRabbitManager.DockerContainerResult dockerResult =
                        dockerManager.createRabbitContainer(config);

                if (!dockerResult.isSuccess()) {
                    return BrokerCreationResult.failure("RabbitMQ Docker container oluşturulamadı: " + dockerResult.getErrorMessage());
                }

                // ✅ Docker sonuçlarını al
                host = dockerResult.getHost();
                port = dockerResult.getPort();
                dockerCreated = true;
                dockerContainerId = dockerResult.getContainerId();
                dockerContainerName = dockerResult.getContainerName();

                // Docker başlaması için bekle
                log.info("⏳ RabbitMQ Docker container başlaması bekleniyor...");
                Thread.sleep(8000);
            }

            // ✅ Broker entity oluştur ve database'e kaydet
            RabbitMQBrokerEntity broker = new RabbitMQBrokerEntity();
            broker.setBrokerKey(config.getBrokerKey());
            broker.setHost(host);
            broker.setPort(port);
            broker.setUsername(config.getUsername() != null ? config.getUsername() : "guest");
            broker.setPassword(config.getPassword() != null ? config.getPassword() : "guest");
            broker.setVirtualHost(config.getVirtualHost() != null ? config.getVirtualHost() : "/");
            broker.setIsActive(true);
            broker.setIsPrimary(false); // Yeni broker'lar primary değil
            broker.setIsDockerManaged(dockerCreated);
            broker.setDockerContainerId(dockerContainerId);
            broker.setDockerContainerName(dockerContainerName);
            broker.setManagementPort(config.getManagementPort() != null ? config.getManagementPort() : 15672);
            broker.setHealthStatus(RabbitMQBrokerEntity.HealthStatus.UNKNOWN); // Henüz test edilmedi

            // ✅ Database'e kaydet
            log.info("💾 RabbitMQ broker database'e kaydediliyor: {}", config.getBrokerKey());
            RabbitMQBrokerEntity savedBroker = save(broker);

            // ✅ Connection test
            log.info("🧪 RabbitMQ broker bağlantı testi yapılıyor: {}", config.getBrokerKey());
            boolean connectionOk = testBrokerConnection(savedBroker);

            if (connectionOk) {
                updateBrokerHealth(config.getBrokerKey(), RabbitMQBrokerEntity.HealthStatus.ONLINE);
                log.info("✅ RabbitMQ broker başarıyla oluşturuldu ve test edildi: {} -> {}:{}",
                        config.getBrokerKey(), host, port);
            } else {
                updateBrokerHealth(config.getBrokerKey(), RabbitMQBrokerEntity.HealthStatus.OFFLINE);
                log.warn("⚠️ RabbitMQ broker oluşturuldu ama bağlantı testi başarısız: {}", config.getBrokerKey());
            }

            return BrokerCreationResult.success(savedBroker, dockerCreated,
                    config.getManagementPort());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("❌ RabbitMQ broker oluşturma interrupted: {}", config.getBrokerKey());
            return BrokerCreationResult.failure("İşlem kesintiye uğradı");
        } catch (Exception e) {
            log.error("❌ RabbitMQ broker oluşturma hatası: {} -> {}", config.getBrokerKey(), e.getMessage(), e);
            return BrokerCreationResult.failure("RabbitMQ broker oluşturulamadı: " + e.getMessage());
        }
    }

    /**
     * RabbitMQ broker silme
     */
    public boolean removeBroker(String brokerKey) {
        log.info("🗑️ RabbitMQ broker siliniyor: {}", brokerKey);

        try {
            // İlk önce broker'ı database'den bul
            RabbitMQBrokerEntity broker = findActiveBrokerByKey(brokerKey);

            // Primary broker silinemez
            if (broker.getIsPrimary()) {
                log.warn("⚠️ Primary RabbitMQ broker silinemez: {}", brokerKey);
                return false;
            }

            log.info("📋 RabbitMQ broker bilgileri: dockerManaged={}, containerName={}",
                    broker.getIsDockerManaged(), broker.getDockerContainerName());

            // Docker container'ı sil (eğer Docker managed ise)
            if (broker.getIsDockerManaged() && broker.getDockerContainerName() != null) {
                String containerKey = broker.getDockerContainerName().replace("rabbitmq-", "");
                log.info("🐳 RabbitMQ Docker container siliniyor: {} -> {}", brokerKey, containerKey);

                boolean dockerRemoved = dockerManager.removeRabbitContainer(containerKey);
                log.info("🐳 RabbitMQ Docker container silme sonucu: {}", dockerRemoved);

                if (!dockerRemoved) {
                    log.warn("⚠️ RabbitMQ Docker container silinemedi, yine de DB'den silmeye devam ediliyor");
                }
            } else {
                log.info("🔄 Bu RabbitMQ broker Docker managed değil, sadece DB'den siliniyor");
            }

            // Database'den soft delete (mutlaka yapılacak)
            log.info("💾 RabbitMQ broker database'den siliniyor: {}", brokerKey);
            deactivateBroker(brokerKey);

            log.info("✅ RabbitMQ broker başarıyla silindi: {}", brokerKey);
            return true;

        } catch (BrokerNotFoundException e) {
            log.error("❌ RabbitMQ broker bulunamadı: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("❌ RabbitMQ broker silme hatası: {} -> {}", brokerKey, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Tüm RabbitMQ broker'larını detaylı listeleme
     */
    public Map<String, Object> listBrokersDetailed() {
        List<RabbitMQBrokerEntity> brokers = listActiveBrokers();

        Map<String, Object> result = new java.util.HashMap<>();
        Map<String, Object> brokerMap = new java.util.HashMap<>();

        for (RabbitMQBrokerEntity broker : brokers) {
            // Docker durumunu kontrol et
            DockerRabbitManager.ContainerStatus dockerStatus = DockerRabbitManager.ContainerStatus.NOT_FOUND;
            if (broker.getIsDockerManaged() && broker.getDockerContainerName() != null) {
                String brokerKeyForDocker = broker.getDockerContainerName().replace("rabbitmq-", "");
                dockerStatus = dockerManager.getContainerStatus(brokerKeyForDocker);
            }

            Map<String, Object> brokerInfo = Map.of(
                    "brokerKey", broker.getBrokerKey(),
                    "connectionInfo", String.format("%s:%d/%s", broker.getHost(), broker.getPort(), broker.getVirtualHost()),
                    "available", broker.getHealthStatus() == RabbitMQBrokerEntity.HealthStatus.ONLINE,
                    "isPrimary", broker.getIsPrimary(),
                    "dockerManaged", broker.getIsDockerManaged(),
                    "dockerStatus", dockerStatus.name(),
                    "healthStatus", broker.getHealthStatus().name(),
                    "lastHealthCheck", broker.getLastHealthCheck()
            );

            brokerMap.put(broker.getBrokerKey(), brokerInfo);
        }

        result.put("brokers", brokerMap);
        result.put("statistics", getBrokerStatistics());

        return result;
    }

    /**
     * RabbitMQ broker durumu kontrol et
     */
    public boolean isBrokerAvailable(String brokerKey) {
        try {
            RabbitMQBrokerEntity broker = findActiveBrokerByKey(brokerKey);
            boolean available = testBrokerConnection(broker);

            RabbitMQBrokerEntity.HealthStatus status = available ?
                    RabbitMQBrokerEntity.HealthStatus.ONLINE : RabbitMQBrokerEntity.HealthStatus.OFFLINE;
            updateBrokerHealth(brokerKey, status);

            return available;
        } catch (Exception e) {
            log.debug("⚠️ RabbitMQ broker erişilemez: {} -> {}", brokerKey, e.getMessage());
            return false;
        }
    }

    // =============== STATISTICS ===============

    /**
     * RabbitMQ broker istatistikleri
     */
    public Map<String, Object> getBrokerStatistics() {
        Long totalCount = rabbitMQBrokerRepository.countActiveBrokers();
        List<RabbitMQBrokerEntity> dockerManagedBrokers = rabbitMQBrokerRepository.findByIsDockerManagedTrueAndIsActiveTrue();
        Long dockerManagedCount = (long) dockerManagedBrokers.size();

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("totalBrokers", totalCount != null ? totalCount : 0L);
        result.put("dockerManagedBrokers", dockerManagedCount);
        result.put("manualBrokers", (totalCount != null ? totalCount : 0L) - dockerManagedCount);

        return result;
    }

    /**
     * Debug için tüm broker'ları getir (aktif + pasif)
     */
    public List<RabbitMQBrokerEntity> getAllBrokers() {
        return rabbitMQBrokerRepository.findAll();
    }

    /**
     * Primary broker bulma
     */
    public Optional<RabbitMQBrokerEntity> findPrimaryBroker() {
        return rabbitMQBrokerRepository.findByIsPrimaryTrueAndIsActiveTrue();
    }

    // =============== RESULT CLASSES ===============

    /**
     * Broker oluşturma sonucu
     */
    public static class BrokerCreationResult {
        private final boolean success;
        private final RabbitMQBrokerEntity broker;
        private final boolean dockerCreated;
        private final Integer managementPort;
        private final String errorMessage;

        private BrokerCreationResult(boolean success, RabbitMQBrokerEntity broker, boolean dockerCreated,
                                     Integer managementPort, String errorMessage) {
            this.success = success;
            this.broker = broker;
            this.dockerCreated = dockerCreated;
            this.managementPort = managementPort;
            this.errorMessage = errorMessage;
        }

        public static BrokerCreationResult success(RabbitMQBrokerEntity broker, boolean dockerCreated, Integer managementPort) {
            return new BrokerCreationResult(true, broker, dockerCreated, managementPort, null);
        }

        public static BrokerCreationResult failure(String errorMessage) {
            return new BrokerCreationResult(false, null, false, null, errorMessage);
        }

        // Getters
        public boolean isSuccess() { return success; }
        public RabbitMQBrokerEntity getBroker() { return broker; }
        public boolean isDockerCreated() { return dockerCreated; }
        public Integer getManagementPort() { return managementPort; }
        public String getErrorMessage() { return errorMessage; }
    }

    // Custom Exception
    public static class BrokerNotFoundException extends RuntimeException {
        public BrokerNotFoundException(String message) {
            super(message);
        }
    }
}