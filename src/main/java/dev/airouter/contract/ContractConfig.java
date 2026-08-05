package dev.airouter.contract;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Immutable configuration for a contract run.
 *
 * <p>The bearer token is supplied lazily so callers can keep it in their own
 * secret store. It is never included in {@link #toString()} or reports.</p>
 */
public final class ContractConfig {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);
    private static final int DEFAULT_MAX_RESPONSE_BYTES = 65_536;
    private static final int DEFAULT_MAX_EVENT_BYTES = 16_384;

    private final URI apiRoot;
    private final String model;
    private final Supplier<String> bearerTokenSupplier;
    private final boolean runChat;
    private final boolean runStreaming;
    private final Duration timeout;
    private final int maxResponseBytes;
    private final int maxEventBytes;

    private ContractConfig(Builder builder) {
        this.apiRoot = normalizeApiRoot(builder.apiRoot);
        this.model = requireText(builder.model, "model");
        this.bearerTokenSupplier = builder.bearerTokenSupplier;
        this.runChat = builder.runChat;
        this.runStreaming = builder.runStreaming;
        this.timeout = requirePositiveDuration(builder.timeout, "timeout");
        this.maxResponseBytes = requirePositiveInt(builder.maxResponseBytes, "maxResponseBytes");
        this.maxEventBytes = requirePositiveInt(builder.maxEventBytes, "maxEventBytes");
        if (maxEventBytes > maxResponseBytes) {
            throw new IllegalArgumentException("maxEventBytes must not exceed maxResponseBytes");
        }
    }

    /**
     * Starts a builder with the endpoint API root, normally ending in /v1.
     *
     * @param apiRoot API root
     * @return builder
     */
    public static Builder builder(URI apiRoot) {
        return new Builder(apiRoot);
    }

    /**
     * Starts a builder from a URI string.
     *
     * @param apiRoot API root
     * @return builder
     */
    public static Builder builder(String apiRoot) {
        return builder(URI.create(Objects.requireNonNull(apiRoot, "apiRoot")));
    }

    /**
     * Returns the API root with exactly one trailing slash.
     *
     * @return API root
     */
    public URI apiRoot() {
        return apiRoot;
    }

    /**
     * Returns the model used by opt-in chat checks.
     *
     * @return model
     */
    public String model() {
        return model;
    }

    /**
     * Returns whether non-streaming chat is enabled.
     *
     * @return true when enabled
     */
    public boolean runChat() {
        return runChat;
    }

    /**
     * Returns whether SSE streaming is enabled.
     *
     * @return true when enabled
     */
    public boolean runStreaming() {
        return runStreaming;
    }

    /**
     * Returns the per-request timeout.
     *
     * @return timeout
     */
    public Duration timeout() {
        return timeout;
    }

    /**
     * Returns the response bound.
     *
     * @return maximum response bytes
     */
    public int maxResponseBytes() {
        return maxResponseBytes;
    }

    /**
     * Returns the SSE event bound.
     *
     * @return maximum event bytes
     */
    public int maxEventBytes() {
        return maxEventBytes;
    }

    /**
     * Resolves the current bearer token. A null or blank value means no
     * Authorization header.
     *
     * @return token or null
     */
    String bearerToken() {
        String value = bearerTokenSupplier == null ? null : bearerTokenSupplier.get();
        return value == null || value.isBlank() ? null : value;
    }

    URI endpoint(String path) {
        return apiRoot.resolve(path);
    }

    @Override
    public String toString() {
        return "ContractConfig{apiRoot=" + apiRoot
                + ", model='" + model + '\''
                + ", runChat=" + runChat
                + ", runStreaming=" + runStreaming
                + ", timeout=" + timeout
                + ", maxResponseBytes=" + maxResponseBytes
                + ", maxEventBytes=" + maxEventBytes
                + ", bearerToken=<redacted>}";
    }

    /**
     * Builder for {@link ContractConfig}.
     */
    public static final class Builder {
        private final URI apiRoot;
        private String model = "contract-test-model";
        private Supplier<String> bearerTokenSupplier;
        private boolean runChat;
        private boolean runStreaming;
        private Duration timeout = DEFAULT_TIMEOUT;
        private int maxResponseBytes = DEFAULT_MAX_RESPONSE_BYTES;
        private int maxEventBytes = DEFAULT_MAX_EVENT_BYTES;

        private Builder(URI apiRoot) {
            this.apiRoot = Objects.requireNonNull(apiRoot, "apiRoot");
        }

        /**
         * Sets the model.
         *
         * @param model model identifier
         * @return this builder
         */
        public Builder model(String model) {
            this.model = model;
            return this;
        }

        /**
         * Sets a lazy bearer token supplier.
         *
         * @param supplier supplier; null disables authentication
         * @return this builder
         */
        public Builder bearerTokenSupplier(Supplier<String> supplier) {
            this.bearerTokenSupplier = supplier;
            return this;
        }

        /**
         * Enables the chat check.
         *
         * @param enabled whether to enable
         * @return this builder
         */
        public Builder runChat(boolean enabled) {
            this.runChat = enabled;
            return this;
        }

        /**
         * Enables the streaming check.
         *
         * @param enabled whether to enable
         * @return this builder
         */
        public Builder runStreaming(boolean enabled) {
            this.runStreaming = enabled;
            return this;
        }

        /**
         * Sets the request timeout.
         *
         * @param timeout timeout
         * @return this builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the maximum response size.
         *
         * @param bytes maximum bytes
         * @return this builder
         */
        public Builder maxResponseBytes(int bytes) {
            this.maxResponseBytes = bytes;
            return this;
        }

        /**
         * Sets the maximum SSE event size.
         *
         * @param bytes maximum bytes
         * @return this builder
         */
        public Builder maxEventBytes(int bytes) {
            this.maxEventBytes = bytes;
            return this;
        }

        /**
         * Builds the immutable configuration.
         *
         * @return configuration
         */
        public ContractConfig build() {
            return new ContractConfig(this);
        }
    }

    private static URI normalizeApiRoot(URI value) {
        Objects.requireNonNull(value, "apiRoot");
        if (value.getScheme() == null || value.getHost() == null) {
            throw new IllegalArgumentException("apiRoot must be an absolute HTTP(S) URI");
        }
        if (!value.getScheme().equalsIgnoreCase("http")
                && !value.getScheme().equalsIgnoreCase("https")) {
            throw new IllegalArgumentException("apiRoot must use http or https");
        }
        if (value.getUserInfo() != null) {
            throw new IllegalArgumentException("apiRoot must not contain user info");
        }
        if (value.getQuery() != null || value.getFragment() != null) {
            throw new IllegalArgumentException("apiRoot must not contain query or fragment");
        }
        String text = value.toString();
        return URI.create(text.endsWith("/") ? text : text + "/");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }

    private static Duration requirePositiveDuration(Duration value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static int requirePositiveInt(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }
}
