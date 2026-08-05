# OpenAI-Compatible Contract JUnit

OpenAI-Compatible Contract JUnit is a small JUnit 5 extension for checking the
observable contract of an OpenAI-compatible HTTP gateway from a JVM build. It
checks /v1/models by default and can explicitly opt in to non-streaming chat
and SSE streaming checks.

This is a test harness, not an SDK, proxy, retryer, billing component, or
provider-specific integration. It does not require an AI ROUTER account and
does not bypass provider, geographic, or contractual restrictions.

## Status

Version 0.1.0 is a reviewed release candidate. The `dev.ai-router` namespace is
verified in Maven Central, and clean Maven builds pass on Java 17 and Java 21.
Publication remains pending until its signed Central deployment is validated
and manually approved. No Portal token or private signing key is stored here.

## Why this is JVM-native

- A JUnit 5 extension runs the checks inside Maven Surefire or a Gradle test
  task; no external CLI is needed.
- java.net.http.HttpClient supplies the HTTP transport and refuses redirects by
  default.
- JUnit reports contain one result per contract check and never include raw
  response bodies or credentials.
- A local LocalGatewayFixture provides deterministic models, chat, error, and
  SSE responses for offline tests.

## Quick start

After 0.1.0 is published to Maven Central, add the dependency to a test source
set:

~~~
<dependency>
  <groupId>dev.ai-router</groupId>
  <artifactId>openai-compatible-contract-junit</artifactId>
  <version>0.1.0</version>
  <scope>test</scope>
</dependency>
~~~

Annotate a JUnit 5 test class. The annotation reads the endpoint and model
from the named system properties first, then from environment variables. The
API key is never an annotation value.

~~~
import dev.airouter.contract.OpenAICompatibleContract;

@OpenAICompatibleContract(
    baseUriProperty = "contract.base-uri",
    modelProperty = "contract.model",
    apiKeyProperty = "contract.api-key",
    runChat = false,
    runStreaming = false
)
class GatewayContractTest {
  // The extension runs before the class and fails with sanitized diagnostics.
}
~~~

For a local run:

~~~
./mvnw -Dcontract.base-uri=http://127.0.0.1:8080/v1 \
    -Dcontract.model=test-model \
    test
~~~

/v1/models is the only check enabled by default. Chat and streaming are
explicit opt-ins because they can consume provider quota and require a caller
to decide whether a request is safe.

## Configuration and safety boundaries

ContractConfig has bounded response and event sizes, a total per-request
deadline that includes response-body delivery, and a redirect policy of NEVER.
The extension resolves a bearer token only at request time from the configured
property/environment name. It does not print the token, prompt, or response
body.

The check intentionally does not retry. A retry policy must be owned by the
application because a contract probe cannot prove that a provider operation is
idempotent or free of billing side effects.

The built-in JSON checks validate the minimum observable shape:

- models responses are JSON objects containing a non-empty data array with an
  object id;
- chat responses are JSON objects containing a choices array;
- streaming responses use text/event-stream, include at least one data event,
  and terminate with [DONE].

Provider-specific fields are not silently treated as universally compatible.

## Offline fixture

The fixture is intended for test scope and binds only to loopback:

~~~
try (var fixture = LocalGatewayFixture.builder()
    .requireBearerToken("fixture-token")
    .build()) {
  var config = ContractConfig.builder(fixture.apiRoot())
      .model("fixture-model")
      .bearerTokenSupplier(() -> "fixture-token")
      .runChat(true)
      .runStreaming(true)
      .build();

  ContractReport report = new ContractRunner(config).run();
  assertTrue(report.passed(), report.summary());
}
~~~

Import the fixture from dev.airouter.contract.fixture.LocalGatewayFixture.

The fixture never emits the configured token. It is not a production server and
should not be exposed beyond the loopback interface.

## Development

The scaffold targets Java 17 bytecode and is tested on Java 17 and Java 21. It
uses JUnit Jupiter 5.14.4 and Jackson Core 2.22.1, both pinned in the POM.

~~~
./mvnw -B -ntp clean verify
./mvnw -B -ntp package
./mvnw -B -ntp javadoc:javadoc
~~~

`package` attaches source and Javadoc JARs. The `release` Maven profile adds PGP
signatures and creates a user-managed Central Portal deployment; ordinary local
builds do not sign or upload anything. Portal credentials and private signing
material remain outside the repository.

The 2026-08-06 release-candidate audit used Apache Maven 3.9.16 to run clean
builds on Java 17 and Java 21. All 19 JUnit tests passed on both runtimes, and
both builds generated the main, sources, and Javadoc JARs. This is build
evidence, not Maven Central publication or third-party adoption evidence.

## Release integrity

Release artifacts are signed by `AI ROUTER Maven Releases
<developer@ai-router.dev>` with this OpenPGP fingerprint:

~~~
424D 95E1 4D48 4268 F13F  0719 6EBA 89CA FFC0 E0D8
~~~

The public key is distributed through `keyserver.ubuntu.com`. Compare the full
fingerprint before trusting a downloaded key or signature.

## Boundaries and non-claims

This project does not claim to be an official OpenAI, Anthropic, Sonatype,
Sub2API, or AI ROUTER SDK. It does not promise complete compatibility with any
provider, zero data loss, zero memory use, automatic retries, or traffic and
conversion results.

## License

MIT. See LICENSE.
