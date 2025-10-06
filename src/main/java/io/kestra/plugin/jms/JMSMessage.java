package io.kestra.plugin.jms;

import at.conapi.plugins.common.endpoints.jms.adapter.AbstractDestination;
import at.conapi.plugins.common.endpoints.jms.adapter.AbstractJMSException;
import at.conapi.plugins.common.endpoints.jms.adapter.AbstractMessage;
import io.kestra.plugin.jms.serde.SerdeType;
import io.kestra.core.models.tasks.Output;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a message consumed from or produced to a JMS broker.
 * This class is a Kestra-friendly representation of a standard JMS message.
 */
@Getter
@Builder
public final class JMSMessage implements Output {
    @Schema(title = "The message's content type.")
    private final String contentType;

    @Schema(title = "The message's content encoding.")
    private final String contentEncoding;

    @Schema(title = "A map of message headers (JMS properties).")
    private final Map<String, Object> headers;

    @Schema(title = "The JMS delivery mode (1 for non-persistent, 2 for persistent).")
    private final Integer deliveryMode;

    @Schema(title = "The message priority level.")
    private final Integer priority;

    @Schema(title = "The unique JMS Message ID.")
    private final String messageId;

    @Schema(title = "The JMS Correlation ID.")
    private final String correlationId;

    @Schema(title = "The name of the destination to which a reply should be sent.")
    private final String replyTo;

    @Schema(title = "The type of the replyTo destination (QUEUE or TOPIC).")
    private final AbstractDestination.DestinationType replyToType;

    @Schema(title = "The duration until the message expires.")
    private final Duration expiration;

    @Schema(title = "The timestamp when the message was sent.")
    private final Instant timestamp;

    @Schema(title = "The message type identifier.")
    private final String type;

    @Schema(title = "The deserialized message body.")
    private final Object data;

    /**
     * Creates a JMSMessage from the AbstractMessage
     *
     * @param msg The source JMS message (AbstractMessage)
     * @param serdeType  The serialization/deserialization format for the message body.
     * @return A new AbstractMessage instance.
     * @throws Exception if an error occurs during message processing.
     */
    public static JMSMessage of(AbstractMessage msg, SerdeType serdeType) throws Exception {
        byte[] body = getBodyAsBytes(msg);
        Object data = serdeType.deserialize(body);
        AbstractDestination replyToDestination = msg.getJMSReplyTo();

        return JMSMessage.builder()
                .data(data)
                .contentType(msg.getJMSType())
                .headers(extractProperties(msg))
                .deliveryMode(msg.getJMSDeliveryMode())
                .priority(msg.getJMSPriority())
                .messageId(msg.getJMSMessageID())
                .correlationId(msg.getJMSCorrelationID())
                .replyTo(getDestinationName(replyToDestination))
                .replyToType(getDestinationType(replyToDestination))
                .expiration(getExpiration(msg))
                .timestamp(Instant.ofEpochMilli(msg.getJMSTimestamp()))
                .type(msg.getJMSType())
                .contentEncoding(getStringProperty(msg, "contentEncoding"))
                .build();
    }

    /**
     * Extracts the correct name from a JMS Destination object.
     * For temporary destinations, it preserves the full unique name.
     * For regular destinations, it returns the clean name.
     *
     * @param destination The JMS destination.
     * @return The queue or topic name as a string.
     * @throws AbstractJMSException if an error occurs.
     */
    private static String getDestinationName(AbstractDestination destination) throws AbstractJMSException {
        if (destination == null) {
            return null;
        }
        // For temporary destinations, toString() provides the full identifier needed by the producer.
        if (destination.isTemporaryDestination()) {
            return destination.toString();
        }
        // For regular destinations, get the clean name.
        return destination.getDestinationName();
    }

    /**
     * Extracts the type (QUEUE or TOPIC) from a JMS Destination object.
     *
     * @param destination The JMS destination.
     * @return The DestinationType enum.
     */
    private static AbstractDestination.DestinationType getDestinationType(AbstractDestination destination) throws AbstractJMSException
    {
        if (destination == null) {
            return null;
        }

        return destination.getDestinationType();
    }

    private static byte[] getBodyAsBytes(AbstractMessage msg) throws AbstractJMSException
    {
        if (msg.isTextMessageInstance()) {
            String text = msg.getText();
            return text != null ? text.getBytes(java.nio.charset.StandardCharsets.UTF_8) : null;
        }
        if (msg.isBytesMessageInstance()) {
            return msg.getByteArray();
        }
        throw new IllegalArgumentException("Unsupported JMS message type: " + msg.getClass().getName());
    }

    private static String getStringProperty(AbstractMessage msg, String key) {
        try {
            return msg.getStringProperty(key);
        } catch (AbstractJMSException e) {
            return null;
        }
    }

    private static Map<String, Object> extractProperties(AbstractMessage msg) {
        try {
            Map<String, Object> properties = new HashMap<>();
            Enumeration<?> propertyNames = msg.getPropertyNames();
            if (propertyNames == null) {
                return Collections.emptyMap();
            }
            while (propertyNames.hasMoreElements()) {
                String name = (String) propertyNames.nextElement();
                properties.put(name, msg.getObjectProperty(name));
            }
            return properties;
        } catch (AbstractJMSException e) {
            return Collections.emptyMap();
        }
    }

    private static Duration getExpiration(AbstractMessage msg) {
        try {
            long expiration = msg.getJMSExpiration();
            if (expiration > 0) {
                // expiration is an absolute timestamp, so we get the duration from now
                return Duration.ofMillis(expiration - System.currentTimeMillis());
            }
            return null;
        } catch (AbstractJMSException e) {
            return null;
        }
    }
}
