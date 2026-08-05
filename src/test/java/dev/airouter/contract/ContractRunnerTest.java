package dev.airouter.contract;

import dev.airouter.contract.fixture.LocalGatewayFixture;
import org.junit.jupiter.api.Test;

import java.time.Duration;

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
}
