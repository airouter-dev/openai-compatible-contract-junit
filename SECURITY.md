# Security policy

## Scope

The package is a test harness. It must not be used as a production proxy or as
a place to store provider credentials.

## Reporting

Do not put API keys, prompts, response bodies, or private logs in a public issue
or in a pull request. Report suspected vulnerabilities through GitHub's private
security advisory form:

https://github.com/airouter-dev/openai-compatible-contract-junit/security/advisories/new

Include only a minimal, redacted reproducer. For non-sensitive questions, use
the public issue tracker.

## Release safeguards

- Credentials are read only from a caller-controlled environment/property.
- Reports redact credentials and never retain raw response bodies.
- The default check is read-only /v1/models.
- Chat and SSE probes are opt-in and have bounded response sizes and timeouts.
- Release signing keys and Central Portal credentials must stay outside this
  directory and outside source control.
