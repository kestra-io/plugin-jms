package io.kestra.plugin.jms;

import io.kestra.plugin.jms.configuration.ConnectionFactoryConfig;
import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public interface JMSConnectionInterface {
    @Schema(
            title = "Connection Factory Configuration",
            description = "Defines how to obtain the JMS ConnectionFactory, either by direct class instantiation or via a JNDI lookup."
    )
    @PluginProperty
    @NotNull
    ConnectionFactoryConfig getConnectionFactoryConfig();

    @Schema(title = "The username for authentication.")
    @PluginProperty(dynamic = true)
    String getUsername();

    @Schema(title = "The password for authentication.")
    @PluginProperty(dynamic = true)
    String getPassword();
}