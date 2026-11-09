package com.juancruzy.earlybuyservice.catalog.messaging;

import com.juancruzy.earlybuyservice.catalog.client.CatalogClient;
import com.juancruzy.earlybuyservice.catalog.service.CatalogValidationService;
import com.juancruzy.earlybuyservice.shared.config.CatalogRabbitConfig;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import tools.jackson.databind.json.JsonMapper;

@SpringJUnitConfig(CatalogRabbitTestSupport.Infrastructure.class)
@TestPropertySource(properties = {
        "earlybuy.catalog.validation-timeout=2s", "earlybuy.catalog.max-pending-requests=32"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class CatalogRabbitTestSupport {

    @Autowired CatalogClient client;
    @Autowired CatalogValidationService validation;
    @Autowired PendingCatalogQueries pending;
    @Autowired RabbitAdmin admin;
    @Autowired CachingConnectionFactory connectionFactory;
    @Autowired AnonymousQueue replyQueue;
    @Autowired JsonMapper mapper;

    @TestConfiguration(proxyBeanMethods = false)
    @Import({CatalogRabbitConfig.class, PendingCatalogQueries.class, CatalogPublisher.class,
            CatalogReplyListener.class, CatalogClient.class, CatalogValidationService.class})
    static class Infrastructure {

        @Bean
        CachingConnectionFactory connectionFactory(Environment environment) {
            var factory = new CachingConnectionFactory(environment.getRequiredProperty("test.rabbit.host"),
                    environment.getRequiredProperty("test.rabbit.port", Integer.class));
            factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
            factory.setPublisherReturns(true);
            return factory;
        }

        @Bean
        RabbitAdmin rabbitAdmin(CachingConnectionFactory factory) {
            return new RabbitAdmin(factory);
        }

        @Bean
        SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(CachingConnectionFactory factory) {
            var listenerFactory = new SimpleRabbitListenerContainerFactory();
            listenerFactory.setConnectionFactory(factory);
            return listenerFactory;
        }

        @Bean
        JsonMapper objectMapper() {
            return JsonMapper.builder().build();
        }
    }
}
