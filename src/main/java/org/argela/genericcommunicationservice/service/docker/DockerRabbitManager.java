package org.argela.genericcommunicationservice.service.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.argela.genericcommunicationservice.dto.RabbitMQBrokerConfigDto;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/**
 * Docker API kullanarak RabbitMQ container'larını yönetir.
 * Windows Docker Desktop ile uyumlu.
 */
@Slf4j
@Service
public class DockerRabbitManager {

    private final DockerClient dockerClient;

    public DockerRabbitManager() {
        try {
            // 🐳 Windows Docker Desktop için otomatik konfigürasyon
            DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .build(); // ✅ Otomatik Docker host detection

            ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .sslConfig(config.getSSLConfig())
                    .maxConnections(100)
                    .connectionTimeout(Duration.ofSeconds(30))
                    .responseTimeout(Duration.ofSeconds(45))
                    .build();

            this.dockerClient = DockerClientImpl.getInstance(config, httpClient);

            // 🧪 Docker bağlantı testi
            dockerClient.pingCmd().exec();
            log.info("✅ Docker API bağlantısı başarılı: {}", config.getDockerHost());

        } catch (Exception e) {
            log.error("❌ Docker API bağlantısı başarısız: {}", e.getMessage());
            throw new RuntimeException("Docker API'ye bağlanılamadı. Docker Desktop çalışıyor mu?", e);
        }
    }

    /**
     * RabbitMQ container'ı oluşturur ve başlatır.
     */
    public DockerContainerResult createRabbitContainer(RabbitMQBrokerConfigDto config) {
        try {
            String containerName = "rabbitmq-" + config.getBrokerKey();

            // 🔍 Aynı isimde container var mı kontrol et
            if (isContainerExists(containerName)) {
                return DockerContainerResult.failure(
                        "RabbitMQ container zaten mevcut: " + containerName + ". Önce mevcut container'ı silin."
                );
            }

            // Port kontrolü ve otomatik atama
            int rabbitPort = config.getPort() != null ? config.getPort() : findAvailablePort(5672);
            int managementPort = config.getManagementPort() != null ?
                    config.getManagementPort() : findAvailablePort(15672);

            // Environment variables hazırla
            List<String> envVars = prepareEnvironmentVariables(config);

            // Port binding
            PortBinding rabbitPortBinding = PortBinding.parse(rabbitPort + ":5672");
            PortBinding mgmtPortBinding = PortBinding.parse(managementPort + ":15672");

            log.info("🐳 RabbitMQ container oluşturuluyor: {} -> Port: {}, Management: {}",
                    containerName, rabbitPort, managementPort);

            // Container oluştur
            CreateContainerResponse container = dockerClient.createContainerCmd(config.getDockerImage())
                    .withName(containerName)
                    .withEnv(envVars)
                    .withExposedPorts(ExposedPort.tcp(5672), ExposedPort.tcp(15672))
                    .withHostConfig(HostConfig.newHostConfig()
                            .withMemory((long) config.getMemoryLimitMb() * 1024 * 1024)
                            .withRestartPolicy(config.isAutoRestart() ?
                                    RestartPolicy.unlessStoppedRestart() : RestartPolicy.noRestart())
                            .withPortBindings(rabbitPortBinding, mgmtPortBinding)
                            .withAutoRemove(false)) // Container'ı otomatik silmesin
                    .exec();

            // Container'ı başlat
            dockerClient.startContainerCmd(container.getId()).exec();

            log.info("✅ RabbitMQ container başarıyla oluşturuldu: {} -> ID: {}",
                    containerName, container.getId().substring(0, 12));

            log.info("🔗 RabbitMQ Management UI: http://localhost:{} ({}:{})",
                    managementPort, config.getUsername(), config.getPassword());

            return DockerContainerResult.success(
                    container.getId(),
                    containerName,
                    "localhost",
                    rabbitPort,
                    managementPort,
                    config.getUsername(),
                    config.getPassword()
            );

        } catch (Exception e) {
            log.error("❌ RabbitMQ container oluşturma hatası: {}", e.getMessage(), e);
            return DockerContainerResult.failure("RabbitMQ container oluşturulamadı: " + e.getMessage());
        }
    }

