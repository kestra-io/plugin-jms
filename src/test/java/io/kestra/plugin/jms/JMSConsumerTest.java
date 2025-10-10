package io.kestra.plugin.jms;

import at.conapi.oss.jms.adapter.AbstractDestination;
import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.jms.configuration.ConnectionFactoryConfig;
import io.kestra.plugin.jms.serde.SerdeType;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import jakarta.jms.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class JMSConsumerTest extends AbstractJMSTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void consumeSingleMessageFromQueue() throws Exception {
        // Create test queue and send a message
        createTestQueue();
        sendTestMessage(TEST_QUEUE_NAME, "Test message for consumption");

        // Configure and run consumer
        RunContext runContext = runContextFactory.of(Map.of("testId", IdUtils.create()));

        JMSConsumer task = JMSConsumer.builder()
            .id("consume-test")
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
            .maxMessages(1)
            .maxWaitTimeout(5000L)
            .serdeType(SerdeType.STRING)
            .build();

        JMSConsumer.Output output = task.run(runContext);

        // Verify output
        assertThat(output.getCount(), is(1));
        assertThat(output.getUri(), notNullValue());

        // Read consumed messages from storage
        List<JMSMessage> messages = readMessagesFromStorage(runContext, output.getUri());
        assertThat(messages, hasSize(1));
        assertThat(messages.get(0).getData().toString(), containsString("Test message for consumption"));
    }

    @Test
    void consumeMultipleMessagesFromQueue() throws Exception {
        // Create test queue and send multiple messages
        createTestQueue();
        sendTestMessage(TEST_QUEUE_NAME, "Message 1");
        sendTestMessage(TEST_QUEUE_NAME, "Message 2");
        sendTestMessage(TEST_QUEUE_NAME, "Message 3");

        // Configure and run consumer
        RunContext runContext = runContextFactory.of(Map.of("testId", IdUtils.create()));

        JMSConsumer task = JMSConsumer.builder()
            .id("consume-test-multiple")
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
            .maxMessages(3)
            .maxWaitTimeout(5000L)
            .serdeType(SerdeType.STRING)
            .build();

        JMSConsumer.Output output = task.run(runContext);

        // Verify output
        assertThat(output.getCount(), is(3));

        // Read consumed messages
        List<JMSMessage> messages = readMessagesFromStorage(runContext, output.getUri());
        assertThat(messages, hasSize(3));
    }

    @Test
    @org.junit.jupiter.api.Disabled("RabbitMQ JMS client does not support message selectors")
    void consumeWithMessageSelector() throws Exception {
        // Create test queue and send messages with different properties
        createTestQueue();
        sendTestMessageWithProperty(TEST_QUEUE_NAME, "High priority message", "priority", 10);
        sendTestMessageWithProperty(TEST_QUEUE_NAME, "Low priority message", "priority", 1);
        sendTestMessageWithProperty(TEST_QUEUE_NAME, "Another high priority", "priority", 10);

        // Configure consumer with message selector
        RunContext runContext = runContextFactory.of(Map.of("testId", IdUtils.create()));

        JMSConsumer task = JMSConsumer.builder()
            .id("consume-test-selector")
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
            .messageSelector("priority = 10")
            .maxMessages(10)
            .maxWaitTimeout(5000L)
            .serdeType(SerdeType.STRING)
            .build();

        JMSConsumer.Output output = task.run(runContext);

        // Should only consume 2 messages (those with priority = 10)
        assertThat(output.getCount(), is(2));

        // Verify filtered messages
        List<JMSMessage> messages = readMessagesFromStorage(runContext, output.getUri());
        assertThat(messages, hasSize(2));
        messages.forEach(msg -> {
            assertThat(msg.getData().toString(), containsString("high priority"));
        });
    }

    @Test
    void consumeWithTimeout() throws Exception {
        // Create empty queue
        createTestQueue();

        // Configure consumer with short timeout
        RunContext runContext = runContextFactory.of(Map.of("testId", IdUtils.create()));

        JMSConsumer task = JMSConsumer.builder()
            .id("consume-test-timeout")
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
            .maxMessages(10)
            .maxWaitTimeout(2000L) // 2 seconds timeout
            .serdeType(SerdeType.STRING)
            .build();

        JMSConsumer.Output output = task.run(runContext);

        // Should consume 0 messages due to timeout
        assertThat(output.getCount(), is(0));
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
     * Helper method to send a test message with a custom property.
     */
    private void sendTestMessageWithProperty(String queueName, String messageText, String propertyName, int propertyValue) throws Exception {
        try (Connection connection = connectionFactory.createConnection();
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {

            Queue queue = session.createQueue(queueName);
            MessageProducer producer = session.createProducer(queue);

            TextMessage message = session.createTextMessage(messageText);
            message.setIntProperty(propertyName, propertyValue);
            producer.send(message);
        }
    }

    /**
     * Helper method to read consumed messages from Kestra storage.
     */
    private List<JMSMessage> readMessagesFromStorage(RunContext runContext, java.net.URI uri) throws Exception {
        List<JMSMessage> messages = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(runContext.storage().getFile(uri)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                JMSMessage message = JacksonMapper.ofIon().readValue(line, JMSMessage.class);
                messages.add(message);
            }
        }
        return messages;
    }
}
