package com.frauddetect.risk.grpc;

import com.frauddetect.common.correlation.CorrelationContext;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

/**
 * Reads the {@code x-correlation-id} gRPC metadata header and binds it to the logging MDC for the
 * duration of the call, so risk-scoring log lines stitch into the same end-to-end trace as the
 * calling fraud-detection-service. Cleared after the call completes.
 */
public class CorrelationServerInterceptor implements ServerInterceptor {

    static final Metadata.Key<String> CORRELATION_KEY =
            Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        String correlationId = headers.get(CORRELATION_KEY);
        CorrelationContext.setCorrelationId(
                correlationId != null && !correlationId.isBlank() ? correlationId : CorrelationContext.newId());

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(next.startCall(call, headers)) {
            @Override
            public void onComplete() {
                try {
                    super.onComplete();
                } finally {
                    CorrelationContext.clear();
                }
            }

            @Override
            public void onCancel() {
                try {
                    super.onCancel();
                } finally {
                    CorrelationContext.clear();
                }
            }
        };
    }
}
