package io.kestra.plugin.jms;

import at.conapi.oss.jms.adapter.AbstractDestination;
import at.conapi.oss.jms.adapter.AbstractMessage;
import at.conapi.oss.jms.adapter.AbstractProducer;
import at.conapi.oss.jms.adapter.AbstractSession;
import at.conapi.oss.jms.adapter.impl.ConnectionAdapter;
import at.conapi.oss.jms.adapter.impl.ProducerAdapter;
import at.conapi.oss.jms.adapter.impl.SessionAdapter;
import io.kestra.core.models.property.Data;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.jms.serde.SerdeType;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * A Kestra task to produce messages to a JMS-compliant message broker.
 * This task connects to a broker and sends messages from a specified source.
 */
@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Schema(title = "Produce messages to a JMS queue or topic.")
@Plugin(examples = {
        @Example(
                full = true,
                title = "Produce a List of Messages to a JMS Queue",
                code = """
                        id: jms_produce
                        namespace: company.team

                        tasks:
                          - id: produce_to_queue
                            type: io.kestra.plugin.jms.JMSProducer
                            connectionFactoryConfig:
                              type: DIRECT
                              providerJarPaths: kestra:///jms/activemq-client.jar
                              connectionFactoryClass: org.apache.activemq.ActiveMQConnectionFactory
                              username: admin
                              password: "{{ secret('AMQ_PASSWORD') }}"
                            destination:
                              name: my-queue
                              destinationType: QUEUE
                            from:
                              - data: "Hello World"
                                headers:
                                  property1: "value1"
                              - data:
                                  message: "Another message"
                                  id: 123
                        """
        )
})
public class JMSProducer extends AbstractJmsTask implements RunnableTask<JMSProducer.Output>, Data.From {

    // NOTE: Using @PluginProperty instead of Property<JMSDestination> wrapper.
    // Nested configuration objects with @PluginProperty fields don't deserialize correctly
    // when wrapped in Property<>. Other Kestra messaging plugins (AMQP, Solace) avoid nested
    // config objects entirely, using flat Property<String> fields instead.
    @PluginProperty
    @NotNull
    @Schema(title = "The destination to send messages to.")
    private JMSDestination destination;

    @Builder.Default
    @Schema(title = "The JMS priority used to send the message (default: 4)")
    private Property<Integer> priority = Property.ofValue(4);

    @Builder.Default
    @Schema(title = "The JMS delivery mode used to send the message (default: 2 = PERSISTENT)")
    private Property<Integer> deliveryMode = Property.ofValue(2);

    @Builder.Default
    @Schema(title = "The time to live of the sent message in milliseconds (default: 0 = does not expire)")
    private Property<Long> timeToLive = Property.ofValue(0L);

    @NotNull
    private Object from;

    @Builder.Default
    @Schema(
            title = "The serialization format for the message body.",
            description = "Determines how message bodies are serialized. STRING for text messages, JSON for JSON-formatted text, BYTES for binary data.",
            defaultValue = "STRING"
    )
    private SerdeType serdeType = SerdeType.STRING;

