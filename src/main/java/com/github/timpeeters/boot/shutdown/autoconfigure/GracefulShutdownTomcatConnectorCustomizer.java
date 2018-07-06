package com.github.timpeeters.boot.shutdown.autoconfigure;

import org.apache.catalina.connector.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class GracefulShutdownTomcatConnectorCustomizer implements TomcatConnectorCustomizer {
    private static final Logger LOG = LoggerFactory.getLogger(GracefulShutdownTomcatConnectorCustomizer.class);

    private static final int CHECK_INTERVAL = 10;

    private final ApplicationContext applicationContext;
    private final GracefulShutdownProperties props;

    private Connector connector;

    public GracefulShutdownTomcatConnectorCustomizer(ApplicationContext ctx, GracefulShutdownProperties props) {
        this.applicationContext = ctx;
        this.props = props;
    }

    @Override
    public void customize(Connector connector) {
        this.connector = connector;
    }

    @EventListener(ContextClosedEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public void contextClosed(ContextClosedEvent event) {
        if (connector == null) {
            return;
        }

        if (isEventFromLocalContext(event)) {
            stopAcceptingNewRequests();
            getThreadPoolExecutor().ifPresent(this::shutdownThreadPoolExecutor);
        }
    }

    private void stopAcceptingNewRequests() {
        connector.pause();

        LOG.info("Paused {} to stop accepting new requests", connector);
    }

    private void shutdownThreadPoolExecutor(ThreadPoolExecutor executor) {
        executor.shutdown();
        awaitTermination(executor);
    }

    private void awaitTermination(ThreadPoolExecutor executor) {
        for (int remaining = props.getTimeout(); remaining > 0; remaining -= CHECK_INTERVAL) {
            if (tryAwaitTermination(executor)) {
                return;
            }

            LOG.info("{} thread(s) active, {} seconds remaining", executor.getActiveCount(), remaining);
        }

        logMessageIfThereAreStillActiveThreads(executor);
    }

    private void logMessageIfThereAreStillActiveThreads(ThreadPoolExecutor executor) {
        if (executor.getActiveCount() > 0) {
            LOG.warn("{} thread(s) still active, force shutdown", executor.getActiveCount());
        }
    }

    private boolean tryAwaitTermination(ThreadPoolExecutor executor) {
        try {
            return executor.awaitTermination(CHECK_INTERVAL, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOG.warn("Interrupted while waiting for termination");
        }

        return false;
    }

    private Optional<ThreadPoolExecutor> getThreadPoolExecutor() {
        Executor executor = connector.getProtocolHandler().getExecutor();

        if (executor instanceof ThreadPoolExecutor) {
            return Optional.of((ThreadPoolExecutor) executor);
        }

        return Optional.empty();
    }

    private boolean isEventFromLocalContext(ContextClosedEvent event) {
        return event.getApplicationContext().equals(applicationContext);
    }
}
