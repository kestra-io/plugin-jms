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
            title = "Provider JAR paths",
            description = "One or more paths to the JMS provider JARs (e.g., file:///app/plugins/jms-libs/client.jar). If unset, all JARs under the plugins/jms-libs folder are added to the classpath."
    )
    @PluginProperty(dynamic = true)
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) // Allow single string in YAML
    private List<String> providerJarPaths;

    @Schema(
            title = "Username for broker authentication",
            description = "Rendered username used when creating the connection. Omit for JNDI if the ConnectionFactory already embeds credentials."
    )
    @PluginProperty(dynamic = true)
    private String username;

    @Schema(
            title = "Password for broker authentication",
            description = "Rendered password used when creating the connection. Omit for JNDI if the ConnectionFactory already embeds credentials."
    )
    @PluginProperty(dynamic = true)
    private String password;

    @Schema(
            title = "Connection properties",
            description = "Additional POJO properties applied to the Direct or JNDI ConnectionFactory instance."
    )
    @PluginProperty(dynamic =true)
    private Map<String, String> connectionProperties;

    @Builder.Default
    @Schema(
            title = "Use filtered classloader",
            description = "Enable only for providers that ship JMS API classes (e.g., SonicMQ/Aurea) to avoid ClassCastException by loading JMS APIs from the parent classloader. Leave disabled for typical providers like RabbitMQ, ActiveMQ, Artemis.",
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
                title = "ConnectionFactory class",
                description = "Fully qualified class name of the JMS ConnectionFactory implementation.",
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
                title = "JNDI initial context factory",
                example = "org.wildfly.naming.client.WildFlyInitialContextFactory"
        )
        @NotNull
        private Property<String> jndiInitialContextFactory;

        @Schema(title = "JNDI provider URL", example = "remote+http://localhost:8080")
        @NotNull
        private Property<String> jndiProviderUrl;

        @Schema(title = "JNDI principal", example = "Administrator")
        private Property<String> jndiPrincipal;

        @Schema(title = "JNDI credentials", example = "password")
        private Property<String> jndiCredentials;


        @Schema(title = "JNDI ConnectionFactory name", example = "jms/RemoteConnectionFactory")
        @NotNull
        private Property<String> jndiConnectionFactoryName;

        // This is a hack to make JavaDoc working as annotation processor didn't run before JavaDoc.
        public static abstract class JndiBuilder<C extends Jndi, B extends JndiBuilder<C, B>> extends ConnectionFactoryConfigBuilder<C, B> {
        }

        @JsonPOJOBuilder(withPrefix = "")
        public static class JndiBuilderImpl extends Jndi.JndiBuilder<Jndi, JndiBuilderImpl> {}
    }
}