    /**
     * The main execution method for the task, called by the Kestra runner.
     * It orchestrates the entire process of connecting to the broker, preparing the
     * messages, and publishing them to the specified destination.
     *
     * @param runContext The context for the current task run.
     * @return An Output object containing the total count of messages sent.
     * @throws Exception if an error occurs during the process.
     */
    @Override
    public Output run(RunContext runContext) throws Exception {
        int messageCount;
        Logger logger = runContext.logger();
        String rDestName = runContext.render(this.destination.getDestinationName());

        try (
                ConnectionAdapter connection = this.createConnection(runContext);
                SessionAdapter session = (SessionAdapter) connection.createSession()
        ) {
            String destType = this.destination.getDestinationType() == AbstractDestination.DestinationType.QUEUE ?
                    SessionAdapter.QUEUE : SessionAdapter.TOPIC;
            String destinationUrl = String.format("%s://%s", destType, rDestName);
            AbstractDestination jmsDestination = session.createDestination(destinationUrl);

            try (ProducerAdapter producer = (ProducerAdapter) session.createProducer(jmsDestination)) {
                Flux<JMSMessage> messages = this.processFrom(runContext);
                messageCount = messages
                        .map(message -> {
                            try {
                                this.send(session, producer, message, runContext);
                                logger.debug(
                                    "Successfully sent JMS message to {}",
                                    destinationUrl
                                );
                                return 1;
                            } catch (Exception e) {
                                logger.error("Failed to send JMS message", e);
                                throw new RuntimeException(e);
                            }
                        })
                        .reduce(0, Integer::sum)
                        .blockOptional()
                        .orElse(0);
            }
        }

        runContext.metric(Counter.of("records", messageCount, "destination", rDestName));
        return Output.builder().messagesCount(messageCount).build();
    }

    /**
     * Serializes a single Kestra JMSMessage into a provider-specific JMS message
     * and sends it using the given producer. It now supports creating different
     * message types based on the SerdeType.
     *
     * @param session    The active AbstractSession, used to create the JMS message.
     * @param producer   The active AbstractProducer used to send the message.
     * @param message    The Kestra JMSMessage to be sent.
     * @param runContext The Kestra run context
     * @throws Exception if serialization or sending fails.
     */
    private void send(AbstractSession session, AbstractProducer producer, JMSMessage message, RunContext runContext) throws Exception {

        AbstractMessage jmsMessage = switch (this.serdeType) {
            case STRING -> {
                String stringBody = message.getData() != null ? message.getData().toString() : null;
                // Assuming createTextMessage exists or is added to SessionAdapter
                yield session.createTextMessage(stringBody, message.getHeaders());
            }
            case JSON -> {
                String jsonBody = JacksonMapper.ofJson().writeValueAsString(message.getData());
                // Use the same TextMessage creation for JSON strings
                yield session.createTextMessage(jsonBody, message.getHeaders());
            }
            case BYTES -> {
                byte[] byteBody = this.serdeType.serialize(message.getData());
                // Use the new createBytesMessage method
                yield session.createBytesMessage(byteBody, message.getHeaders());
            }
            default -> throw new IllegalStateException("Unexpected SerdeType: " + this.serdeType);
        };

        var rDeliveryMode = runContext.render(deliveryMode).as(Integer.class).orElseThrow();
        var rPriority = runContext.render(priority).as(Integer.class).orElseThrow();
        var rTimeToLive = runContext.render(timeToLive).as(Long.class).orElseThrow();

        producer.send(jmsMessage, rDeliveryMode, rPriority, rTimeToLive);
    }

    /**
     * Processes the 'from' property, handling both plain strings and structured data.
     * Plain strings are wrapped in JMSMessage objects, while structured data (Maps, Lists,
     * JSON strings, URIs) is processed through Data.From.
     *
     * @param runContext The Kestra run context for rendering and storage access.
     * @return A Flux of JMSMessage objects ready to be published.
     * @throws Exception if the 'from' data is invalid or cannot be processed.
     */
    private Flux<JMSMessage> processFrom(RunContext runContext) throws Exception {
        // Handle plain strings separately (not valid JSON)
        if (from instanceof String str) {
            String rendered = runContext.render(str);
            // Check if it's structured data (JSON/URI) vs plain text
            if (!rendered.startsWith("{") && !rendered.startsWith("[") &&
                !rendered.contains("://")) {
                // Plain string - wrap in JMSMessage
                return Flux.just(JMSMessage.builder().data(rendered).build());
            }
        }

        // Use Data.From for structured data (Maps, Lists, JSON, URIs)
        return Data.from(from)
            .readAs(runContext, JMSMessage.class, map -> JacksonMapper.ofIon().convertValue(map, JMSMessage.class));
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Number of messages published.")
        private final Integer messagesCount;
    }
}
