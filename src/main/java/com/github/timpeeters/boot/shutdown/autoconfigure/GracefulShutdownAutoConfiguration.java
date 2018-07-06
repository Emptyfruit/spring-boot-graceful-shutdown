package com.github.timpeeters.boot.shutdown.autoconfigure;

import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;
import org.springframework.boot.context.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@ConditionalOnProperty(havingValue = "true", prefix = "graceful.shutdown", name = "enabled")
@ConditionalOnWebApplication
@Configuration
@EnableConfigurationProperties(GracefulShutdownProperties.class)
public class GracefulShutdownAutoConfiguration {
    @Bean
    public HealthIndicator gracefulShutdownHealthIndicator(
            ApplicationContext ctx, GracefulShutdownProperties props) {

        return new GracefulShutdownHealthIndicator(ctx, props);
    }

    @Bean
    public EmbeddedServletContainerCustomizer gracefulShutdownTomcatContainerCustomizer(
            ApplicationContext ctx, GracefulShutdownProperties props) {

        return container -> {
            if (container instanceof TomcatEmbeddedServletContainerFactory) {
                ((TomcatEmbeddedServletContainerFactory) container)
                        .addConnectorCustomizers(gracefulShutdownTomcatConnectorCustomizer(ctx, props));
            }
        };
    }

    @Bean
    public TomcatConnectorCustomizer gracefulShutdownTomcatConnectorCustomizer(
            ApplicationContext ctx, GracefulShutdownProperties props) {

        return new GracefulShutdownTomcatConnectorCustomizer(ctx, props);
    }
}
