# Architecture Decision Records

> Each ADR captures **one** decision: the forces at play, what was decided, and what it costs us.
> Records are immutable once accepted — to change a decision, add a new ADR and mark the old one `Superseded by ADR-XXXX`.

Back to the [architecture reference](../architecture.md).

## Index

| # | Decision | Status | Date |
| --- | --- | --- | --- |
| [ADR-0001](0001-hexagonal-architecture.md) | Hexagonal architecture (ports & adapters) | Accepted | 2026-07-20 |
| [ADR-0002](0002-own-consumer-contract-dto.md) | Own consumer contract DTO, distinct from the Salesforce DTO | Accepted | 2026-07-20 |
| [ADR-0003](0003-two-lane-error-taxonomy.md) | Two-lane error taxonomy: business vs. technical | Accepted | 2026-07-20 |
| [ADR-0004](0004-oauth2-jwt-bearer-flow.md) | OAuth 2.0 JWT Bearer flow for server-to-server auth | Accepted | 2026-07-20 |
| [ADR-0005](0005-token-cache-and-401-refresh.md) | Local token cache with early refresh + transparent 401 retry | Accepted | 2026-07-20 |
| [ADR-0006](0006-retry-on-idempotent-operations-only.md) | `@Retry` on idempotent operations only | Accepted | 2026-07-20 |
| [ADR-0007](0007-protocol-faithful-mock.md) | A protocol-faithful mock instead of recorded stubs | Accepted | 2026-07-20 |
| [ADR-0008](0008-in-memory-mock-state.md) | In-memory state in the mock | Accepted | 2026-07-20 |
| [ADR-0009](0009-single-503-at-the-edge.md) | Collapse every "CRM unavailable" signal into one `503` at the edge | Accepted | 2026-07-21 |
| [ADR-0010](0010-constructor-injection.md) | Constructor injection over field injection | Accepted | 2026-07-21 |
| [ADR-0011](0011-code-first-openapi-contract.md) | Generate the OpenAPI contract from the code, not alongside it | Accepted | 2026-07-22 |
| [ADR-0012](0012-expose-openapi-in-production.md) | Serve `/q/openapi` in every profile, keep Swagger UI in dev only | Accepted | 2026-07-22 |
| [ADR-0013](0013-traefik-edge-router.md) | Traefik as the edge router, with no gateway logic in the services | Accepted | 2026-07-22 |

## Template

New records follow [`_template.md`](_template.md).

## Numbering

Four digits, monotonically increasing, never reused. The number is the permanent identifier —
file names may be renamed for clarity, the number may not change.
