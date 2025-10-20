package io.kestra.plugin.jms.configuration;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ConnectionFactoryConfig.Direct.class, name = "DIRECT"),
        @JsonSubTypes.Type(value = ConnectionFactoryConfig.Jndi.class, name = "JNDI")
})
@Getter
@SuperBuilder // Use SuperBuilder on the base class
public abstract class ConnectionFactoryConfig {

    @Schema(
            title = "Provider JAR Path(s)",
            description = "The path to the JMS provider's JAR file(s). This can be a single path or a list of paths (JARs). " +
                    "(e.g., 'file:///app/plugins/jms-libs/client.jar')." +
                    "If not specified, all jar files in the 'jms-libs' sub folder of your plugins location will be added to the classpath."
    )
    @PluginProperty(dynamic = true)
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) // Allow single string in YAML
    private List<String> providerJarPaths;

    @Schema(
            title = "The username for broker authentication.",
            description = "This is used when creating the connection to the JMS broker. Omit for JNDI if credentials are embedded in the ConnectionFactory."
    )
    @PluginProperty(dynamic = true)
    private String username;

    @Schema(
            title = "The password for broker authentication.",
            description = "This is used when creating the connection to the JMS broker. Omit for JNDI if credentials are embedded in the ConnectionFactory."
    )
    @PluginProperty(dynamic = true)
    private String password;

    @Schema(
            title = "Connection Properties",
            description = "Additional (Pojo) properties to set on the Direct/JNDI ConnectionFactory instance."
    )
    @PluginProperty(dynamic =true)
    private Map<String, String> connectionProperties;

    @Builder.Default
    @Schema(
            title = "Use Filtered ClassLoader",
            description = "Enable this for JMS providers that bundle JMS API classes in their JAR (e.g., SonicMQ/Aurea Messenger). " +
                    "When enabled, JMS API classes are loaded from the parent classloader to prevent ClassCastException. " +
                    "Leave disabled (default: false) for well-behaved providers like RabbitMQ, ActiveMQ, Artemis, etc. " +
                    "Only enable this if you encounter ClassCastException with javax.jms or jakarta.jms classes.",
            defaultValue = "false"
    )
    private Property<Boolean> useFilteredClassLoader = Property.ofValue(false);

    // This is a hack to make JavaDoc working as annotation processor didn't run before JavaDoc.
    // See https://stackoverflow.com/questions/51947791/javadoc-cannot-find-symbol-error-when-using-lomboks-builder-annotation
    public static abstract class ConnectionFactoryConfigBuilder<C extends ConnectionFactoryConfig, B extends ConnectionFactoryConfigBuilder<C, B>> {
    }

    // --- Nested subclasses ---

    @Getter
    @SuperBuilder
    @JsonDeserialize(builder = Direct.DirectBuilderImpl.class)
    public static final class Direct extends ConnectionFactoryConfig {
        @Schema(
                title = "Connection Factory Class",
                description = "The fully qualified class name of the JMS ConnectionFactory.",
                examples = {"org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory"}
        )
        @NotNull
        private Property<String> connectionFactoryClass;

        // This is a hack to make JavaDoc working as annotation processor didn't run before JavaDoc.
        public static abstract class DirectBuilder<C extends Direct, B extends DirectBuilder<C, B>> extends ConnectionFactoryConfigBuilder<C, B> {
        }

        @JsonPOJOBuilder(withPrefix = "")
        public static class DirectBuilderImpl extends Direct.DirectBuilder<Direct, DirectBuilderImpl> {}
    }

    @Getter
    @SuperBuilder
    @JsonDeserialize(builder = Jndi.JndiBuilderImpl.class)
    public static final class Jndi extends ConnectionFactoryConfig {
        @Schema(
                title = "JNDI Initial Context Factory",
                example = "org.wildfly.naming.client.WildFlyInitialContextFactory"
        )
        @NotNull
        private Property<String> jndiInitialContextFactory;

        @Schema(title = "JNDI Provider URL", example = "remote+http://localhost:8080")
        @NotNull
        private Property<String> jndiProviderUrl;

        @Schema(title = "JNDI Principal", example = "Administrator")
        private Property<String> jndiPrincipal;

        @Schema(title = "JNDI Credentials", example = "password")
        private Property<String> jndiCredentials;


        @Schema(title = "JNDI Connection Factory Name", example = "jms/RemoteConnectionFactory")
        @NotNull
        private Property<String> jndiConnectionFactoryName;

        // This is a hack to make JavaDoc working as annotation processor didn't run before JavaDoc.
        public static abstract class JndiBuilder<C extends Jndi, B extends JndiBuilder<C, B>> extends ConnectionFactoryConfigBuilder<C, B> {
        }

        @JsonPOJOBuilder(withPrefix = "")
        public static class JndiBuilderImpl extends Jndi.JndiBuilder<Jndi, JndiBuilderImpl> {}
    }
}
