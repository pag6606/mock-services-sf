# ADR-0012 — Serve `/q/openapi` in every profile, keep Swagger UI in dev only

| | |
| --- | --- |
| **Status** | Accepted |
| **Date** | 2026-07-22 |
| **Scope** | `cuentas-service`, `sf-mock` |
| **Related** | [ADR-0011](0011-code-first-openapi-contract.md), [ADR-0007](0007-protocol-faithful-mock.md) |

## Context

Quarkus does not serve the generated OpenAPI document in production by default. `/q/openapi` and
`/q/swagger-ui` are registered in `dev` and `test`; in `prod` the endpoints are absent unless
explicitly enabled. The default is a deliberate safety posture: an API description is a map of the
attack surface, and most services have no reason to publish one.

That default leaves [ADR-0011](0011-code-first-openapi-contract.md) half-delivered. A contract
generated from code but only reachable on a developer's laptop cannot be imported by an API gateway,
pulled by a consumer's client generator, or verified against the deployed artifact. The contract
that matters is the one the *running* service reports — the one derived from the code actually
deployed, not from a branch someone built locally.

The constraint that makes this decision cheap: **this service is internal.** It sits behind the
bank's gateway, is reachable only from inside the network, and returns account data that already
requires authentication to obtain. The document describes two `GET` endpoints and a response shape.

Two sub-decisions are separable and were treated separately:

1. **The document** (`/q/openapi`) — a static YAML/JSON description. Read-only, no data.
2. **The UI** (`/q/swagger-ui`) — an interactive HTML console that *issues live requests* against
   the service it is served from.

## Decision

We enable the document in every profile, in both modules:

```properties
# --- expose contract in prod -----
quarkus.smallrye-openapi.always-include=true
```

We deliberately do **not** set `quarkus.swagger-ui.always-include`. Swagger UI therefore keeps its
Quarkus default and remains a dev-time convenience, absent from production images.

The asymmetry is the decision. A description of the API is a contract artifact and belongs wherever
the API runs. An interactive request console is a debugging tool: in production it is an
unauthenticated form that lets anyone who reaches the pod fire real calls at real endpoints, and it
ships a non-trivial amount of static JavaScript with the service.

`sf-mock` carries the same setting for consistency of build and deployment
([ADR-0007](0007-protocol-faithful-mock.md)) — though "production" for the mock means a CI or demo
environment, never a released one.

## Consequences

**Positive**

- The deployed artifact is self-describing. `curl https://<host>/q/openapi` returns the contract of
  the code actually running, which is the only version anyone should integrate against.
- Gateway registration, consumer client generation, and contract tests can all point at a live
  environment instead of at a checked-in file that may be stale.
- Contract drift becomes detectable in CI: fetch `/q/openapi` from the deployed service and diff it
  against the previous release. This is only possible because the endpoint exists in `prod`.
- Keeping Swagger UI out of production removes the interactive-console exposure and the static asset
  weight from the runtime image, at zero cost to the contract's usefulness.

**Negative / accepted trade-offs**

- **The endpoint is unauthenticated.** Anyone who can reach the service can enumerate its paths,
  parameters and response schemas. We accept this because reachability is already restricted to the
  internal network — which means **this ADR's safety rests on a network control, not an application
  control**. If `cuentas-service` is ever exposed beyond the perimeter, this setting must be revisited
  before that happens, not after.
- The document is a live disclosure surface: any endpoint added later is published automatically,
  including one added carelessly. Nothing in the build reviews what gets exposed.
- Enabling it in `sf-mock` publishes the `/mock-admin/chaos/{mode}` and `/mock-admin/revocar-tokens`
  endpoints — an advertised way to degrade the mock. Harmless where the mock legitimately runs; a
  clear reason it must never be deployed anywhere real.
- Production and dev now differ in *which* Quarkus endpoints exist (`/q/openapi` in both,
  `/q/swagger-ui` in dev only). Anyone debugging a missing UI in a deployed environment has to know
  this is intentional — hence the comment in `application.properties` pointing here.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Keep the Quarkus default — dev/test only | Safest, and leaves [ADR-0011](0011-code-first-openapi-contract.md) unusable outside a laptop. Consumers would be back to a hand-copied file, reintroducing exactly the drift that ADR as a whole exists to remove. |
| Expose the document **and** Swagger UI in prod | Convenient for support and demos, but ships an unauthenticated request console next to a live banking API. The debugging value does not justify the exposure; `curl` against `/q/openapi` covers the legitimate use. |
| Publish a build-time `openapi.yaml` artifact instead of a live endpoint | Attractive: no runtime exposure, contract versioned per release. Rejected for now because it needs distribution machinery (registry, versioning, consumer pull) that does not exist here — and a build artifact cannot answer "what is *this pod* serving?". Worth revisiting if the service is ever externally exposed. |
| Serve `/q/openapi` behind authentication | The correct answer if this were an internet-facing service. Rejected as premature: the consumers are internal and already inside the perimeter, and adding an auth requirement to fetch a contract complicates gateway and client-generation tooling for no gain at the current exposure level. |
| Move `/q` to a separate management port, firewalled | Quarkus supports it and it is the cleaner control. Deferred — it changes deployment and probe wiring (`/q/health/*` moves too), so it is a larger change than this decision needs. |
