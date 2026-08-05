package dev.airouter.contract;

import java.time.Duration;
import java.util.Objects;

/**
 * Sanitized result of one contract check.
 *
 * @param name check name
 * @param status result status
 * @param detail bounded diagnostic without a response body or credential
 * @param duration elapsed time
 */
public record ContractCheckResult(
        String name,
        Status status,
        String detail,
        Duration duration) {

    /**
     * Result status.
     */
    public enum Status {
        /** Check completed successfully. */
        PASSED,
        /** Check was explicitly disabled. */
        SKIPPED,
        /** Check failed. */
        FAILED
    }

    /**
     * Creates a normalized result.
     */
    public ContractCheckResult {
        name = requireText(name, "name");
        status = Objects.requireNonNull(status, "status");
        detail = requireText(detail, "detail");
        duration = Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }
}
