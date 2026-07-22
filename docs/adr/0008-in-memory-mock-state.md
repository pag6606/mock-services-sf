# ADR-0008 — Keep the mock's state in memory, with no persistence

| | |
| --- | --- |
| **Status** | Accepted |
| **Date** | 2026-07-20 |
| **Scope** | `sf-mock` |
| **Related** | [ADR-0007](0007-protocol-faithful-mock.md) |

## Context

`sf-mock` holds three kinds of state: the seeded `Account` records, the set of tokens it has issued,
and the current chaos mode. It needs to serve them consistently within a run — a token it issued
must validate on the next request, and a `PATCH` must be visible to a following `GET`.

The obvious next step is a database. That would make the mock's state durable across restarts and
shareable across instances.

## Decision

State lives in memory only:

- `AccountStore` seeds ~315 `Account` records at startup.
- `MockState` holds issued tokens in a `ConcurrentHashMap.newKeySet()` and the chaos mode in a
  `volatile` field.

There is no database, no schema, no migration, and no persistence layer. **Every restart is a clean,
identical slate** — that is a feature, not a limitation.

## Consequences

**Positive**

- Zero infrastructure. `docker compose up` starts the whole stack; no database container, no
  connection string, no migration step.
- Fast boot, which matters because CI starts the mock on every run and native images boot in
  milliseconds.
- **Deterministic tests.** State resets on restart, so a test run cannot be polluted by leftovers
  from a previous one — the single most common source of flaky integration tests.
- No cleanup logic and no test-ordering constraints.
- Concurrency is handled with a concurrent set and a `volatile` field; no transactions to reason
  about.

**Negative / accepted trade-offs**

- **Not durable.** Anything written via `PATCH` is lost on restart. Long-lived manual testing
  scenarios must be re-seeded.
- **Not shareable.** Two `sf-mock` replicas would have independent token sets and independent chaos
  modes, so a token issued by one is rejected by the other. The mock is effectively single-instance.
- The chaos mode is global and process-wide: two developers or two parallel CI jobs sharing one mock
  instance will interfere with each other's fault injection. Each run needs its own instance.
- Memory grows with issued tokens — they accumulate until `revocarTodos()` or a restart. Irrelevant
  at test scale, but it is an unbounded set.

All of these are acceptable because the mock's job is to be a **disposable test double**, not a data
store. Any requirement that would force persistence — durable fixtures, shared state across replicas
— is a signal to use a real sandbox org instead.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Embedded H2 / SQLite | Adds a schema, a persistence layer, and migrations to a throwaway test double, in exchange for durability nobody has asked for. |
| PostgreSQL container | All of the above plus a second container in every developer's compose file and every CI job, to persist data whose *whole value* is being reset. |
| File-backed JSON store | Durability without a database, but reintroduces exactly the cross-run state pollution that makes integration tests flaky. |
| Quarkus Dev Services database | Would make the local story easy, but it is still a database, and the state it buys is state we actively want to discard. |
