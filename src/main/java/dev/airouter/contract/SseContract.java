package dev.airouter.contract;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Bounded SSE contract parser for OpenAI-compatible streaming responses.
 */
final class SseContract {
    private SseContract() {
    }

    static void requireComplete(byte[] body, int maxEventBytes)
            throws ContractViolationException {
        Objects.requireNonNull(body, "body");
        if (maxEventBytes <= 0) {
            throw new IllegalArgumentException("maxEventBytes must be positive");
        }

        String text = new String(body, StandardCharsets.UTF_8);
        StringBuilder data = new StringBuilder();
        int eventBytes = 0;
        boolean sawData = false;
        boolean sawDone = false;
        boolean sawEventBoundary = false;

        String[] lines = text.split("\\n", -1);
        for (String rawLine : lines) {
            String line = rawLine.endsWith("\r")
                    ? rawLine.substring(0, rawLine.length() - 1)
                    : rawLine;
            if (line.isEmpty()) {
                sawEventBoundary = true;
                EventState state = dispatch(data, sawData, sawDone, maxEventBytes);
                sawData = state.sawData();
                sawDone = state.sawDone();
                data.setLength(0);
                eventBytes = 0;
                continue;
            }

            if (line.startsWith(":")) {
                continue;
            }
            if (line.startsWith("data:")) {
                String value = line.substring(5);
                if (value.startsWith(" ")) {
                    value = value.substring(1);
                }
                int added = value.getBytes(StandardCharsets.UTF_8).length
                        + (data.isEmpty() ? 0 : 1);
                if (added > maxEventBytes - eventBytes) {
                    throw new ContractViolationException("SSE event exceeds configured byte bound");
                }
                if (!data.isEmpty()) {
                    data.append('\n');
                }
                data.append(value);
                eventBytes += added;
            }
            // event/id/retry and unknown fields are allowed by SSE framing.
        }

        if (!data.isEmpty()) {
            throw new ContractViolationException("SSE ended with an unterminated event");
        }
        if (!sawEventBoundary || !sawData || !sawDone) {
            throw new ContractViolationException("SSE must contain data and a terminal [DONE] event");
        }
    }

    private static EventState dispatch(
            StringBuilder data,
            boolean sawData,
            boolean sawDone,
            int maxEventBytes) throws ContractViolationException {
        if (data.isEmpty()) {
            return new EventState(sawData, sawDone);
        }
        String value = data.toString();
        if ("[DONE]".equals(value)) {
            if (sawDone) {
                throw new ContractViolationException("SSE contains duplicate [DONE]");
            }
            return new EventState(sawData, true);
        }
        if (sawDone) {
            throw new ContractViolationException("SSE contains data after [DONE]");
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > maxEventBytes) {
            throw new ContractViolationException("SSE data exceeds configured byte bound");
        }
        byte[] payload = value.getBytes(StandardCharsets.UTF_8);
        if (JsonContract.hasTopLevelField(payload, "error")) {
            throw new ContractViolationException("SSE contains a provider error event");
        }
        if (!JsonContract.isValidJson(payload)) {
            throw new ContractViolationException("SSE data event is not valid JSON");
        }
        return new EventState(true, false);
    }

    private record EventState(boolean sawData, boolean sawDone) {
    }
}
