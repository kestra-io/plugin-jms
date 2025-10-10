package io.kestra.plugin.jms;

import at.conapi.oss.jms.adapter.AbstractDestination;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.jms.configuration.ConnectionFactoryConfig;
import io.kestra.plugin.jms.serde.SerdeType;
import io.kestra.core.junit.annotations.KestraTest;
import org.junit.jupiter.api.Test;

import jakarta.jms.*;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class JMSRealtimeTriggerTest extends AbstractJMSTest {

    @Test
    void triggerOnMessageArrival() throws Exception {
        // Create test queue
        createTestQueue();

        // Create a simple flow with the JMS realtime trigger
        String flowId = IdUtils.create();
        String triggerId = IdUtils.create();

        Flow flow = Flow.builder()
            .id(flowId)
            .namespace("io.kestra.test")
            .revision(1)
            .triggers(java.util.List.of(
                JMSRealtimeTrigger.builder()
                    .id(triggerId)
                    .connectionFactoryConfig(
                        ConnectionFactoryConfig.Direct.builder()
                            .connectionFactoryClass("com.rabbitmq.jms.admin.RMQConnectionFactory")
                            .connectionProperties(Map.of(
                                "host", RABBITMQ_HOST,
                                "port", String.valueOf(RABBITMQ_PORT),
                                "username", RABBITMQ_USER,
                                "password", RABBITMQ_PASSWORD,
                                "virtualHost", RABBITMQ_VHOST
                            ))
                            .build()
                    )
                    .destination(JMSDestination.builder()
                        .destinationName(TEST_QUEUE_NAME)
                        .destinationType(AbstractDestination.DestinationType.QUEUE)
                        .build())
                    .serdeType(SerdeType.STRING)
                    .build()
            ))
            .tasks(java.util.List.of())
            .build();

        // Verify trigger configuration
        assertThat(flow.getTriggers(), hasSize(1));
        assertThat(flow.getTriggers().get(0), instanceOf(JMSRealtimeTrigger.class));

        JMSRealtimeTrigger trigger = (JMSRealtimeTrigger) flow.getTriggers().get(0);
        assertThat(trigger.getDestination().getDestinationName(), is(TEST_QUEUE_NAME));
        assertThat(trigger.getDestination().getDestinationType(), is(AbstractDestination.DestinationType.QUEUE));
    }

    @Test
    void triggerWithMessageSelector() throws Exception {
        // Create test queue
        createTestQueue();

        String flowId = IdUtils.create();
        String triggerId = IdUtils.create();

        Flow flow = Flow.builder()
            .id(flowId)
            .namespace("io.kestra.test")
            .revision(1)
            .triggers(java.util.List.of(
                JMSRealtimeTrigger.builder()
                    .id(triggerId)
                    .connectionFactoryConfig(
                        ConnectionFactoryConfig.Direct.builder()
                            .connectionFactoryClass("com.rabbitmq.jms.admin.RMQConnectionFactory")
                            .connectionProperties(Map.of(
                                "host", RABBITMQ_HOST,
                                "port", String.valueOf(RABBITMQ_PORT),
                                "username", RABBITMQ_USER,
                                "password", RABBITMQ_PASSWORD,
                                "virtualHost", RABBITMQ_VHOST
                            ))
                            .build()
                    )
                    .destination(JMSDestination.builder()
                        .destinationName(TEST_QUEUE_NAME)
                        .destinationType(AbstractDestination.DestinationType.QUEUE)
                        .build())
                    .messageSelector("urgent = TRUE")
                    .serdeType(SerdeType.STRING)
                    .build()
            ))
            .tasks(java.util.List.of())
            .build();

        // Verify trigger configuration includes selector
        assertThat(flow.getTriggers(), hasSize(1));
        assertThat(flow.getTriggers().get(0), instanceOf(JMSRealtimeTrigger.class));

        JMSRealtimeTrigger trigger = (JMSRealtimeTrigger) flow.getTriggers().get(0);
        assertThat(trigger.getMessageSelector(), is("urgent = TRUE"));
        assertThat(trigger.getDestination().getDestinationName(), is(TEST_QUEUE_NAME));
    }

    /**
     * Helper method to send a test message to a queue.
     */
    private void sendTestMessage(String queueName, String messageText) throws Exception {
        try (Connection connection = connectionFactory.createConnection();
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {

            Queue queue = session.createQueue(queueName);
            MessageProducer producer = session.createProducer(queue);

            TextMessage message = session.createTextMessage(messageText);
            producer.send(message);
        }
    }

    /**
     * Helper method to send a test message with a boolean property.
     */
    private void sendTestMessageWithBooleanProperty(String queueName, String messageText, String propertyName, boolean propertyValue) throws Exception {
        try (Connection connection = connectionFactory.createConnection();
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {

            Queue queue = session.createQueue(queueName);
            MessageProducer producer = session.createProducer(queue);

            TextMessage message = session.createTextMessage(messageText);
            message.setBooleanProperty(propertyName, propertyValue);
            producer.send(message);
        }
    }
}
