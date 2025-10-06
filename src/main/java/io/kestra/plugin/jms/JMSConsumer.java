package io.kestra.plugin.jms;

import at.conapi.plugins.common.endpoints.jms.adapter.AbstractDestination;
import at.conapi.plugins.common.endpoints.jms.adapter.AbstractMessage;
import at.conapi.plugins.common.endpoints.jms.adapter.impl.ConnectionAdapter;
import at.conapi.plugins.common.endpoints.jms.adapter.impl.ConsumerAdapter;
import at.conapi.plugins.common.endpoints.jms.adapter.impl.SessionAdapter;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
                        "    maxMessages: 1",
                        "    maxWaitTimeout: 5000",
                }
        )
})

public class JMSConsumer extends AbstractJmsTask implements RunnableTask<JMSConsumer.Output> {

    @PluginProperty
    @NotNull
    @Schema(title = "The destination to consume messages from.")
    private JMSDestination destination;

    @PluginProperty(dynamic = true)
    @Schema(title = "Message selector to only consume specific messages")
    private String messageSelector;

    @PluginProperty
    @Builder.Default
    @Schema(title = "The format for deserializing the message body.", defaultValue = "STRING")
    private SerdeType serdeType = SerdeType.STRING;

    @PluginProperty
    @Schema(title = "The maximum number of messages to consume. (default 1)")
    private Integer maxMessages = 1;

    @PluginProperty
    @Schema(title = "The maximum time to wait for messages in milliseconds. (default 0, never times out)")
    private Long maxWaitTimeout = 0L;

    @Override
    public Output run(RunContext runContext) throws Exception {

        // writing the message to a file, try to optimize i.e. 10000 messages
        List<URI> uris = Collections.synchronizedList( new ArrayList<>() );
        AtomicInteger total = new AtomicInteger();

        try (
                ConsumeRunner consumer = new ConsumeRunner(runContext, this)
        ) {
            consumer.run(
                    Rethrow.throwConsumer(message -> {
                        // dedicated temp file per message
                        File tempFile = runContext.workingDir().createTempFile(".jms").toFile();

                        try (BufferedOutputStream outputFile = new BufferedOutputStream(new FileOutputStream(tempFile))){
                            FileSerde.write(outputFile, message);
                            outputFile.flush();
                            uris.add(runContext.storage().putFile(tempFile));
                            total.getAndIncrement();
                        }
                    }),
                    () -> this.ended(total)
            );

            runContext.metric(Counter.of("records", total.get(), "destination", this.destination.getDestinationName()));
        }

        return Output.builder()
                .messageURIs(uris)
                .count(total.get())
                .build();
    }

    private boolean ended(AtomicInteger count) {
        return this.maxMessages != null && count.get() >= this.maxMessages;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Number of messages consumed.")
        private final Integer count;

        @Schema(title = "URIs of the files in Kestra's internal storage containing the consumed messages.")
        private final List<URI> messageURIs;
    }

    /**
     * A helper class that manages the JMS connection, session, and consumer lifecycle.
     */
    private class ConsumeRunner implements AutoCloseable {
        private final ConnectionAdapter connection;
        private final SessionAdapter session;
        private final ConsumerAdapter messageConsumer;
        private final long timeout;

        public ConsumeRunner(RunContext runContext, JMSConsumer task) throws Exception {
            // Inherit the connection logic from the abstract base class
            this.connection = task.createConnection(runContext);

            this.connection.setExceptionListener(exception ->
                    runContext.logger().error("Asynchronous JMS Connection Error: {}", exception.getMessage(), exception)
            );

            //  Create the Session object
            this.session = (SessionAdapter) this.connection.createSession();

            //  Create the Destination object depending on the Destination Type (QUEUE or TOPIC)
            String destName = runContext.render(task.getDestination().getDestinationName());
            String destType = task.getDestination().getDestinationType() == AbstractDestination.DestinationType.QUEUE ?
                    SessionAdapter.QUEUE : SessionAdapter.TOPIC;
            String destinationUrl = String.format("%s://%s", destType, destName);
            AbstractDestination jmsDestination = this.session.createDestination(destinationUrl);

            // allow dynamic message selector use cases
            String msgSelector = runContext.render(task.getMessageSelector());

            this.messageConsumer = (ConsumerAdapter) this.session.createConsumer(jmsDestination, msgSelector);
            this.timeout = maxWaitTimeout;

            // Start the connection now that all resources are set up
            this.connection.start();

            runContext.logger().info("JMS Consumer started for destination '{}'", destName);
        }

        public void run(java.util.function.Consumer<JMSMessage> messageProcessor, java.util.function.Supplier<Boolean> endCondition) throws Exception {
            long startTime = System.currentTimeMillis();

            while (!endCondition.get()) {
                long waitTime = 100; // Default short poll
                if (this.timeout > 0) {
                    long remainingTime = (startTime + this.timeout) - System.currentTimeMillis();
                    if (remainingTime <= 0) break; // Max duration reached
                    waitTime = remainingTime;
                }

                AbstractMessage message = this.messageConsumer.receive(waitTime);

                if (message == null) {
                    if (this.timeout > 0) break; // Timeout on receive with max duration set
                    else continue; // No message on a short poll, just loop again
                }

                messageProcessor.accept(JMSMessage.of(message, serdeType));
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
