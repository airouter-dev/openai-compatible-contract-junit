package dev.airouter.contract;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Immutable, sanitized report for a contract run.
 */
public final class ContractReport {
    private final List<ContractCheckResult> checks;

    /**
     * Creates a report from check results.
     *
     * @param checks results in execution order
     */
    public ContractReport(List<ContractCheckResult> checks) {
        Objects.requireNonNull(checks, "checks");
        this.checks = List.copyOf(checks);
    }

    /**
     * Returns an immutable view of the check results.
     *
     * @return checks
     */
    public List<ContractCheckResult> checks() {
        return checks;
    }

    /**
     * Returns whether no check failed.
     *
     * @return true when passed or skipped
     */
    public boolean passed() {
        return checks.stream()
                .noneMatch(check -> check.status() == ContractCheckResult.Status.FAILED);
    }

    /**
     * Returns a one-line-safe summary intended for test output.
     *
     * @return summary without raw response data
     */
    public String summary() {
        return checks.stream()
                .map(check -> check.name() + "=" + check.status() + " (" + check.detail() + ")")
                .collect(Collectors.joining("; "));
    }

    @Override
    public String toString() {
        return "ContractReport{passed=" + passed() + ", checks=" + checks + "}";
    }
}
