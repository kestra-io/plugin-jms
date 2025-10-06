package io.kestra.plugin.jms;

import at.conapi.plugins.common.endpoints.jms.adapter.impl.ConnectionAdapter;
import at.conapi.plugins.common.endpoints.jms.adapter.impl.ConnectionFactoryAdapter;
import io.kestra.plugin.jms.configuration.ConnectionFactoryConfig;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
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

    @PluginProperty
    private ConnectionFactoryConfig connectionFactoryConfig; // The ONLY connection property

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
        String username = connectionFactoryConfig.getUsername() != null ? runContext.render(connectionFactoryConfig.getUsername()) : null;
        String password = connectionFactoryConfig.getPassword() != null ? runContext.render(connectionFactoryConfig.getPassword()) : null;

        ConnectionAdapter connection;
        if (username != null) {
            connection = (ConnectionAdapter) factory.createConnection(username, password);
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
