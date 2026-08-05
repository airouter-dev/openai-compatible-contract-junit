package dev.airouter.contract;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables bounded OpenAI-compatible contract checks for a JUnit 5 test class.
 *
 * <p>The annotation stores names of properties/environment variables for
 * secrets instead of accepting secret values. The extension always runs the
 * read-only models check; chat and streaming checks require explicit opt-in.</p>
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ExtendWith(OpenAICompatibleContractExtension.class)
public @interface OpenAICompatibleContract {

    /**
     * Direct API root, normally ending in {@code /v1}. Empty means resolve
     * {@link #baseUriProperty()}.
     *
     * @return API root
     */
    String baseUri() default "";

    /**
     * System-property/environment-variable name used when {@link #baseUri()}
     * is empty.
     *
     * @return setting name
     */
    String baseUriProperty() default "OPENAI_COMPATIBLE_BASE_URL";

    /**
     * Direct model identifier used by opt-in chat checks. Empty means resolve
     * {@link #modelProperty()}.
     *
     * @return model name
     */
    String model() default "";

    /**
     * System-property/environment-variable name for the opt-in model.
     *
     * @return setting name
     */
    String modelProperty() default "OPENAI_COMPATIBLE_TEST_MODEL";

    /**
     * System-property/environment-variable name for a bearer token. The
     * annotation deliberately has no direct token-value attribute.
     *
     * @return setting name
     */
    String apiKeyProperty() default "OPENAI_COMPATIBLE_API_KEY";

    /**
     * Enables the non-streaming chat check.
     *
     * @return true to run chat
     */
    boolean runChat() default false;

    /**
     * Enables the SSE streaming check.
     *
     * @return true to run streaming
     */
    boolean runStreaming() default false;

    /**
     * Per-request timeout in milliseconds.
     *
     * @return timeout
     */
    long timeoutMillis() default 3_000L;

    /**
     * Maximum retained response size in bytes.
     *
     * @return bound
     */
    int maxResponseBytes() default 65_536;

    /**
     * Maximum retained SSE event size in bytes.
     *
     * @return bound
     */
    int maxEventBytes() default 16_384;
}
