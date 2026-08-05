package dev.airouter.contract;

import dev.airouter.contract.fixture.LocalGatewayFixture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAICompatibleContractExtensionTest {
    private static final LocalGatewayFixture FIXTURE = startFixture();
    @RegisterExtension
    static final OpenAICompatibleContractExtension EXTENSION =
            new OpenAICompatibleContractExtension(
                    ContractConfig.builder(FIXTURE.apiRoot())
                            .model("fixture-model")
                            .build());

    private static LocalGatewayFixture startFixture() {
        try {
            LocalGatewayFixture fixture = LocalGatewayFixture.builder().build();
            return fixture;
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @AfterAll
    static void closeFixture() {
        FIXTURE.close();
    }

    @Test
    void injectsSanitizedReportAndConfig(
            ContractReport report,
            ContractConfig config,
            ContractRunner runner) {
        assertTrue(report.passed(), report.summary());
        assertEquals(FIXTURE.apiRoot(), config.apiRoot());
        assertTrue(runner.run().passed());
    }
}
