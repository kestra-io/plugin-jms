package io.kestra.plugin.jms;

import at.conapi.oss.jms.adapter.AbstractDestination;
import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class JMSDestination
{
    @Schema(title = "Destination name", description = "Rendered JMS queue or topic name")
    @PluginProperty(dynamic = true)
    @NotNull
    private String destinationName;

    @Schema(title = "Destination type", description = "QUEUE or TOPIC", defaultValue = "QUEUE")
    @PluginProperty
    @Builder.Default
    private AbstractDestination.DestinationType destinationType = AbstractDestination.DestinationType.QUEUE;
}
