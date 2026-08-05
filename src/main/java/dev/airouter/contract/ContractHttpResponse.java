package dev.airouter.contract;

import java.net.http.HttpHeaders;
import java.util.Objects;

/**
 * Bounded HTTP response used by contract checks.
 *
 * @param statusCode status code
 * @param headers response headers
 * @param body retained body bytes, bounded by configuration
 */
public record ContractHttpResponse(int statusCode, HttpHeaders headers, byte[] body) {
    /**
     * Creates a response and defensively copies body bytes.
     */
    public ContractHttpResponse {
        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException("statusCode must be a valid HTTP status");
        }
        headers = Objects.requireNonNull(headers, "headers");
        body = Objects.requireNonNull(body, "body").clone();
    }

    /**
     * Returns a defensive copy of the body.
     *
     * @return body
     */
    @Override
    public byte[] body() {
        return body.clone();
    }

    /**
     * Returns the first content type header, or an empty string.
     *
     * @return content type
     */
    public String contentType() {
        return headers.firstValue("content-type").orElse("");
    }
}
