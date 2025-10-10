package io.kestra.plugin.jms;

import com.rabbitmq.jms.admin.RMQConnectionFactory;
import com.rabbitmq.jms.admin.RMQDestination;
import org.junit.jupiter.api.BeforeEach;

import jakarta.jms.Connection;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.Topic;

/**
 * Abstract base class for JMS integration tests using RabbitMQ.
 * <p>
 * This class provides common setup and utility methods for testing the JMS plugin
 * with a local RabbitMQ instance. Tests expect RabbitMQ to be running on localhost:5672
 * (which can be started using docker-compose up).
 * </p>
 */
public abstract class AbstractJMSTest {

    protected static final String RABBITMQ_HOST = "localhost";
    protected static final int RABBITMQ_PORT = 5672;
    protected static final String RABBITMQ_USER = "guest";
    protected static final String RABBITMQ_PASSWORD = "guest";
    protected static final String RABBITMQ_VHOST = "/";

    protected static final String TEST_QUEUE_NAME = "kestra_test_queue";
    protected static final String TEST_TOPIC_NAME = "kestra_test_topic";

    protected RMQConnectionFactory connectionFactory;

    /**
     * Sets up the test environment before each test.
     * Creates a RabbitMQ JMS connection factory and initializes test destinations.
     */
    @BeforeEach
    public void setUp() throws Exception {
        // Create RabbitMQ JMS connection factory
        connectionFactory = new RMQConnectionFactory();
        connectionFactory.setHost(RABBITMQ_HOST);
        connectionFactory.setPort(RABBITMQ_PORT);
        connectionFactory.setUsername(RABBITMQ_USER);
        connectionFactory.setPassword(RABBITMQ_PASSWORD);
        connectionFactory.setVirtualHost(RABBITMQ_VHOST);

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
     * Creates a test queue in RabbitMQ.
     */
    protected Queue createTestQueue() throws Exception {
        try (Connection connection = connectionFactory.createConnection();
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            return session.createQueue(TEST_QUEUE_NAME);
        }
    }

    /**
     * Creates a test topic in RabbitMQ.
     */
    protected Topic createTestTopic() throws Exception {
        try (Connection connection = connectionFactory.createConnection();
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            return session.createTopic(TEST_TOPIC_NAME);
        }
    }
}
