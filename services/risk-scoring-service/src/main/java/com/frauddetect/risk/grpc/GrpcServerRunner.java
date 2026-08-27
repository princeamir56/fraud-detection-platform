package com.frauddetect.risk.grpc;

import com.frauddetect.risk.config.GrpcServerProperties;
import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.protobuf.services.ProtoReflectionServiceV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Owns the lifecycle of the embedded gRPC {@link Server}. Implemented as a Spring
 * {@link SmartLifecycle} bean using only stable core grpc-java APIs, so it is not coupled to any
 * fast-moving auto-configuration starter. Binds every {@link BindableService} in the context plus
 * standard health + reflection services, and shuts down gracefully.
 */
@Component
public class GrpcServerRunner implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerRunner.class);

    private final GrpcServerProperties properties;
    private final List<BindableService> services;
    private final HealthStatusManager healthStatusManager = new HealthStatusManager();

    private Server server;
    private volatile boolean running = false;

    public GrpcServerRunner(GrpcServerProperties properties, List<BindableService> services) {
        this.properties = properties;
        this.services = services;
    }

    @Override
    public void start() {
        ServerBuilder<?> builder = ServerBuilder.forPort(properties.port())
                .maxInboundMessageSize(properties.maxInboundMessageBytes())
                .intercept(new CorrelationServerInterceptor())
                .addService(healthStatusManager.getHealthService())
                .addService(ProtoReflectionServiceV1.newInstance());

        services.forEach(builder::addService);

        try {
            this.server = builder.build().start();
            this.running = true;
            healthStatusManager.setStatus("", io.grpc.health.v1.HealthCheckResponse.ServingStatus.SERVING);
            log.info("gRPC server started on port {} with {} business service(s)", properties.port(), services.size());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start gRPC server on port " + properties.port(), e);
        }
    }

    @Override
    public void stop() {
        if (server == null) {
            return;
        }
        log.info("Shutting down gRPC server (grace {}s)", properties.shutdownGraceSeconds());
        healthStatusManager.enterTerminalState();
        server.shutdown();
        try {
            if (!server.awaitTermination(properties.shutdownGraceSeconds(), TimeUnit.SECONDS)) {
                server.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            server.shutdownNow();
        } finally {
            running = false;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // Start relatively late / stop relatively early so dependencies are ready.
        return Integer.MAX_VALUE - 100;
    }
}
