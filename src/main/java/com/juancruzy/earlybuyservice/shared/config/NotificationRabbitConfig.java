package com.juancruzy.earlybuyservice.shared.config;

import com.juancruzy.earlybuyservice.notification.publisher.NotificationProperties;
import com.juancruzy.earlybuyservice.notification.publisher.NotificationPublisher;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationRabbitConfig {
    @Bean
    public DirectExchange notificationExchange() {
        return new DirectExchange(NotificationPublisher.EXCHANGE, true, false);
    }

    @Bean
    public RabbitTemplate notificationRabbitTemplate(ConnectionFactory connectionFactory) {
        var template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        return template;
    }
}
