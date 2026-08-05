package dev.airouter.contract;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SseContractTest {

    @Test
    void acceptsJsonDataAndDone() {
        assertDoesNotThrow(() -> SseContract.requireComplete(
                utf8("event: message\ndata: {\"choices\":[]}\n\n"
                        + "data: [DONE]\n\n"),
                1_024));
    }

    @Test
    void rejectsMissingTerminalEvent() {
        assertThrows(ContractViolationException.class, () -> SseContract.requireComplete(
                utf8("data: {\"choices\":[]}\n\n"), 1_024));
    }

    @Test
    void rejectsDataAfterDone() {
        assertThrows(ContractViolationException.class, () -> SseContract.requireComplete(
                utf8("data: {\"choices\":[]}\n\n"
                        + "data: [DONE]\n\n"
                        + "data: {\"choices\":[]}\n\n"), 1_024));
    }

    @Test
    void rejectsProviderErrorAndMalformedJson() {
        assertThrows(ContractViolationException.class, () -> SseContract.requireComplete(
                utf8("data: {\"error\":{}}\n\ndata: [DONE]\n\n"), 1_024));
        assertThrows(ContractViolationException.class, () -> SseContract.requireComplete(
                utf8("data: not-json\n\ndata: [DONE]\n\n"), 1_024));
    }

    @Test
    void rejectsOversizedEvent() {
        assertThrows(ContractViolationException.class, () -> SseContract.requireComplete(
                utf8("data: {\"value\":\"" + "x".repeat(100) + "\"}\n\n"
                        + "data: [DONE]\n\n"), 32));
    }

    private static byte[] utf8(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
