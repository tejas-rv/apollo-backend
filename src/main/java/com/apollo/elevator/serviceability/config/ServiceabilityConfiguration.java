package com.apollo.elevator.serviceability.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ServiceabilityProperties.class)
public class ServiceabilityConfiguration {
}
