package com.tubadev.receivables.infrastructure.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param dispatchConcurrency max in-flight sends per campaign (Cloud API default throughput is 80 msg/s)
 */
@ConfigurationProperties(prefix = "campaign")
public record CampaignProperties(@DefaultValue("20") int dispatchConcurrency) {
}