    /**
     * RabbitMQ container'ı durdur ve sil.
     */
    public boolean removeRabbitContainer(String brokerKey) {
        try {
            String containerName = "rabbitmq-" + brokerKey;

            // Container'ı bul
            List<Container> containers = dockerClient.listContainersCmd()
                    .withShowAll(true)
                    .withNameFilter(Collections.singleton(containerName))
                    .exec();

            if (containers.isEmpty()) {
                log.warn("⚠️ RabbitMQ container bulunamadı: {}", containerName);
                return false;
            }

            String containerId = containers.get(0).getId();
            String state = containers.get(0).getState();

            log.info("🗑️ RabbitMQ container siliniyor: {} ({})", containerName, state);

            // Çalışıyorsa durdur
            if ("running".equalsIgnoreCase(state)) {
                dockerClient.stopContainerCmd(containerId).exec();
                log.info("⏹️ RabbitMQ container durduruldu: {}", containerName);
            }

            // Sil
            dockerClient.removeContainerCmd(containerId).exec();
            log.info("✅ RabbitMQ container silindi: {}", containerName);

            return true;

        } catch (Exception e) {
            log.error("❌ RabbitMQ container silme hatası: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * RabbitMQ container durumunu kontrol et.
     */
    public ContainerStatus getContainerStatus(String brokerKey) {
        try {
            String containerName = "rabbitmq-" + brokerKey;

            List<Container> containers = dockerClient.listContainersCmd()
                    .withShowAll(true)
                    .withNameFilter(Collections.singleton(containerName))
                    .exec();

            if (containers.isEmpty()) {
                return ContainerStatus.NOT_FOUND;
            }

            String state = containers.get(0).getState();
            return switch (state.toLowerCase()) {
                case "running" -> ContainerStatus.RUNNING;
                case "exited" -> ContainerStatus.STOPPED;
                case "created" -> ContainerStatus.CREATED;
                case "restarting" -> ContainerStatus.RUNNING;
                case "paused" -> ContainerStatus.STOPPED;
                default -> ContainerStatus.UNKNOWN;
            };

        } catch (Exception e) {
            log.error("❌ RabbitMQ container durum kontrolü hatası: {}", e.getMessage());
            return ContainerStatus.ERROR;
        }
    }

    /**
     * Container var mı kontrol et.
     */
    private boolean isContainerExists(String containerName) {
        try {
            List<Container> containers = dockerClient.listContainersCmd()
                    .withShowAll(true)
                    .withNameFilter(Collections.singleton(containerName))
                    .exec();
            return !containers.isEmpty();
        } catch (Exception e) {
            log.error("RabbitMQ container varlık kontrolü hatası: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Kullanılabilir port bul.
     */
    private int findAvailablePort(int startPort) {
        // Basit implementasyon - gerçek uygulamada port kontrolü yapılabilir
        return startPort;
    }

    /**
     * Environment variables hazırla.
     */
    private List<String> prepareEnvironmentVariables(RabbitMQBrokerConfigDto config) {
        List<String> envVars = new ArrayList<>();

        // RabbitMQ default credentials
        String username = config.getUsername() != null ? config.getUsername() : "guest";
        String password = config.getPassword() != null ? config.getPassword() : "guest";
        String virtualHost = config.getVirtualHost() != null ? config.getVirtualHost() : "/";

        envVars.add("RABBITMQ_DEFAULT_USER=" + username);
        envVars.add("RABBITMQ_DEFAULT_PASS=" + password);
        envVars.add("RABBITMQ_DEFAULT_VHOST=" + virtualHost);

        // Management plugin otomatik enable
        envVars.add("RABBITMQ_MANAGEMENT_PATH_PREFIX=/");

        log.debug("🔧 RabbitMQ Environment variables: user={}, vhost={}", username, virtualHost);

        return envVars;
    }

    /**
     * Docker container oluşturma sonucu.
     */
    public static class DockerContainerResult {
        private final boolean success;
        private final String containerId;
        private final String containerName;
        private final String host;
        private final int port;
        private final int managementPort;
        private final String username;
        private final String password;
        private final String errorMessage;

        private DockerContainerResult(boolean success, String containerId, String containerName,
                                      String host, int port, int managementPort, String username,
                                      String password, String errorMessage) {
            this.success = success;
            this.containerId = containerId;
            this.containerName = containerName;
            this.host = host;
            this.port = port;
            this.managementPort = managementPort;
            this.username = username;
            this.password = password;
            this.errorMessage = errorMessage;
        }

        public static DockerContainerResult success(String containerId, String containerName,
                                                    String host, int port, int managementPort,
                                                    String username, String password) {
            return new DockerContainerResult(true, containerId, containerName, host, port,
                    managementPort, username, password, null);
        }

        public static DockerContainerResult failure(String errorMessage) {
            return new DockerContainerResult(false, null, null, null, 0, 0, null, null, errorMessage);
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getContainerId() { return containerId; }
        public String getContainerName() { return containerName; }
        public String getHost() { return host; }
        public int getPort() { return port; }
        public int getManagementPort() { return managementPort; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public String getErrorMessage() { return errorMessage; }
    }

    public enum ContainerStatus {
        RUNNING, STOPPED, CREATED, NOT_FOUND, UNKNOWN, ERROR
    }
}