package org.argela.genericcommunicationservice.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * RabbitMQ temel konfigürasyonu
 * Sadece gerekli bean'leri ve primary broker connection'ı içerir
 */
@Configuration
public class RabbitMQConfig {

    @Value("${spring.rabbitmq.host:localhost}")
    private String defaultHost;

    @Value("${spring.rabbitmq.port:5672}")
    private int defaultPort;

    @Value("${spring.rabbitmq.username:guest}")
    private String defaultUsername;

    @Value("${spring.rabbitmq.password:guest}")
    private String defaultPassword;

    @Value("${spring.rabbitmq.virtual-host:/}")
    private String defaultVirtualHost;

    /**
     * JSON message converter - RabbitMQ için gerekli
     */
    @Bean("jackson2JsonMessageConverter")
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Primary connection factory - Listener için gerekli
     */
    @Bean
    @Primary
    public CachingConnectionFactory connectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setHost(defaultHost);
        factory.setPort(defaultPort);
        factory.setUsername(defaultUsername);
        factory.setPassword(defaultPassword);
        factory.setVirtualHost(defaultVirtualHost);

        // Connection optimization
        factory.setConnectionTimeout(30000);
        factory.setChannelCacheSize(10);
        factory.setChannelCheckoutTimeout(5000);
        factory.setRequestedHeartBeat(60);
        factory.setConnectionNameStrategy(cf -> "GCS-Primary");

        return factory;
    }

    /**
     * Queue'ları otomatik oluştur (monitoring ile)
     */
    @Bean
    public Queue genericMessagesQueue() {
        return new Queue("generic-messages-queue", true, false, false);
    }

    @Bean
    public Queue notificationsQueue() {
        return new Queue("notifications", true, false, false);
    }

    @Bean
    public Queue userNotificationsQueue() {
        return new Queue("user-notifications", true, false, false);
    }

    /**
     * Test için yeni queue (listener yok, mesaj birikecek)
     */
    @Bean
    public Queue testBuildupQueue() {
        return new Queue("test-buildup-queue", true, false, false);
    }
}