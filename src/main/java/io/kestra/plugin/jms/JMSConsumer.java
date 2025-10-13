package io.kestra.plugin.jms;

import at.conapi.oss.jms.adapter.AbstractDestination;
import at.conapi.oss.jms.adapter.AbstractMessage;
import at.conapi.oss.jms.adapter.impl.ConnectionAdapter;
import at.conapi.oss.jms.adapter.impl.ConsumerAdapter;
import at.conapi.oss.jms.adapter.impl.SessionAdapter;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.jms.serde.SerdeType;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.utils.Rethrow;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A Kestra task to consume messages from a JMS-compliant message broker.
 * This task connects to a broker, consumes messages until a specified limit
 * is reached, and stores them in Kestra's internal storage.
 */
@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Schema(title = "Consume messages from a JMS queue or topic.", description = "It is recommended to set `maxWaitTimeout` or `maxMessages`.")
@Plugin(examples = {
        @Example(
                full = true,
                title = "Consume 100 Messages from a JMS Queue",
                code = {
                        "id: jms_consume",
                        "namespace: company.team",
                        "",
                        "tasks:",
                        "  - id: consume_from_queue",
                        "    type: io.kestra.plugin.jms.JMSConsumer",
                        "    connectionFactoryConfig:",
                        "      type: DIRECT",
                        "      providerJarPaths: kestra:///jms/activemq-client.jar",
                        "      connectionFactoryClass: org.apache.activemq.ActiveMQConnectionFactory",
                        "      username: admin",
                        "      password: \"{{ secret('AMQ_PASSWORD') }}\"",
                        "    destination:",
                        "      name: my-queue",
                        "      destinationType: QUEUE",
                        "    maxMessages: 100",
                        "    maxWaitTimeout: 5000",
                }
        )
})

public class JMSConsumer extends AbstractJmsTask implements RunnableTask<JMSConsumer.Output> {

    // NOTE: Using @PluginProperty instead of Property<JMSDestination> wrapper.
    // Nested configuration objects with @PluginProperty fields don't deserialize correctly
    // when wrapped in Property<>. Other Kestra messaging plugins (AMQP, Solace) avoid nested
    // config objects entirely, using flat Property<String> fields instead.
    @PluginProperty
    @NotNull
    @Schema(title = "The destination to consume messages from.")
    private JMSDestination destination;

    @PluginProperty(dynamic = true)
    @Schema(
            title = "Message selector to only consume specific messages.",
            description = "A JMS message selector expression to filter messages. Uses SQL-92 syntax (e.g., \"JMSPriority > 5 AND type = 'order'\")."
    )
    private String messageSelector;

    @Builder.Default
    @Schema(
            title = "The format for deserializing the message body.",
            description = "Determines how message bodies are deserialized. STRING for text messages, JSON for JSON-formatted text, BYTES for binary data.",
            defaultValue = "STRING"
    )
    private Property<SerdeType> serdeType = Property.ofValue(SerdeType.STRING);

    @Builder.Default
    @Schema(title = "The maximum number of messages to consume. (default 1)")
    private Property<Integer> maxMessages = Property.ofValue(1);

    @Builder.Default
    @Schema(title = "The maximum time to wait for messages in milliseconds. (default 0, never times out)")
    private Property<Long> maxWaitTimeout = Property.ofValue(0L);

    @Override
    public Output run(RunContext runContext) throws Exception {
        // Render maxMessages once at the beginning
        Integer rMaxMessages = runContext.render(this.maxMessages).as(Integer.class).orElseThrow();

        AtomicInteger total = new AtomicInteger();
        File tempFile = runContext.workingDir().createTempFile(".ion").toFile();
        URI uri;

        try (ConsumeRunner consumer = new ConsumeRunner(runContext, this);
             BufferedOutputStream outputFile = new BufferedOutputStream(new FileOutputStream(tempFile))) {

            consumer.run(
                    Rethrow.throwConsumer(message -> {
                        FileSerde.write(outputFile, message);
                        total.getAndIncrement();
                    }),
                    () -> this.ended(total, rMaxMessages)
            );

            outputFile.flush();
            String rDestName = runContext.render(this.destination.getDestinationName());
            runContext.metric(Counter.of("messages", total.get(), "destination", rDestName));
        }

        uri = runContext.storage().putFile(tempFile);

        return Output.builder()
                .uri(uri)
                .count(total.get())
                .build();
    }

    private boolean ended(AtomicInteger count, Integer rMaxMessages) {
        return rMaxMessages != null && count.get() >= rMaxMessages;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Number of messages consumed.")
        private final Integer count;

        @Schema(title = "URI of a Kestra internal storage file containing the consumed messages.")
        private final URI uri;
    }

    /**
     * A helper class that manages the JMS connection, session, and consumer lifecycle.
     */
    private class ConsumeRunner implements AutoCloseable {
        private final ConnectionAdapter connection;
        private final SessionAdapter session;
        private final ConsumerAdapter messageConsumer;
        private final SerdeType rSerdeType;
        private final long rMaxWaitTimeout;

        public ConsumeRunner(RunContext runContext, JMSConsumer task) throws Exception {
            // Render Property fields with 'r' prefix
            this.rSerdeType = runContext.render(task.serdeType).as(SerdeType.class).orElseThrow();
            this.rMaxWaitTimeout = runContext.render(task.maxWaitTimeout).as(Long.class).orElseThrow();

            // Inherit the connection logic from the abstract base class
            this.connection = task.createConnection(runContext);

            this.connection.setExceptionListener(exception ->
                    runContext.logger().error("Asynchronous JMS Connection Error: {}", exception.getMessage(), exception)
            );

            //  Create the Session object
            this.session = (SessionAdapter) this.connection.createSession();

            //  Create the Destination object depending on the Destination Type (QUEUE or TOPIC)
            String destName = runContext.render(task.destination.getDestinationName());
            String destType = task.destination.getDestinationType() == AbstractDestination.DestinationType.QUEUE ?
                    SessionAdapter.QUEUE : SessionAdapter.TOPIC;
            String destinationUrl = String.format("%s://%s", destType, destName);
            AbstractDestination jmsDestination = this.session.createDestination(destinationUrl);

            // allow dynamic message selector use cases
            String msgSelector = runContext.render(task.getMessageSelector());

            this.messageConsumer = (ConsumerAdapter) this.session.createConsumer(jmsDestination, msgSelector);

            // Start the connection now that all resources are set up
            this.connection.start();

            runContext.logger().info("JMS Consumer started for destination '{}'", destName);
        }

        public void run(java.util.function.Consumer<JMSMessage> messageProcessor, java.util.function.Supplier<Boolean> endCondition) throws Exception {
            long startTime = System.currentTimeMillis();

            while (!endCondition.get()) {
                long waitTime = 100; // Default short poll
                if (this.rMaxWaitTimeout > 0) {
                    long remainingTime = (startTime + this.rMaxWaitTimeout) - System.currentTimeMillis();
                    if (remainingTime <= 0) break; // Max duration reached
                    waitTime = remainingTime;
                }

                AbstractMessage message = this.messageConsumer.receive(waitTime);

                if (message == null) {
                    if (this.rMaxWaitTimeout > 0) break; // Timeout on receive with max duration set
                    else continue; // No message on a short poll, just loop again
                }

                messageProcessor.accept(JMSMessage.of(message, this.rSerdeType));
            }
        }

        @Override
        public void close() {
            // Use the quiet closing utility from the abstract base class
            closeQuietly(this.messageConsumer);
            closeQuietly(this.session);
            closeQuietly(this.connection);
        }
    }
}
