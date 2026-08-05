package dev.airouter.contract.fixture;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Loopback-only deterministic HTTP fixture for local contract tests.
 *
 * <p>This fixture is deliberately small and is not a production HTTP server.
 * It exposes only {@code /v1/models} and
 * {@code /v1/chat/completions}; all response bodies are configured in memory.</p>
 */
public final class LocalGatewayFixture implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor;
    private final Response models;
    private final Response chat;
    private final Response streaming;
    private final String expectedBearerToken;
    private final Map<String, Integer> requestCounts = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    private LocalGatewayFixture(Builder builder) throws IOException {
        this.models = builder.models;
        this.chat = builder.chat;
        this.streaming = builder.streaming;
        this.expectedBearerToken = builder.expectedBearerToken;
        this.server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "contract-fixture");
            thread.setDaemon(true);
            return thread;
        });
        server.createContext("/v1/models", exchange -> handle(exchange, models));
        server.createContext("/v1/chat/completions", exchange -> {
            Response response = "true".equals(requestBodyField(exchange, "stream"))
                    ? streaming
                    : chat;
            handle(exchange, response);
        });
        server.setExecutor(executor);
        server.start();
    }

    /**
     * Starts a fixture with healthy default responses.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the API root, including the trailing {@code /v1/}.
     *
     * @return loopback API root
     */
    public URI apiRoot() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/");
    }

    /**
     * Returns the number of requests observed for a path.
     *
     * @param path path such as {@code /v1/models}
     * @return count
     */
    public int requestCount(String path) {
        return requestCounts.getOrDefault(path, 0);
    }

    /**
     * Stops the fixture and its daemon executor.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private void handle(HttpExchange exchange, Response configured) throws IOException {
        String path = exchange.getRequestURI().getPath();
        requestCounts.merge(path, 1, Integer::sum);
        boolean modelsPath = "/v1/models".equals(path);
        boolean chatPath = "/v1/chat/completions".equals(path);
        if ((modelsPath && !"GET".equals(exchange.getRequestMethod()))
                || (chatPath && !"POST".equals(exchange.getRequestMethod()))) {
            respond(exchange, Response.text(405, "method not allowed"));
            return;
        }
        if (expectedBearerToken != null
                && !("Bearer " + expectedBearerToken).equals(
                exchange.getRequestHeaders().getFirst("Authorization"))) {
            respond(exchange, Response.json(401, "{\"error\":{\"type\":\"unauthorized\"}}"));
            return;
        }
        respond(exchange, configured);
    }

    private static String requestBodyField(HttpExchange exchange, String field) {
        if (!"POST".equals(exchange.getRequestMethod())) {
            return "";
        }
        try (var input = exchange.getRequestBody()) {
            byte[] body = input.readNBytes(65_537);
            if (body.length > 65_536) {
                return "";
            }
            String text = new String(body, StandardCharsets.UTF_8);
            String marker = "\"" + field + "\":";
            int start = text.indexOf(marker);
            if (start < 0) {
                return "";
            }
            int valueStart = start + marker.length();
            while (valueStart < text.length() && Character.isWhitespace(text.charAt(valueStart))) {
                valueStart++;
            }
            if (valueStart < text.length() && text.charAt(valueStart) == 't'
                    && text.startsWith("true", valueStart)) {
                return "true";
            }
            return "";
        } catch (IOException ignored) {
            return "";
        }
    }

    private static void respond(HttpExchange exchange, Response response) throws IOException {
        if (!response.delay().isZero()) {
            try {
                Thread.sleep(response.delay().toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                exchange.close();
                return;
            }
        }
        exchange.getResponseHeaders().set("Content-Type", response.contentType());
        byte[] body = response.body();
        exchange.sendResponseHeaders(response.status(), body.length);
        try (var output = exchange.getResponseBody()) {
            if (!response.bodyDelay().isZero()) {
                try {
                    Thread.sleep(response.bodyDelay().toMillis());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            try {
                output.write(body);
            } catch (IOException ignored) {
                // A timeout test may intentionally close the client connection.
            }
        }
    }

    /**
     * Builder for a fixture.
     */
    public static final class Builder {
        private Response models = Response.json(
                200, "{\"data\":[{\"id\":\"fixture-model\",\"object\":\"model\"}]}");
        private Response chat = Response.json(
                200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}");
        private Response streaming = Response.sse(
                200, "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}\n\n"
                        + "data: [DONE]\n\n");
        private String expectedBearerToken;

        private Builder() {
        }

        /**
         * Configures the models response.
         *
         * @param response response
         * @return this builder
         */
        public Builder models(Response response) {
            this.models = Objects.requireNonNull(response, "response");
            return this;
        }

        /**
         * Configures the non-streaming chat response.
         *
         * @param response response
         * @return this builder
         */
        public Builder chat(Response response) {
            this.chat = Objects.requireNonNull(response, "response");
            return this;
        }

        /**
         * Configures the streaming response.
         *
         * @param response response
         * @return this builder
         */
        public Builder streaming(Response response) {
            this.streaming = Objects.requireNonNull(response, "response");
            return this;
        }

        /**
         * Requires a bearer token without ever returning it in a response.
         *
         * @param token expected token
         * @return this builder
         */
        public Builder requireBearerToken(String token) {
            if (token == null || token.isBlank()) {
                throw new IllegalArgumentException("token must not be blank");
            }
            this.expectedBearerToken = token;
            return this;
        }

        /**
         * Starts the loopback server.
         *
         * @return fixture
         * @throws IOException if the loopback listener cannot start
         */
        public LocalGatewayFixture build() throws IOException {
            return new LocalGatewayFixture(this);
        }
    }

    /**
     * Immutable response definition.
     */
    public static final class Response {
        private final int status;
        private final String contentType;
        private final byte[] body;
        private final Duration delay;
        private final Duration bodyDelay;

        private Response(
                int status,
                String contentType,
                byte[] body,
                Duration delay,
                Duration bodyDelay) {
            if (status < 100 || status > 599) {
                throw new IllegalArgumentException("status must be a valid HTTP status");
            }
            this.status = status;
            this.contentType = Objects.requireNonNull(contentType, "contentType");
            this.body = Objects.requireNonNull(body, "body").clone();
            this.delay = Objects.requireNonNull(delay, "delay");
            this.bodyDelay = Objects.requireNonNull(bodyDelay, "bodyDelay");
            if (delay.isNegative() || bodyDelay.isNegative()) {
                throw new IllegalArgumentException("delays must not be negative");
            }
        }

        /**
         * Creates a JSON response.
         *
         * @param status HTTP status
         * @param body UTF-8 JSON
         * @return response
         */
        public static Response json(int status, String body) {
            return text(status, "application/json", body);
        }

        /**
         * Creates an SSE response.
         *
         * @param status HTTP status
         * @param body UTF-8 SSE
         * @return response
         */
        public static Response sse(int status, String body) {
            return text(status, "text/event-stream", body);
        }

        /**
         * Creates a text response.
         *
         * @param status HTTP status
         * @param body UTF-8 text
         * @return response
         */
        public static Response text(int status, String body) {
            return text(status, "text/plain; charset=utf-8", body);
        }

        /**
         * Creates a response with an explicit content type.
         *
         * @param status HTTP status
         * @param contentType content type
         * @param body UTF-8 body
         * @return response
         */
        public static Response text(int status, String contentType, String body) {
            return new Response(status, contentType,
                    Objects.requireNonNull(body, "body").getBytes(StandardCharsets.UTF_8),
                    Duration.ZERO,
                    Duration.ZERO);
        }

        /**
         * Adds a fixed response delay.
         *
         * @param delay delay
         * @return a new response
         */
        public Response withDelay(Duration delay) {
            return new Response(status, contentType, body, delay, bodyDelay);
        }

        /**
         * Adds a fixed delay after response headers and before the body.
         *
         * @param delay body delay
         * @return a new response
         */
        public Response withBodyDelay(Duration delay) {
            return new Response(status, contentType, body, this.delay, delay);
        }

        private int status() {
            return status;
        }

        private String contentType() {
            return contentType;
        }

        private byte[] body() {
            return body.clone();
        }

        private Duration delay() {
            return delay;
        }

        private Duration bodyDelay() {
            return bodyDelay;
        }
    }
}
