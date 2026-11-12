package com.juancruzy.earlybuyservice.notification.publisher;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import com.juancruzy.earlybuyservice.notification.dto.NotificationRequest;
import com.juancruzy.earlybuyservice.subscription.model.NotificationChannel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class NotificationPublisherTests {
    private final RabbitTemplate template = mock(RabbitTemplate.class);
    private final NotificationRequest request = new NotificationRequest("article", "user", 25, "test-contact");

    @Test
    void independentlyConfirmsPublicationsThatShareTheSameEventCorrelationId() {
        var publisher = publisher(Duration.ofSeconds(5));
        var mail = publisher.publish(NotificationChannel.EMAIL, request, "one-stock-event");
        var sms = publisher.publish(NotificationChannel.SMS, request, "one-stock-event");
        var confirmations = ArgumentCaptor.forClass(CorrelationData.class);
        var messages = ArgumentCaptor.forClass(Message.class);
        verify(template, times(2)).send(eq("notifications"), anyString(), messages.capture(), confirmations.capture());
        assertThat(messages.getAllValues()).allSatisfy(message ->
                assertThat(message.getMessageProperties().getCorrelationId()).isEqualTo("one-stock-event"));
        var first = confirmations.getAllValues().get(0);
        var second = confirmations.getAllValues().get(1);
        assertThat(first.getId()).isNotEqualTo(second.getId());

        second.getFuture().complete(new CorrelationData.Confirm(true, null));
        assertThat(sms).isCompleted();
        assertThat(mail).isNotDone();
        first.getFuture().complete(new CorrelationData.Confirm(false, "broker nack"));
        assertThatThrownBy(mail::join).hasCauseInstanceOf(NotificationPublishException.class)
                .hasMessageContaining("rechazó");
    }

    @Test
    void failsIfConfirmationDoesNotArriveBeforeTheDeadline() {
        var result = publisher(Duration.ofMillis(20)).publish(NotificationChannel.PUSH, request, "stock-event");
        assertThatThrownBy(result::join).hasCauseInstanceOf(NotificationPublishException.class)
                .hasRootCauseInstanceOf(TimeoutException.class);
    }

    @Test
    void reportsConnectionFailureToTheCaller() {
        var error = new AmqpConnectException(new java.net.ConnectException("broker unavailable"));
        doThrow(error).when(template).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        var result = publisher(Duration.ofSeconds(5)).publish(NotificationChannel.EMAIL, request, "stock-event");
        assertThatThrownBy(result::join).hasCauseInstanceOf(NotificationPublishException.class)
                .hasRootCauseInstanceOf(java.net.ConnectException.class);
    }

    private NotificationPublisher publisher(Duration timeout) {
        return new NotificationPublisher(template, JsonMapper.builder().build(), new NotificationProperties(timeout));
    }
}
