package io.kestra.plugin.jms;

import at.conapi.oss.jms.adapter.impl.ConnectionAdapter;
import at.conapi.oss.jms.adapter.impl.ConnectionFactoryAdapter;
import io.kestra.plugin.jms.configuration.ConnectionFactoryConfig;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
public abstract class AbstractJmsTask extends Task {

    // NOTE: Using @PluginProperty instead of Property<ConnectionFactoryConfig> wrapper.
    // Polymorphic configuration objects with @JsonTypeInfo/@JsonSubTypes don't deserialize correctly
    // when wrapped in Property<>. Jackson cannot resolve the type discriminator ('type' field)
    // during Property deserialization, causing "missing type id property 'type'" errors.
    @PluginProperty
    @Schema(
            title = "Connection factory configuration.",
            description = "Configuration for connecting to the JMS broker. Supports both direct connection factory instantiation and JNDI lookup."
    )
    private ConnectionFactoryConfig connectionFactoryConfig;

    /**
     * Creates a JMS connection using the provided configuration but does NOT start it.
     * Subclasses are responsible for starting the connection after setting up consumers.
     *
     * @param runContext The Kestra RunContext.
     * @return A new, unstarted JMS connection adapter.
     * @throws Exception if the connection cannot be established.
     */
    protected ConnectionAdapter createConnection(RunContext runContext) throws Exception {
        JMSConnectionFactory factoryService = new JMSConnectionFactory();

        // 1. Delegate factory creation to the service
        ConnectionFactoryAdapter factory = factoryService.create(runContext, this.connectionFactoryConfig);

        // 2. Use the factory to create the connection with credentials from the config
        String rUsername = this.connectionFactoryConfig.getUsername() != null ? runContext.render(this.connectionFactoryConfig.getUsername()) : null;
        String rPassword = this.connectionFactoryConfig.getPassword() != null ? runContext.render(this.connectionFactoryConfig.getPassword()) : null;

        ConnectionAdapter connection;
        if (rUsername != null) {
            connection = (ConnectionAdapter) factory.createConnection(rUsername, rPassword);
        } else {
            connection = (ConnectionAdapter) factory.createConnection();
        }

        // The connection is NOT started here. Subclasses must call connection.start()
        // after they have set up any necessary MessageConsumers.
        return connection;
    }

    /**
     * Utility method to safely close JMS resources without throwing exceptions.
     * @param closeable The JMS resource (Connection, Session, etc.) to close.
     */
    protected void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                // Ignore exceptions on close
            }
        }
    }
}
