-- WebSocket Broker yönetimi için tablo
CREATE TABLE websocket (
                           id SERIAL PRIMARY KEY,
                           key VARCHAR(50) UNIQUE NOT NULL,
                           endpoint_url VARCHAR(255) NOT NULL,           -- ws://localhost:8080/ws
                           protocol_type VARCHAR(20) DEFAULT 'STOMP',    -- STOMP, SockJS, Raw
                           max_connections INTEGER DEFAULT 1000,
                           heartbeat_interval INTEGER DEFAULT 60000,
                           is_primary BOOLEAN DEFAULT FALSE,
                           is_active BOOLEAN DEFAULT TRUE,
                           connection_params JSONB,
                           created_at TIMESTAMP DEFAULT NOW(),
                           updated_at TIMESTAMP DEFAULT NOW(),
                           last_health_check TIMESTAMP,
                           health_status VARCHAR(20) DEFAULT 'UNKNOWN'
);

-- Indexes for performance
CREATE INDEX idx_websocket_active ON websocket(is_active);
CREATE INDEX idx_websocket_primary ON websocket(is_primary);
CREATE INDEX idx_websocket_key ON websocket(key);

-- Default primary WebSocket broker
INSERT INTO websocket (key, endpoint_url, protocol_type, is_primary, is_active)
VALUES ('websocket-local', 'ws://localhost:8080/ws', 'STOMP', true, true);