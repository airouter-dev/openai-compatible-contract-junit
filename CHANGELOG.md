# Changelog

All notable changes to this project will be documented here.

## 0.1.0

- Added a JUnit 5 extension for bounded OpenAI-compatible HTTP contract checks.
- Added read-only `/v1/models` validation with opt-in chat and SSE probes.
- Added redacted diagnostics, exact media-type checks, strict response bounds,
  and a total deadline covering slow response bodies.
- Added a loopback-only deterministic fixture for offline tests.
- Embedded the MIT license and a stable automatic module name in the main JAR.
