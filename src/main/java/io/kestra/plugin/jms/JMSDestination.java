package io.kestra.plugin.jms;

import at.conapi.plugins.common.endpoints.jms.adapter.AbstractDestination;
import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class JMSDestination
{
    @Schema(title = "The name of the JMS queue or topic.")
    @PluginProperty(dynamic = true)
    @NotNull
    private String destinationName;

    @Schema(title = "The type of the destination.", defaultValue = "QUEUE")
    @PluginProperty
    @Builder.Default
    private AbstractDestination.DestinationType destinationType = AbstractDestination.DestinationType.QUEUE;
}