package io.kestra.plugin.jms;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.BeforeEach;

import jakarta.jms.Connection;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.Topic;

/**
 * Abstract base class for JMS integration tests using ActiveMQ Artemis.
 * <p>
 * This class provides common setup and utility methods for testing the JMS plugin
 * with a local ActiveMQ Artemis instance. Tests expect ActiveMQ to be running on localhost:61616
 * (which can be started using docker-compose up).
 * </p>
 */
public abstract class AbstractJMSTest {

    protected static final String ACTIVEMQ_URL = "tcp://localhost:61616";
    protected static final String ACTIVEMQ_USER = "admin";
    protected static final String ACTIVEMQ_PASSWORD = "admin";

    protected static final String TEST_QUEUE_NAME = "kestra_test_queue";
    protected static final String TEST_TOPIC_NAME = "kestra_test_topic";

    protected ActiveMQConnectionFactory connectionFactory;

    /**
     * Sets up the test environment before each test.
     * Creates an ActiveMQ Artemis JMS connection factory and initializes test destinations.
     */
    @BeforeEach
    public void setUp() throws Exception {
        // Create ActiveMQ Artemis connection factory
        connectionFactory = new ActiveMQConnectionFactory(ACTIVEMQ_URL, ACTIVEMQ_USER, ACTIVEMQ_PASSWORD);

        // Clean up test destinations from previous runs
        cleanupDestinations();
    }

    /**
     * Cleans up test destinations by creating a connection and purging queues.
     */
    protected void cleanupDestinations() throws Exception {
        try (Connection connection = connectionFactory.createConnection();
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {

            connection.start();

            // Create queue and drain all messages
            Queue queue = session.createQueue(TEST_QUEUE_NAME);
            var consumer = session.createConsumer(queue);

            // Drain all messages from the queue with a short timeout
            while (consumer.receive(100) != null) {
                // Just consume and discard
            }

            consumer.close();

            // Topics don't need cleanup as they're publish-subscribe
        }
    }

    /**
     * Creates a test queue in ActiveMQ.
     */
    protected Queue createTestQueue() throws Exception {
        try (Connection connection = connectionFactory.createConnection();
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            return session.createQueue(TEST_QUEUE_NAME);
        }
    }

    /**
     * Creates a test topic in ActiveMQ.
     */
    protected Topic createTestTopic() throws Exception {
        try (Connection connection = connectionFactory.createConnection();
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            return session.createTopic(TEST_TOPIC_NAME);
        }
    }
}
