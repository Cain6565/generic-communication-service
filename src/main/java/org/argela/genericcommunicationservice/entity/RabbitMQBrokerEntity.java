package org.argela.genericcommunicationservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * RabbitMQ Broker entity - Database'de RabbitMQ broker'larını tutmak için
 */
@Entity
@Table(name = "rabbitmq_brokers")
@Getter
@Setter
public class RabbitMQBrokerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "broker_key", unique = true, nullable = false, length = 50)
    private String brokerKey;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private Integer port = 5672;

    @Column(length = 100)
    private String username = "guest";

    @Column(length = 255)
    private String password = "guest";

    @Column(name = "virtual_host", length = 100)
    private String virtualHost = "/";

    @Column(name = "is_primary")
    private Boolean isPrimary = false;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "is_docker_managed")
    private Boolean isDockerManaged = false;

    @Column(name = "docker_container_id", length = 100)
    private String dockerContainerId;

    @Column(name = "docker_container_name", length = 100)
    private String dockerContainerName;

    @Column(name = "management_port")
    private Integer managementPort = 15672;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "connection_params", columnDefinition = "jsonb")
    private Map<String, Object> connectionParams;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "last_health_check")
    private Instant lastHealthCheck;

    @Enumerated(EnumType.STRING)
    @Column(name = "health_status", length = 20)
    private HealthStatus healthStatus = HealthStatus.UNKNOWN;

    // Sağlık durumları
    public enum HealthStatus {
        ONLINE, OFFLINE, ERROR, UNKNOWN
    }
}