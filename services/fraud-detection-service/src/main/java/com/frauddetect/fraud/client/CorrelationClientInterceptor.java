package com.frauddetect.fraud.client;

import com.frauddetect.common.correlation.CorrelationContext;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/**
 * Client-side gRPC interceptor that copies the current correlation id into the {@code
 * x-correlation-id} metadata header, matching risk-scoring-service's server interceptor. This is how
 * the end-to-end correlation id survives the REST → Kafka → gRPC hops.
 */
public class CorrelationClientInterceptor implements ClientInterceptor {

    static final Metadata.Key<String> CORRELATION_KEY =
            Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                String correlationId = CorrelationContext.getCorrelationId();
                if (correlationId != null && !correlationId.isBlank()) {
                    headers.put(CORRELATION_KEY, correlationId);
                }
                super.start(responseListener, headers);
            }
        };
    }
}
