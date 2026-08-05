package dev.airouter.contract;

/**
 * Internal exception carrying a sanitized check failure.
 */
final class ContractViolationException extends Exception {
    ContractViolationException(String message) {
        super(message);
    }

    ContractViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
