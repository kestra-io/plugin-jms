package io.kestra.plugin.jms.serde;

import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Schema(title = "Serializer / Deserializer used for the message body.")
public enum SerdeType {
    STRING,
    JSON,
    BYTES;

    /**
     * Deserializes a byte array into an object based on the enum type.
     *
     * @param message The raw byte array from the JMS message.
     * @return The deserialized object.
     * @throws IOException If JSON parsing fails.
     */
    public Object deserialize(byte[] message) throws IOException {
        if (message == null) {
            return null;
        }

        return switch (this) {
            case JSON -> JacksonMapper.ofJson(false).readValue(message, Object.class);
            case STRING -> new String(message, StandardCharsets.UTF_8);
            case BYTES -> message;
        };
    }

    /**
     * Serializes an object into a byte array based on the enum type.
     *
     * @param data The object to serialize.
     * @return The resulting byte array.
     * @throws IOException If JSON serialization fails.
     */
    public byte[] serialize(Object data) throws IOException {
        if (data == null) {
            return null;
        }

        return switch (this) {
            case JSON -> JacksonMapper.ofJson(false).writeValueAsBytes(data);
            case STRING -> data.toString().getBytes(StandardCharsets.UTF_8);
            case BYTES -> {
                if (!(data instanceof byte[])) {
                    throw new IllegalArgumentException("For BYTES SerdeType, the provided message body must be a byte array.");
                }
                yield (byte[]) data;
            }
        };
    }
}
