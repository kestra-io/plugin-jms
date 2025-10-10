package io.kestra.plugin.jms;

import at.conapi.oss.jms.adapter.AbstractDestination;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
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
    @Builder.Default
    private Property<AbstractDestination.DestinationType> destinationType = Property.ofValue(AbstractDestination.DestinationType.QUEUE);
}