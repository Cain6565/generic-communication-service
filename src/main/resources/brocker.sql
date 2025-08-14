-- RabbitMQ Brokers tablosu - Sadece RabbitMQ broker'larını tutacağız
CREATE TABLE rabbitmq_brokers (
                                  id SERIAL PRIMARY KEY,
                                  broker_key VARCHAR(50) UNIQUE NOT NULL,
                                  host VARCHAR(255) NOT NULL,
                                  port INTEGER NOT NULL DEFAULT 5672,
                                  username VARCHAR(100) DEFAULT 'guest',
                                  password VARCHAR(255) DEFAULT 'guest',
                                  virtual_host VARCHAR(100) DEFAULT '/',
                                  is_primary BOOLEAN DEFAULT FALSE,
                                  is_active BOOLEAN DEFAULT TRUE,
                                  is_docker_managed BOOLEAN DEFAULT FALSE,
                                  docker_container_id VARCHAR(100),
                                  docker_container_name VARCHAR(100),
                                  management_port INTEGER DEFAULT 15672,
                                  connection_params JSONB,
                                  created_at TIMESTAMP DEFAULT NOW(),
                                  updated_at TIMESTAMP DEFAULT NOW(),
                                  last_health_check TIMESTAMP,
                                  health_status VARCHAR(20) DEFAULT 'UNKNOWN'
);

-- Indexes for performance
CREATE INDEX idx_rabbitmq_brokers_active ON rabbitmq_brokers(is_active);
CREATE INDEX idx_rabbitmq_brokers_primary ON rabbitmq_brokers(is_primary);
CREATE INDEX idx_rabbitmq_brokers_key ON rabbitmq_brokers(broker_key);
CREATE INDEX idx_rabbitmq_brokers_docker ON rabbitmq_brokers(is_docker_managed);

-- Default primary broker ekle
INSERT INTO rabbitmq_brokers (broker_key, host, port, username, password, virtual_host, is_primary, is_active)
VALUES ('rabbitmq-local', 'localhost', 5672, 'guest', 'guest', '/', true, true);

-- Test için ikinci bir broker (opsiyonel)
-- INSERT INTO rabbitmq_brokers (broker_key, host, port, username, password, virtual_host, is_active)
-- VALUES ('rabbitmq-test', 'localhost', 5673, 'guest', 'guest', '/', true);