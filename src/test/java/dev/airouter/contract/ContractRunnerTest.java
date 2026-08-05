package dev.airouter.contract;

import com.sun.net.httpserver.HttpServer;
import dev.airouter.contract.fixture.LocalGatewayFixture;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractRunnerTest {

    @Test
    void healthyFixturePassesAllExplicitChecksWithoutLeakingToken() throws Exception {
        String token = "fixture-token-that-must-not-appear";
        try (LocalGatewayFixture fixture = LocalGatewayFixture.builder()
                .requireBearerToken(token)
                .build()) {
            ContractConfig config = ContractConfig.builder(fixture.apiRoot())
                    .model("fixture-model")
                    .bearerTokenSupplier(() -> token)
                    .runChat(true)
                    .runStreaming(true)
                    .timeout(Duration.ofSeconds(2))
                    .build();

            ContractReport report = new ContractRunner(config).run();

            assertTrue(report.passed(), report.summary());
            assertEquals(ContractCheckResult.Status.PASSED, report.checks().get(0).status());
            assertEquals(ContractCheckResult.Status.PASSED, report.checks().get(1).status());
            assertEquals(ContractCheckResult.Status.PASSED, report.checks().get(2).status());
            assertEquals(1, fixture.requestCount("/v1/models"));
            assertEquals(2, fixture.requestCount("/v1/chat/completions"));
            assertFalse(report.summary().contains(token));
            assertFalse(config.toString().contains(token));
        }
    }

    @Test
    void optionalChecksAreSkippedByDefault() throws Exception {
        try (LocalGatewayFixture fixture = LocalGatewayFixture.builder().build()) {
            ContractReport report = new ContractRunner(
                    ContractConfig.builder(fixture.apiRoot()).build()).run();

            assertTrue(report.passed(), report.summary());
            assertEquals(ContractCheckResult.Status.PASSED, report.checks().get(0).status());
            assertEquals(ContractCheckResult.Status.SKIPPED, report.checks().get(1).status());
            assertEquals(ContractCheckResult.Status.SKIPPED, report.checks().get(2).status());
            assertEquals(0, fixture.requestCount("/v1/chat/completions"));
        }
    }

    @Test
    void malformedModelsShapeFailsClosed() throws Exception {
        try (LocalGatewayFixture fixture = LocalGatewayFixture.builder()
                .models(LocalGatewayFixture.Response.json(200, "{\"data\":[{\"name\":\"no-id\"}]}"))
                .build()) {
            ContractReport report = new ContractRunner(
                    ContractConfig.builder(fixture.apiRoot()).build()).run();

            assertFalse(report.passed());
            assertEquals(ContractCheckResult.Status.FAILED, report.checks().get(0).status());
            assertFalse(report.summary().contains("no-id"));
        }
    }

    @Test
    void truncatedStreamFailsInsteadOfTreatingPartialOutputAsSuccess() throws Exception {
        try (LocalGatewayFixture fixture = LocalGatewayFixture.builder()
                .streaming(LocalGatewayFixture.Response.sse(
                        200, "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n"))
                .build()) {
            ContractReport report = new ContractRunner(
                    ContractConfig.builder(fixture.apiRoot())
                            .runStreaming(true)
                            .build()).run();

            assertFalse(report.passed());
            assertEquals(ContractCheckResult.Status.FAILED, report.checks().get(2).status());
        }
    }

    @Test
    void responseBoundProducesSanitizedFailure() throws Exception {
        String oversized = "{\"data\":[{\"id\":\"" + "x".repeat(20_000) + "\"}]}";
        try (LocalGatewayFixture fixture = LocalGatewayFixture.builder()
                .models(LocalGatewayFixture.Response.json(200, oversized))
                .build()) {
            ContractReport report = new ContractRunner(
                    ContractConfig.builder(fixture.apiRoot())
                            .maxResponseBytes(1_024)
                            .maxEventBytes(512)
                            .build()).run();

            assertFalse(report.passed());
            assertTrue(report.summary().contains("byte bound"));
            assertFalse(report.summary().contains("xxxx"));
        }
    }

    @Test
    void invalidSseJsonAndProviderErrorFailClosed() throws Exception {
        try (LocalGatewayFixture fixture = LocalGatewayFixture.builder()
                .streaming(LocalGatewayFixture.Response.sse(
                        200, "data: {\"error\":{\"message\":\"private detail\"}}\n\n"
                                + "data: [DONE]\n\n"))
                .build()) {
            ContractReport report = new ContractRunner(
                    ContractConfig.builder(fixture.apiRoot())
                            .runStreaming(true)
                            .build()).run();

            assertFalse(report.passed());
            assertFalse(report.summary().contains("private detail"));
        }
    }

    @Test
    void contentTypeRequiresAnExactMediaTypeButAllowsParameters() throws Exception {
        String models = "{\"data\":[{\"id\":\"fixture-model\"}]}";
        try (LocalGatewayFixture fixture = LocalGatewayFixture.builder()
                .models(LocalGatewayFixture.Response.text(
                        200, "application/json; charset=utf-8", models))
                .build()) {
            ContractReport report = new ContractRunner(
                    ContractConfig.builder(fixture.apiRoot()).build()).run();
            assertTrue(report.passed(), report.summary());
        }

        try (LocalGatewayFixture fixture = LocalGatewayFixture.builder()
                .models(LocalGatewayFixture.Response.text(
                        200, "application/jsonevil", models))
                .build()) {
            ContractReport report = new ContractRunner(
                    ContractConfig.builder(fixture.apiRoot()).build()).run();
            assertFalse(report.passed());
            assertEquals(ContractCheckResult.Status.FAILED, report.checks().get(0).status());
        }

        try (LocalGatewayFixture fixture = LocalGatewayFixture.builder()
                .streaming(LocalGatewayFixture.Response.text(
                        200, "text/event-streaming", "data: [DONE]\n\n"))
                .build()) {
            ContractReport report = new ContractRunner(
                    ContractConfig.builder(fixture.apiRoot())
                            .runStreaming(true)
                            .build()).run();
            assertFalse(report.passed());
            assertEquals(ContractCheckResult.Status.FAILED, report.checks().get(2).status());
        }
    }

    @Test
    void chatBodyEscapesEveryJsonControlCharacter() {
        StringBuilder controls = new StringBuilder("model-");
        for (char value = 0; value < 0x20; value++) {
            controls.append(value);
        }
        controls.append("-end");

        ContractConfig config = ContractConfig.builder("https://example.invalid/v1/")
                .model(controls.toString())
                .build();
        String body = new ContractRunner(config).chatBody(false);

        assertTrue(JsonContract.isValidJson(body.getBytes(StandardCharsets.UTF_8)));
        assertFalse(body.contains("\u0000"));
    }

    @Test
    void timeoutAndHttpErrorDetailsRemainSanitized() throws Exception {
        String secret = "provider-body-secret";
        try (LocalGatewayFixture fixture = LocalGatewayFixture.builder()
                .models(LocalGatewayFixture.Response.text(500, secret))
                .build()) {
            ContractReport report = new ContractRunner(
                    ContractConfig.builder(fixture.apiRoot()).build()).run();
            assertFalse(report.passed());
            assertTrue(report.summary().contains("HTTP 500"));
            assertFalse(report.summary().contains(secret));
        }

        try (LocalGatewayFixture fixture = LocalGatewayFixture.builder()
                .models(LocalGatewayFixture.Response.json(
                        200, "{\"data\":[{\"id\":\"slow\"}]}")
                        .withBodyDelay(Duration.ofSeconds(5)))
                .build()) {
            long started = System.nanoTime();
            ContractReport report = new ContractRunner(
                    ContractConfig.builder(fixture.apiRoot())
                            .timeout(Duration.ofMillis(200))
                            .build()).run();
            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
            assertFalse(report.passed());
            assertTrue(report.summary().contains("request timed out"));
            assertTrue(elapsed.compareTo(Duration.ofSeconds(2)) < 0, elapsed.toString());
        }
    }

    @Test
    void clientRefusesRedirects() throws Exception {
        AtomicInteger redirectedRequests = new AtomicInteger();
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/models", exchange -> {
            exchange.getResponseHeaders().set("Location", "/v1/redirected");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/v1/redirected", exchange -> {
            redirectedRequests.incrementAndGet();
            byte[] body = "{\"data\":[{\"id\":\"redirected\"}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String root = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/";
            ContractReport report = new ContractRunner(
                    ContractConfig.builder(root).build()).run();
            assertFalse(report.passed());
            assertTrue(report.summary().contains("HTTP 302"));
            assertEquals(0, redirectedRequests.get());
        } finally {
            server.stop(0);
        }
    }
}
