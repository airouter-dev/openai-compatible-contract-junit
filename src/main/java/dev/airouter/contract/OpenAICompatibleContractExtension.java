package dev.airouter.contract;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.util.Objects;

/**
 * JUnit 5 extension that runs a configured {@link ContractRunner} once before
 * the test class and exposes the sanitized configuration/report to parameters.
 */
public final class OpenAICompatibleContractExtension
        implements BeforeAllCallback, ParameterResolver {
    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(OpenAICompatibleContractExtension.class);
    private static final String CONFIG = "config";
    private static final String REPORT = "report";
    private static final String RUNNER = "runner";
    private final ContractConfig explicitConfig;

    /**
     * Creates an annotation-driven extension. The extension is normally added
     * automatically by {@link OpenAICompatibleContract}.
     */
    public OpenAICompatibleContractExtension() {
        this.explicitConfig = null;
    }

    /**
     * Creates a programmatic extension, useful when a local fixture chooses a
     * random port at test startup.
     *
     * @param config immutable configuration
     */
    public OpenAICompatibleContractExtension(ContractConfig config) {
        this.explicitConfig = Objects.requireNonNull(config, "config");
    }

    /**
     * Runs the configured contract before any test in the class.
     *
     * @param context JUnit context
     */
    @Override
    public void beforeAll(ExtensionContext context) {
        OpenAICompatibleContract annotation = context.getRequiredTestClass()
                .getAnnotation(OpenAICompatibleContract.class);
        if (explicitConfig == null && annotation == null) {
            throw new IllegalStateException(
                    "OpenAICompatibleContractExtension requires @OpenAICompatibleContract "
                            + "or a programmatic ContractConfig");
        }
        ContractConfig config = explicitConfig != null ? explicitConfig : toConfig(annotation);
        ContractRunner runner = new ContractRunner(config);
        ContractReport report = runner.run();
        ExtensionContext.Store store = context.getStore(NAMESPACE);
        store.put(CONFIG, config);
        store.put(RUNNER, runner);
        store.put(REPORT, report);
        if (!report.passed()) {
            throw new AssertionError("OpenAI-compatible contract failed: " + report.summary());
        }
    }

    /**
     * Supports injecting the immutable config, runner, or report.
     *
     * @param parameterContext parameter metadata
     * @param extensionContext JUnit context
     * @return whether the type is supported
     */
    @Override
    public boolean supportsParameter(
            ParameterContext parameterContext,
            ExtensionContext extensionContext) {
        Class<?> type = parameterContext.getParameter().getType();
        return type == ContractConfig.class
                || type == ContractRunner.class
                || type == ContractReport.class;
    }

    /**
     * Resolves an injected config, runner, or report.
     *
     * @param parameterContext parameter metadata
     * @param extensionContext JUnit context
     * @return resolved object
     */
    @Override
    public Object resolveParameter(
            ParameterContext parameterContext,
            ExtensionContext extensionContext) {
        Class<?> type = parameterContext.getParameter().getType();
        Object value = extensionContext.getStore(NAMESPACE).get(keyFor(type));
        if (value == null) {
            throw new ParameterResolutionException(
                    "Contract extension state is unavailable; check @OpenAICompatibleContract");
        }
        return value;
    }

    private static String keyFor(Class<?> type) {
        if (type == ContractConfig.class) {
            return CONFIG;
        }
        if (type == ContractRunner.class) {
            return RUNNER;
        }
        return REPORT;
    }

    private static ContractConfig toConfig(OpenAICompatibleContract annotation) {
        String baseUri = firstNonBlank(annotation.baseUri(), setting(annotation.baseUriProperty()));
        if (baseUri == null) {
            throw new IllegalStateException(
                    "No API root; set annotation baseUri or " + annotation.baseUriProperty());
        }
        String model = firstNonBlank(annotation.model(), setting(annotation.modelProperty()));
        if (model == null && (annotation.runChat() || annotation.runStreaming())) {
            throw new IllegalStateException(
                    "An explicit model is required when chat or streaming is enabled");
        }
        String resolvedModel = model == null ? "contract-test-model" : model;
        return ContractConfig.builder(baseUri)
                .model(resolvedModel)
                .bearerTokenSupplier(() -> setting(annotation.apiKeyProperty()))
                .runChat(annotation.runChat())
                .runStreaming(annotation.runStreaming())
                .timeout(java.time.Duration.ofMillis(annotation.timeoutMillis()))
                .maxResponseBytes(annotation.maxResponseBytes())
                .maxEventBytes(annotation.maxEventBytes())
                .build();
    }

    private static String setting(String name) {
        Objects.requireNonNull(name, "setting name");
        String property = System.getProperty(name);
        if (property != null && !property.isBlank()) {
            return property;
        }
        String environment = System.getenv(name);
        return environment == null || environment.isBlank() ? null : environment;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null || second.isBlank() ? null : second;
    }
}
