package io.kestra.plugin.jms;

import at.conapi.oss.jms.adapter.AbstractDestination;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.jms.configuration.ConnectionFactoryConfig;
import io.kestra.plugin.jms.serde.SerdeType;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import jakarta.jms.Connection;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class JMSProducerTest extends AbstractJMSTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void produceSingleMessageToQueue() throws Exception {
        // Create test queue
        createTestQueue();

        // Configure and run producer
        RunContext runContext = runContextFactory.of(Map.of("testId", IdUtils.create()));

        JMSProducer task = JMSProducer.builder()
            .id("produce-test")
            .connectionFactoryConfig(
                ConnectionFactoryConfig.Direct.builder()
                    .connectionFactoryClass(Property.of("org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory"))
                    .connectionProperties(Map.of(
                        "brokerURL", ACTIVEMQ_URL,
                        "user", ACTIVEMQ_USER,
                        "password", ACTIVEMQ_PASSWORD
                    ))
                    .build()
            )
            .destination(JMSDestination.builder()
                .destinationName(TEST_QUEUE_NAME)
                .destinationType(AbstractDestination.DestinationType.QUEUE)
                .build())
            .from(Map.of("data", "Hello Kestra JMS!"))
            .serdeType(SerdeType.STRING)
            .build();

        JMSProducer.Output output = task.run(runContext);

        // Verify output
        assertThat(output.getMessagesCount(), is(1));

        // Verify message was actually sent by consuming it
        try (Connection connection = connectionFactory.createConnection();
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {

            connection.start();
            MessageConsumer consumer = session.createConsumer(session.createQueue(TEST_QUEUE_NAME));

            Message message = consumer.receive(5000);
            assertThat(message, notNullValue());
            assertThat(message, instanceOf(TextMessage.class));

            TextMessage textMessage = (TextMessage) message;
            assertThat(textMessage.getText(), containsString("Hello Kestra JMS!"));
        }
    }

    @Test
    void produceMultipleMessagesToQueue() throws Exception {
        // Create test queue
        createTestQueue();

        RunContext runContext = runContextFactory.of(Map.of("testId", IdUtils.create()));

        JMSProducer task = JMSProducer.builder()
            .id("produce-test-multiple")
            .connectionFactoryConfig(
                ConnectionFactoryConfig.Direct.builder()
                    .connectionFactoryClass(Property.of("org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory"))
                    .connectionProperties(Map.of(
                        "brokerURL", ACTIVEMQ_URL,
                        "user", ACTIVEMQ_USER,
                        "password", ACTIVEMQ_PASSWORD
                    ))
                    .build()
            )
            .destination(JMSDestination.builder()
                .destinationName(TEST_QUEUE_NAME)
                .destinationType(AbstractDestination.DestinationType.QUEUE)
                .build())
            .from(List.of(
                Map.of("data", "Message 1"),
                Map.of("data", "Message 2"),
                Map.of("data", "Message 3")
            ))
            .serdeType(SerdeType.STRING)
            .build();

        JMSProducer.Output output = task.run(runContext);

        // Verify output
        assertThat(output.getMessagesCount(), is(3));

        // Verify all messages were sent
        try (Connection connection = connectionFactory.createConnection();
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {

            connection.start();
            MessageConsumer consumer = session.createConsumer(session.createQueue(TEST_QUEUE_NAME));

            for (int i = 1; i <= 3; i++) {
                Message message = consumer.receive(5000);
                assertThat(message, notNullValue());
                assertThat(message, instanceOf(TextMessage.class));

                TextMessage textMessage = (TextMessage) message;
                assertThat(textMessage.getText(), containsString("Message " + i));
            }
        }
    }

    @Test
    void produceJsonMessageToQueue() throws Exception {
        // Create test queue
        createTestQueue();

        RunContext runContext = runContextFactory.of(Map.of("testId", IdUtils.create()));

        JMSProducer task = JMSProducer.builder()
            .id("produce-test-json")
            .connectionFactoryConfig(
                ConnectionFactoryConfig.Direct.builder()
                    .connectionFactoryClass(Property.of("org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory"))
                    .connectionProperties(Map.of(
                        "brokerURL", ACTIVEMQ_URL,
                        "user", ACTIVEMQ_USER,
                        "password", ACTIVEMQ_PASSWORD
                    ))
                    .build()
            )
            .destination(JMSDestination.builder()
                .destinationName(TEST_QUEUE_NAME)
                .destinationType(AbstractDestination.DestinationType.QUEUE)
                .build())
            .from(Map.of("data", Map.of("id", 123, "name", "Test Item")))
            .serdeType(SerdeType.JSON)
            .build();

        JMSProducer.Output output = task.run(runContext);

        // Verify output
        assertThat(output.getMessagesCount(), is(1));

        // Verify JSON message was sent
        try (Connection connection = connectionFactory.createConnection();
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {

            connection.start();
            MessageConsumer consumer = session.createConsumer(session.createQueue(TEST_QUEUE_NAME));

            Message message = consumer.receive(5000);
            assertThat(message, notNullValue());
            assertThat(message, instanceOf(TextMessage.class));

            TextMessage textMessage = (TextMessage) message;
            String jsonContent = textMessage.getText();
            assertThat(jsonContent, containsString("\"id\""));
            assertThat(jsonContent, containsString("\"name\""));
            assertThat(jsonContent, containsString("Test Item"));
        }
    }

    @Test
    void produceMessageWithHeaders() throws Exception {
        // Create test queue
        createTestQueue();

        RunContext runContext = runContextFactory.of(Map.of("testId", IdUtils.create()));

        JMSProducer task = JMSProducer.builder()
            .id("produce-test-headers")
            .connectionFactoryConfig(
                ConnectionFactoryConfig.Direct.builder()
                    .connectionFactoryClass(Property.of("org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory"))
                    .connectionProperties(Map.of(
                        "brokerURL", ACTIVEMQ_URL,
                        "user", ACTIVEMQ_USER,
                        "password", ACTIVEMQ_PASSWORD
                    ))
                    .build()
            )
            .destination(JMSDestination.builder()
                .destinationName(TEST_QUEUE_NAME)
                .destinationType(AbstractDestination.DestinationType.QUEUE)
                .build())
            .from(Map.of(
                "data", "Message with custom properties",
                "headers", Map.of("customProp", "customValue", "priority", 5)
            ))
            .serdeType(SerdeType.STRING)
            .build();

        JMSProducer.Output output = task.run(runContext);

        // Verify output
        assertThat(output.getMessagesCount(), is(1));

        // Verify message properties
        try (Connection connection = connectionFactory.createConnection();
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {

            connection.start();
            MessageConsumer consumer = session.createConsumer(session.createQueue(TEST_QUEUE_NAME));

            Message message = consumer.receive(5000);
            assertThat(message, notNullValue());
            assertThat(message.getStringProperty("customProp"), is("customValue"));
        }
    }
}
