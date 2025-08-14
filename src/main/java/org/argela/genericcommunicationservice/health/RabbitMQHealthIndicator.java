package org.argela.genericcommunicationservice.health;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ broker bağlantı durumu health check.
 * /actuator/health veya /actuator/rabbit üzerinden görülebilir.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMQHealthIndicator implements HealthIndicator {

    private final CachingConnectionFactory connectionFactory;

    @Override
    public Health health() {
        try (Connection connection = createRawConnection();
             Channel channel = connection.createChannel()) {
            log.debug("RabbitMQ connection OK");
            return Health.up().withDetail("rabbitmq", "Connected").build();
        } catch (Exception e) {
            log.error("RabbitMQ connection FAILED", e);
            return Health.down(e).withDetail("rabbitmq", "Not reachable").build();
        }
    }

    private Connection createRawConnection() throws Exception {
        ConnectionFactory rawFactory = new ConnectionFactory();
        rawFactory.setHost(connectionFactory.getHost());
        rawFactory.setPort(connectionFactory.getPort());
        rawFactory.setUsername(connectionFactory.getUsername());
        rawFactory.setPassword(connectionFactory.getRabbitConnectionFactory().getPassword());
        rawFactory.setVirtualHost(connectionFactory.getVirtualHost());
        return rawFactory.newConnection();
    }
}
