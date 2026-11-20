package com.juancruzy.earlybuyservice.shared.config;

import com.juancruzy.earlybuyservice.catalog.dto.StockChangedEvent;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class StockEventsRabbitConfig {
    @Bean
    public FanoutExchange catalogEventsExchange() {
        return new FanoutExchange(StockChangedEvent.EXCHANGE, true, false);
    }

    @Bean
    public Queue stockEventsQueue() {
        return QueueBuilder.durable(StockChangedEvent.QUEUE).build();
    }

    @Bean
    public Binding stockEventsBinding(@Qualifier("stockEventsQueue") Queue queue, FanoutExchange catalogEventsExchange) {
        return BindingBuilder.bind(queue).to(catalogEventsExchange);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory stockEventsListenerFactory(ConnectionFactory connectionFactory) {
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setPrefetchCount(1);
        return factory;
    }
}
