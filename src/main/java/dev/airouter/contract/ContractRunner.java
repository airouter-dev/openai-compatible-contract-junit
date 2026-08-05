package dev.airouter.contract;

import com.fasterxml.jackson.core.io.JsonStringEncoder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Executes the bounded read-only and opt-in gateway checks.
 */
public final class ContractRunner {
    private final ContractConfig config;

    /**
     * Creates a runner.
     *
     * @param config immutable contract configuration
     */
    public ContractRunner(ContractConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Runs models and the explicitly enabled optional checks.
     *
     * @return sanitized report
     */
    public ContractReport run() {
        BoundedHttpClient http = new BoundedHttpClient(config);
        List<ContractCheckResult> checks = new ArrayList<>();
        checks.add(runCheck("models", () -> {
            ContractHttpResponse response = http.get("models");
            requireStatus(response, 200, "models");
            requireJson(response, "models");
            JsonContract.requireModelsShape(response.body());
        }));

        if (config.runChat()) {
            checks.add(runCheck("chat", () -> {
                ContractHttpResponse response = http.postJson(
                        "chat/completions",
                        chatBody(false),
                        "application/json");
                requireStatus(response, 200, "chat");
                requireJson(response, "chat");
                JsonContract.requireTopLevelArray(response.body(), "choices", "chat");
            }));
        } else {
            checks.add(skipped("chat", "disabled by configuration"));
        }

        if (config.runStreaming()) {
            checks.add(runCheck("streaming", () -> {
                ContractHttpResponse response = http.postJson(
                        "chat/completions",
                        chatBody(true),
                        "text/event-stream");
                requireStatus(response, 200, "streaming");
                requireContentType(response, "text/event-stream", "streaming");
                SseContract.requireComplete(response.body(), config.maxEventBytes());
            }));
        } else {
            checks.add(skipped("streaming", "disabled by configuration"));
        }
        return new ContractReport(checks);
    }

    private ContractCheckResult runCheck(String name, CheckedCheck check) {
        Instant started = Instant.now();
        try {
            check.run();
            return new ContractCheckResult(name, ContractCheckResult.Status.PASSED,
                    "status and shape accepted", elapsed(started));
        } catch (ContractViolationException exception) {
            return new ContractCheckResult(name, ContractCheckResult.Status.FAILED,
                    safeDetail(exception), elapsed(started));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new ContractCheckResult(name, ContractCheckResult.Status.FAILED,
                    "interrupted", elapsed(started));
        } catch (java.io.IOException exception) {
            return new ContractCheckResult(name, ContractCheckResult.Status.FAILED,
                    "I/O failure (" + exception.getClass().getSimpleName() + ")", elapsed(started));
        } catch (RuntimeException exception) {
            return new ContractCheckResult(name, ContractCheckResult.Status.FAILED,
                    "configuration/runtime failure (" + exception.getClass().getSimpleName() + ")",
                    elapsed(started));
        }
    }

    private static ContractCheckResult skipped(String name, String detail) {
        return new ContractCheckResult(name, ContractCheckResult.Status.SKIPPED, detail, Duration.ZERO);
    }

    private static String safeDetail(ContractViolationException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "contract violation" : message;
    }

    private static Duration elapsed(Instant started) {
        return Duration.between(started, Instant.now());
    }

    private static void requireStatus(ContractHttpResponse response, int expected, String name)
            throws ContractViolationException {
        if (response.statusCode() != expected) {
            throw new ContractViolationException(
                    name + " returned HTTP " + response.statusCode() + "; expected " + expected);
        }
    }

    private static void requireJson(ContractHttpResponse response, String name)
            throws ContractViolationException {
        requireContentType(response, "application/json", name);
    }

    private static void requireContentType(
            ContractHttpResponse response,
            String expected,
            String name) throws ContractViolationException {
        String contentType = response.contentType();
        int parameters = contentType.indexOf(';');
        String mediaType = (parameters < 0 ? contentType : contentType.substring(0, parameters))
                .trim();
        if (!mediaType.equalsIgnoreCase(expected)) {
            throw new ContractViolationException(
                    name + " returned an unexpected content type");
        }
    }

    String chatBody(boolean stream) {
        return "{\"model\":\"" + jsonEscape(config.model())
                + "\",\"messages\":[{\"role\":\"user\",\"content\":\"contract probe\"}]"
                + ",\"max_tokens\":1,\"stream\":" + stream + "}";
    }

    private static String jsonEscape(String value) {
        return new String(JsonStringEncoder.getInstance().quoteAsString(value));
    }

    @FunctionalInterface
    private interface CheckedCheck {
        void run() throws ContractViolationException, java.io.IOException, InterruptedException;
    }
}
