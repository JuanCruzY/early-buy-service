package com.juancruzy.earlybuyservice.shared.config;

import java.util.UUID;

import com.juancruzy.earlybuyservice.catalog.client.CatalogProperties;
import com.juancruzy.earlybuyservice.catalog.messaging.CatalogPublisher;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableRabbit
@EnableConfigurationProperties(CatalogProperties.class)
public class CatalogRabbitConfig {

    @Bean
    public DirectExchange catalogValidationExchange() {
        return new DirectExchange(CatalogPublisher.EXCHANGE, false, false);
    }

    @Bean
    public AnonymousQueue catalogReplyQueue() {
        return new AnonymousQueue(() -> "earlybuy.article.validation." + UUID.randomUUID());
    }

    @Bean
    public Binding catalogReplyBinding(@Qualifier("catalogReplyQueue") AnonymousQueue queue,
                                        @Qualifier("catalogValidationExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(queue.getName());
    }

    @Bean
    public RabbitTemplate catalogRabbitTemplate(ConnectionFactory connectionFactory) {
        var template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        return template;
    }

    @Bean
    public ThreadPoolTaskExecutor catalogPublisherExecutor(CatalogProperties properties) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(properties.maxPendingRequests());
        executor.setThreadNamePrefix("catalog-publisher-");
        return executor;
    }
}
