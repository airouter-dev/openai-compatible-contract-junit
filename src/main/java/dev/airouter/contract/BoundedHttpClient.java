package dev.airouter.contract;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

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
        HttpResponse<InputStream> response = client.send(
                request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            byte[] bytes = readBounded(body, config.maxResponseBytes());
            return new ContractHttpResponse(response.statusCode(), response.headers(), bytes);
        }
    }

    private static byte[] readBounded(InputStream input, int maximum)
            throws IOException, ContractViolationException {
        int readLimit = maximum == Integer.MAX_VALUE ? maximum : maximum + 1;
        byte[] bytes = input.readNBytes(readLimit);
        if (bytes.length > maximum) {
            throw new ContractViolationException("response exceeds configured byte bound");
        }
        return bytes;
    }
}
