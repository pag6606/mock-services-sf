# ADR-0009 — Collapse every "CRM unavailable" signal into one `503 + Retry-After` at the edge

| | |
| --- | --- |
| **Status** | Accepted |
| **Date** | 2026-07-21 |
| **Scope** | `cuentas-service` |
| **Related** | [ADR-0003](0003-two-lane-error-taxonomy.md), [ADR-0006](0006-retry-on-idempotent-operations-only.md) |

## Context

By the time a failure reaches the HTTP edge, "the CRM did not answer" can arrive as **three
different exception types**, produced by three different mechanisms:

| Exception | Raised by | Means |
| --- | --- | --- |
| `CrmNodisponibleException` | our adapter, after `@Retry` exhausts | the CRM kept failing |
| `TimeoutException` | `@Timeout(8s)` | the CRM was too slow |
| `CircuitBreakerOpenException` | `@CircuitBreaker` | we did not even call the CRM |

These are meaningfully different *to us*. To a consumer they are the same fact: the request cannot
be served right now, and trying again shortly is reasonable. Left unmapped, the last two would
surface as generic `500`s — telling consumers the fault is ours and permanent, when it is neither.

## Decision

`DomainExceptionMapper` maps all three to a single response:

```java
if (e instanceof CrmNodisponibleException          // retries exhausted
        || e instanceof CircuitBreakerOpenException // breaker open
        || e instanceof TimeoutException) {         // @Timeout fired
    return Response.status(503)
            .header("Retry-After", "15")
            .entity(Map.of("error", "CRM_NO_DISPONIBLE",
                           "detalle", "Intente nuevamente en unos minutos"))
            .build();
}
```

The full mapping at the edge:

| Condition | Status | Body `error` |
| --- | --- | --- |
| `CuentaNoEncontradaException` | `404` | `CUENTA_NO_ENCONTRADA` |
| the three CRM-unavailable signals | `503` + `Retry-After: 15` | `CRM_NO_DISPONIBLE` |
| anything else | `500` | `ERROR_INTERNO` |

The `Retry-After: 15` is deliberately aligned with the circuit breaker's 15-second open window
([ADR-0003](0003-two-lane-error-taxonomy.md)): a client that honours the header retries at roughly
the moment the breaker moves to half-open.

The final branch **logs the exception before responding**, because an unmapped exception reaching
the edge is a defect, not a normal outcome:

```java
Log.error("Excepción no mapeada llegó al borde HTTP", e);
```

## Consequences

**Positive**

- One failure contract for consumers. They handle `503` and honour `Retry-After` — they never need
  to know what a circuit breaker is.
- The status is honest: `503` says "try later", which is true in all three cases. A `500` would say
  "we are broken", which is false when the breaker is simply doing its job.
- `Retry-After` gives clients a concrete, correct backoff instead of guessing, and its alignment
  with the breaker window means well-behaved clients retry exactly when it can help.
- Internal resilience mechanics can be retuned — breaker thresholds, timeout budgets — without
  changing the public contract.
- Unmapped exceptions are logged with a stack trace and returned as an opaque `500`, so internals
  never leak to consumers while remaining diagnosable.

**Negative / accepted trade-offs**

- Consumers cannot distinguish "the CRM is slow" from "we are shedding load" from "the CRM returned
  errors". That distinction lives only in the logs.
- `Retry-After: 15` is a constant. It does not reflect the breaker's actual remaining open time, so
  a client retrying on it can still arrive early and get another `503`.
- The mapper is `ExceptionMapper<RuntimeException>`, a broad catch. It handles anything unexpected
  as `500` — safe, but it means a genuine bug is reported to the consumer identically to an
  infrastructure fault.
- Type-checking with `instanceof` means a new fault-tolerance exception type is silently mapped to
  `500` until someone remembers to add it here.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| A distinct status per cause (`503` / `504` / `429`) | More precise, but exposes internal resilience mechanics as public contract — retuning the breaker would become a breaking change — and gives consumers three cases where one action ("retry later") is correct for all. |
| Let the three propagate as `500` | Misrepresents transient conditions as permanent server faults and gives clients no reason to retry. |
| Distinguish only via the response body, keeping `503` | A reasonable middle ground: same status, richer `error` code. Rejected for now to keep the consumer contract minimal; the body could carry a sub-code later without breaking anyone. |
| Per-exception `ExceptionMapper` classes | More idiomatic JAX-RS, but scatters the edge contract across several files when the value here is seeing the whole mapping in one place. |
