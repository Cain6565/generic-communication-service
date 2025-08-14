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
 * WebSocket Broker entity - Database'de WebSocket broker'larını tutmak için
 */
@Entity
@Table(name = "websocket_brokers")
@Getter
@Setter
public class WebSocketBrokerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "broker_key", unique = true, nullable = false, length = 50)
    private String brokerKey;

    @Column(name = "endpoint_url", nullable = false)
    private String endpointUrl;

    @Column(name = "protocol_type", length = 20)
    private String protocolType = "STOMP";

    @Column(name = "max_connections")
    private Integer maxConnections = 1000;

    @Column(name = "heartbeat_interval")
    private Integer heartbeatInterval = 60000;

    @Column(name = "is_primary")
    private Boolean isPrimary = false;

    @Column(name = "is_active")
    private Boolean isActive = true;

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