# ADR-0006 — Apply `@Retry` only to idempotent operations

| | |
| --- | --- |
| **Status** | Accepted |
| **Date** | 2026-07-20 |
| **Scope** | `cuentas-service` |
| **Related** | [ADR-0003](0003-two-lane-error-taxonomy.md), [ADR-0009](0009-single-503-at-the-edge.md) |

## Context

Retrying is not free of semantics. When a call fails, we often cannot tell **whether the server
processed it**:

- A `500` may be raised before or after the write committed.
- A timeout tells us nothing at all — the request may still be executing.
- A dropped connection can lose the *response* to a perfectly successful write.

For a read, repeating the call is harmless. For a *creating* write, repeating it can produce a
duplicate record. Salesforce will happily create two `Case` records if we send the same `POST`
twice, and no error will be reported — the damage is silent and lands in the system of record.

## Decision

Retry policy follows the **idempotency of the operation**, not convenience:

| Operation | HTTP | Idempotent? | Policy |
| --- | --- | --- | --- |
| `buscarPorId` | `GET` | Yes | `@Retry(maxRetries = 2, delay = 500, jitter = 200)` |
| `listar` | `GET` (SOQL) | Yes | `@Retry(maxRetries = 2, delay = 500)` |
| `actualizarEstado` | `PATCH` | Yes — same final state | `@Retry(maxRetries = 1, delay = 500)` |
| *creating* `POST` | `POST` | **No** | **No `@Retry`** without an idempotency key |

`PATCH` is retried because it sets a field to a fixed value: applying it twice leaves the record in
exactly the state one application would. It is retried **once** rather than twice — a write is
worth one recovery attempt, not a sustained campaign against a struggling org.

The rule is recorded in the code at the point where it must be honoured:

```java
// OJO: PATCH idempotente (mismo estado final) → seguro reintentar.
// Un POST de creación NO llevaría @Retry sin idempotency key.
@Retry(maxRetries = 1, delay = 500, retryOn = { CrmNodisponibleException.class })
@Timeout(8_000)
public void actualizarEstado(String idCrm, EstadoCuenta estado) { … }
```

All retries fire on `CrmNodisponibleException` only — the technical lane from
[ADR-0003](0003-two-lane-error-taxonomy.md). Business `4xx` is never retried.

**Jitter** (200 ms on the account lookup) spreads the retry wave. Without it, N clients that failed
together retry together and hit a recovering org with a synchronized thundering herd.

## Consequences

**Positive**

- Transient `5xx`/`429`/timeouts are absorbed without the caller ever seeing them.
- No silent duplicate records in the system of record — the failure mode that is hardest to detect
  and most expensive to unwind.
- Jitter prevents retry storms from re-killing a CRM that is coming back up.
- Bounded amplification: at most 3 CRM calls per read, 2 per write.

**Negative / accepted trade-offs**

- **This discipline is a convention, not a constraint.** Nothing stops the next developer from
  adding `@Retry` to a creating `POST`. The comment in the code is the only guardrail, and comments
  do not fail builds.
- Retries multiply load on a degraded CRM at exactly the wrong moment. The circuit breaker
  ([ADR-0003](0003-two-lane-error-taxonomy.md)) is what ultimately stops this.
- Retries consume the `@Timeout(8s)` budget, so a caller can wait the full 8 seconds *plus* retry
  delays before receiving an error.
- A non-idempotent operation added later gets **no** resilience at all under this rule, until
  someone implements idempotency keys.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Retry every operation uniformly | Simple and consistent, but duplicates records on creating writes — a data-integrity bug in the system of record, discovered weeks later by a human. |
| Retry nothing | Every transient blip becomes a consumer-visible error, discarding the main benefit of a resilience layer. |
| Idempotency keys on all writes, then retry freely | The correct long-term answer. It needs a key generation and dedup strategy that Salesforce does not provide out of the box, so it is deferred until a creating write actually exists. |
| Retry `PATCH` twice, like reads | Marginal gain; writes against a failing CRM are better failed fast and surfaced than pushed repeatedly. |
