package io.kestra.plugin.jms;

import at.conapi.oss.jms.adapter.impl.ConnectionAdapter;
import at.conapi.oss.jms.adapter.impl.ConnectionFactoryAdapter;
import io.kestra.plugin.jms.configuration.ConnectionFactoryConfig;
import io.kestra.core.models.property.Property;
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

    @Schema(
            title = "Connection factory configuration.",
            description = "Configuration for connecting to the JMS broker. Supports both direct connection factory instantiation and JNDI lookup."
    )
    private Property<ConnectionFactoryConfig> connectionFactoryConfig;

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

        // 1. Render the connectionFactoryConfig Property
        ConnectionFactoryConfig rConnectionFactoryConfig = runContext.render(this.connectionFactoryConfig).as(ConnectionFactoryConfig.class).orElseThrow();

        // 2. Delegate factory creation to the service
        ConnectionFactoryAdapter factory = factoryService.create(runContext, rConnectionFactoryConfig);

        // 3. Use the factory to create the connection with credentials from the config
        String rUsername = rConnectionFactoryConfig.getUsername() != null ? runContext.render(rConnectionFactoryConfig.getUsername()) : null;
        String rPassword = rConnectionFactoryConfig.getPassword() != null ? runContext.render(rConnectionFactoryConfig.getPassword()) : null;

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
