package io.kestra.plugin.jms;

import at.conapi.oss.jms.adapter.AbstractDestination;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.jms.configuration.ConnectionFactoryConfig;
import io.kestra.plugin.jms.serde.SerdeType;
import jakarta.inject.Inject;
import jakarta.jms.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.FileInputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class ProduceTest extends AbstractJMSTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private StorageInterface storageInterface;

    @Test
    void produceSingleMessageToQueue() throws Exception {
        // Create test queue
        createTestQueue();

        // Configure and run producer
        RunContext runContext = runContextFactory.of(Map.of("testId", IdUtils.create()));

        Produce task = Produce.builder()
            .id("produce-test")
            .connectionFactoryConfig(
                ConnectionFactoryConfig.Direct.builder()
                    .connectionFactoryClass(Property.ofValue("org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory"))
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

        Produce.Output output = task.run(runContext);

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

        Produce task = Produce.builder()
            .id("produce-test-multiple")
            .connectionFactoryConfig(
                ConnectionFactoryConfig.Direct.builder()
                    .connectionFactoryClass(Property.ofValue("org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory"))
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

        Produce.Output output = task.run(runContext);

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

        Produce task = Produce.builder()
            .id("produce-test-json")
            .connectionFactoryConfig(
                ConnectionFactoryConfig.Direct.builder()
                    .connectionFactoryClass(Property.ofValue("org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory"))
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

        Produce.Output output = task.run(runContext);

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

        Produce task = Produce.builder()
            .id("produce-test-headers")
            .connectionFactoryConfig(
                ConnectionFactoryConfig.Direct.builder()
                    .connectionFactoryClass(Property.ofValue("org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory"))
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

        Produce.Output output = task.run(runContext);

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

    @Test
    void producePlainStringMessage() throws Exception {
        // Create test queue
        createTestQueue();

        RunContext runContext = runContextFactory.of(Map.of("testId", IdUtils.create()));

        // Test with plain string (not JSON) - should be wrapped in JMSMessage
        Produce task = Produce.builder()
            .id("produce-test-plain-string")
            .connectionFactoryConfig(
                ConnectionFactoryConfig.Direct.builder()
                    .connectionFactoryClass(Property.ofValue("org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory"))
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
            .from("Hello from plain string!")
            .serdeType(SerdeType.STRING)
            .build();

        Produce.Output output = task.run(runContext);

        // Verify output
        assertThat(output.getMessagesCount(), is(1));

        // Verify message was sent
        try (Connection connection = connectionFactory.createConnection();
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {

            connection.start();
            MessageConsumer consumer = session.createConsumer(session.createQueue(TEST_QUEUE_NAME));

            Message message = consumer.receive(5000);
            assertThat(message, notNullValue());
            assertThat(((TextMessage) message).getText(), is("Hello from plain string!"));
        }
    }

    @Test
    void produceMessagesFromJsonString() throws Exception {
        // Create test queue
        createTestQueue();

        RunContext runContext = runContextFactory.of(Map.of("testId", IdUtils.create()));

        // Test with JSON array string (using Data.From pattern)
        Produce task = Produce.builder()
            .id("produce-test-json-string")
            .connectionFactoryConfig(
                ConnectionFactoryConfig.Direct.builder()
                    .connectionFactoryClass(Property.ofValue("org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory"))
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
            .from("""
                [
                  {"data": "Message 1 from JSON"},
                  {"data": "Message 2 from JSON"}
                ]
                """)
            .serdeType(SerdeType.STRING)
            .build();

        Produce.Output output = task.run(runContext);

        // Verify output
        assertThat(output.getMessagesCount(), is(2));

        // Verify messages were sent
        try (Connection connection = connectionFactory.createConnection();
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {

            connection.start();
            MessageConsumer consumer = session.createConsumer(session.createQueue(TEST_QUEUE_NAME));

            Message message1 = consumer.receive(5000);
            assertThat(message1, notNullValue());
            assertThat(((TextMessage) message1).getText(), is("Message 1 from JSON"));

            Message message2 = consumer.receive(5000);
            assertThat(message2, notNullValue());
            assertThat(((TextMessage) message2).getText(), is("Message 2 from JSON"));
        }
    }

    @Test
    void produceMessagesFromSingleJsonObject() throws Exception {
        // Create test queue
        createTestQueue();

        RunContext runContext = runContextFactory.of(Map.of("testId", IdUtils.create()));

        // Test with single JSON object string (using Data.From pattern)
        Produce task = Produce.builder()
            .id("produce-test-single-json")
            .connectionFactoryConfig(
                ConnectionFactoryConfig.Direct.builder()
                    .connectionFactoryClass(Property.ofValue("org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory"))
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
            .from("""
                {"data": "Single message from JSON"}
                """)
            .serdeType(SerdeType.STRING)
            .build();

        Produce.Output output = task.run(runContext);

        // Verify output
        assertThat(output.getMessagesCount(), is(1));

        // Verify message was sent
        try (Connection connection = connectionFactory.createConnection();
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {

            connection.start();
            MessageConsumer consumer = session.createConsumer(session.createQueue(TEST_QUEUE_NAME));

            Message message = consumer.receive(5000);
            assertThat(message, notNullValue());
            assertThat(((TextMessage) message).getText(), is("Single message from JSON"));
        }
    }

    @Test
    void produceMessagesFromKestraUri() throws Exception {
        // Create test queue
        createTestQueue();

        // Create temporary ION file with JMSMessage objects
        Path tempFile = Files.createTempFile("jms-messages", ".ion");
        final List<JMSMessage> inputMessages = List.of(
            JMSMessage.builder().data("Message 1 from URI").build(),
            JMSMessage.builder().data("Message 2 from URI").build(),
            JMSMessage.builder().data("Message 3 from URI").build()
        );
        FileSerde.writeAll(Files.newBufferedWriter(tempFile), Flux.fromIterable(inputMessages)).block();

        // Upload to Kestra storage
        URI storageUri;
        try (var input = new FileInputStream(tempFile.toFile())) {
            storageUri = storageInterface.put(MAIN_TENANT, null, URI.create("/jms-test-messages.ion"), input);
        }

        RunContext runContext = runContextFactory.of(Map.of(
            "testId", IdUtils.create(),
            "messagesUri", storageUri
        ));

        // Test with Kestra URI (using Data.From pattern)
        Produce task = Produce.builder()
            .id("produce-test-uri")
            .connectionFactoryConfig(
                ConnectionFactoryConfig.Direct.builder()
                    .connectionFactoryClass(Property.ofValue("org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory"))
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
            .from("{{ messagesUri }}")
            .serdeType(SerdeType.STRING)
            .build();

        Produce.Output output = task.run(runContext);

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
                assertThat(textMessage.getText(), is("Message " + i + " from URI"));
            }
        }
    }
}
