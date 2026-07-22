# ADR-0007 — Build a protocol-faithful mock (`sf-mock`) instead of using recorded stubs

| | |
| --- | --- |
| **Status** | Accepted |
| **Date** | 2026-07-20 |
| **Scope** | `sf-mock`, CI |
| **Related** | [ADR-0004](0004-oauth2-jwt-bearer-flow.md), [ADR-0008](0008-in-memory-mock-state.md) |

## Context

The resilience layer ([ADR-0003](0003-two-lane-error-taxonomy.md),
[ADR-0006](0006-retry-on-idempotent-operations-only.md)) is the part of this service most likely to
be wrong, and the hardest to exercise: it only runs when Salesforce misbehaves. You cannot ask a
production org to return `500`s on demand, and a sandbox will not help either.

Developing against a live org has further costs: every developer needs credentials, API limits are
shared and exhaustible, tests mutate real data, and CI cannot run without network access to
Salesforce.

The usual answer is recorded stubs (WireMock mappings, VCR-style cassettes). They replay known
responses but do not *behave* — they cannot mint a token, cannot validate one, and cannot decide to
start failing.

## Decision

We build `sf-mock`, a second Quarkus application that implements the Salesforce REST API v60.0
**surface** faithfully enough that `cuentas-service` cannot tell the difference:

| Endpoint | Behaviour |
| --- | --- |
| `POST /services/oauth2/token` | Accepts the JWT Bearer grant, issues a real (opaque) token, tracks it as valid |
| `GET /services/data/v60.0/sobjects/Account/{id}` | Returns PascalCase `AccountDto` JSON, `404` for unknown ids |
| `GET /services/data/v60.0/query` | SOQL query with paging (`done`, `nextRecordsUrl`) |
| `PATCH …/sobjects/Account/{id}` | Applies the update to the store |
| `POST /mock-admin/chaos/{mode}` | Switches fault injection: `OK`, `ERROR_500`, `TIMEOUT`, `RATE_LIMIT` |
| `POST /mock-admin/revocar-tokens` | Invalidates all issued tokens — simulates server-side session expiry |

Crucially it holds **state**: `MockState` tracks issued tokens and the current chaos mode, so it can
reject a token it never issued, and `SalesforceApiFilter` enforces auth on every data request the
way the real platform does.

Swapping the mock for a real org is a configuration change — `SF_BASE_URL` and `SF_LOGIN_URL` —
with **no code change** in `cuentas-service`.

## Consequences

**Positive**

- **The production code path runs unchanged in dev and CI.** The real REST client, the real auth
  filter, the real token service, the real fault-tolerance annotations — all exercised. Recorded
  stubs would bypass most of that.
- Failure modes become a one-line test setup:
  ```bash
  curl -X POST http://localhost:8081/mock-admin/chaos/ERROR_500
  curl http://localhost:8080/cuentas/001AAA        # → 503, after retry + breaker
  ```
- The `401` refresh path ([ADR-0005](0005-token-cache-and-401-refresh.md)) is genuinely testable via
  `/mock-admin/revocar-tokens` — the mock can invalidate a token the service still believes in,
  which is exactly the race the retry exists for.
- No credentials, no API limits, no network dependency, no shared-sandbox contention. CI is
  hermetic and a developer can work offline.
- Onboarding is `docker compose up`.

**Negative / accepted trade-offs**

- **A second application to maintain.** It has its own build, its own CI job, and its own README.
- **Fidelity is a claim, not a guarantee.** The mock implements our understanding of the API. Where
  that understanding is wrong, tests pass and production fails — and the mock's confidence makes the
  gap *less* visible than an obviously-fake stub would.
- The mock does not model everything: no governor limits, no field-level security, no validation
  rules, no bulk API, no real SOQL parser.
- It must be kept in step with the API version the client targets. A bump to `v61.0` is now a
  two-module change.

Mitigation: the mock replaces the org for *development and CI*, never for release confidence.
Contract verification against a real sandbox remains necessary before production.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| WireMock recorded stubs | Cheap and used in `cuentas-service`'s own tests (`WireMockSalesforce`) for narrow cases, but stubs cannot mint or validate tokens, cannot maintain state across calls, and cannot be told to start failing. Chaos testing would be static mappings, not behaviour. |
| A shared Salesforce developer sandbox | Real fidelity, but needs credentials for everyone, has shared mutable state, is subject to API limits, cannot be made to return `500` on demand, and blocks CI when unavailable. |
| Mock at the `ClienteCrmPort` boundary only | Excellent for unit-testing the use case — and we do exactly that — but it skips the adapter, the REST client, auth, and every fault-tolerance annotation. It tests the domain, not the integration. |
| Salesforce's own mocking tooling | Aimed at Apex/Platform development, not at external consumers of the REST API. |
