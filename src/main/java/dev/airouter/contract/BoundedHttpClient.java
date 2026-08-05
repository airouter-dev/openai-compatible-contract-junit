package dev.airouter.contract;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Small JDK-only HTTP client that refuses redirects and bounds response
 * bodies before the contract parser sees them.
 */
final class BoundedHttpClient {
    private final ContractConfig config;
    private final HttpClient client;

    BoundedHttpClient(ContractConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.client = HttpClient.newBuilder()
                .connectTimeout(config.timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    ContractHttpResponse get(String path)
            throws IOException, InterruptedException, ContractViolationException {
        HttpRequest.Builder request = baseRequest(config.endpoint(path))
                .GET()
                .header("Accept", "application/json");
        return send(request.build());
    }

    ContractHttpResponse postJson(String path, String body, String accept)
            throws IOException, InterruptedException, ContractViolationException {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(accept, "accept");
        HttpRequest.Builder request = baseRequest(config.endpoint(path))
                .header("Accept", accept)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        return send(request.build());
    }

    private HttpRequest.Builder baseRequest(java.net.URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(config.timeout())
                .header("User-Agent", "openai-compatible-contract-junit/0.1.0");
        String token = config.bearerToken();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private ContractHttpResponse send(HttpRequest request)
            throws IOException, InterruptedException, ContractViolationException {
        BoundedBodyHandler bodyHandler = new BoundedBodyHandler(config.maxResponseBytes());
        CompletableFuture<HttpResponse<byte[]>> requestFuture =
                client.sendAsync(request, bodyHandler);
        try {
            long timeoutNanos = timeoutNanos(config.timeout());
            HttpResponse<byte[]> response = requestFuture.get(timeoutNanos, TimeUnit.NANOSECONDS);
            return new ContractHttpResponse(
                    response.statusCode(), response.headers(), response.body());
        } catch (TimeoutException exception) {
            bodyHandler.cancel();
            requestFuture.cancel(true);
            throw new ContractViolationException("request timed out", exception);
        } catch (InterruptedException exception) {
            bodyHandler.cancel();
            requestFuture.cancel(true);
            throw exception;
        } catch (ExecutionException exception) {
            throwMapped(exception.getCause());
            throw new AssertionError("unreachable");
        }
    }

    private static long timeoutNanos(java.time.Duration timeout) {
        try {
            return Math.max(1L, timeout.toNanos());
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static void throwMapped(Throwable failure)
            throws IOException, ContractViolationException {
        Throwable cause = failure;
        while (cause instanceof java.util.concurrent.CompletionException
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof ContractViolationException violation) {
            throw violation;
        }
        if (cause instanceof IOException ioException) {
            throw ioException;
        }
        if (cause instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new ContractViolationException(
                "request failed (" + cause.getClass().getSimpleName() + ")", cause);
    }

    private static final class BoundedBodyHandler implements HttpResponse.BodyHandler<byte[]> {
        private final int maximum;
        private volatile BoundedBodySubscriber subscriber;

        private BoundedBodyHandler(int maximum) {
            this.maximum = maximum;
        }

        @Override
        public HttpResponse.BodySubscriber<byte[]> apply(HttpResponse.ResponseInfo responseInfo) {
            BoundedBodySubscriber created = new BoundedBodySubscriber(maximum);
            subscriber = created;
            return created;
        }

        private void cancel() {
            BoundedBodySubscriber current = subscriber;
            if (current != null) {
                current.cancel();
            }
        }
    }

    private static final class BoundedBodySubscriber
            implements HttpResponse.BodySubscriber<byte[]> {
        private final int maximum;
        private final ByteArrayOutputStream bytes;
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private volatile Flow.Subscription subscription;
        private int received;

        private BoundedBodySubscriber(int maximum) {
            this.maximum = maximum;
            this.bytes = new ByteArrayOutputStream(Math.min(maximum, 8_192));
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription newSubscription) {
            Objects.requireNonNull(newSubscription, "subscription");
            if (subscription != null) {
                newSubscription.cancel();
                return;
            }
            subscription = newSubscription;
            newSubscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            if (body.isDone()) {
                return;
            }
            for (ByteBuffer item : items) {
                int chunkSize = item.remaining();
                if ((long) received + chunkSize > maximum) {
                    fail(new ContractViolationException(
                            "response exceeds configured byte bound"));
                    return;
                }
                byte[] chunk = new byte[chunkSize];
                item.get(chunk);
                bytes.writeBytes(chunk);
                received += chunkSize;
            }
            Flow.Subscription current = subscription;
            if (current != null && !body.isDone()) {
                current.request(1);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(Objects.requireNonNull(throwable, "throwable"));
        }

        @Override
        public void onComplete() {
            body.complete(bytes.toByteArray());
        }

        private void fail(ContractViolationException exception) {
            Flow.Subscription current = subscription;
            if (current != null) {
                current.cancel();
            }
            body.completeExceptionally(exception);
        }

        private void cancel() {
            Flow.Subscription current = subscription;
            if (current != null) {
                current.cancel();
            }
            body.cancel(false);
        }
    }
}
