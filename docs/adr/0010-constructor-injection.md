# ADR-0010 — Use constructor injection, not field injection

| | |
| --- | --- |
| **Status** | Accepted |
| **Date** | 2026-07-21 |
| **Scope** | `cuentas-service` |
| **Related** | [ADR-0001](0001-hexagonal-architecture.md) |

## Context

The service originally used CDI field injection:

```java
@Inject
SalesforceClient sf;          // package-private field, set by the container
```

It is concise and it is what most Quarkus examples show. It also has three properties that work
against [ADR-0001](0001-hexagonal-architecture.md):

- **Dependencies are invisible in the API.** A class's collaborators are discoverable only by
  reading its fields, not from its constructor.
- **Instances cannot be built without a container.** `new SalesforceCrmAdapter()` produces an object
  with `null` collaborators, so a plain unit test must either start CDI or use reflection to poke
  fields.
- **Fields cannot be `final`.** The container assigns them after construction, so nothing prevents
  later reassignment and there is no safe-publication guarantee.

The third point matters concretely here: `SalesforceTokenService` is accessed concurrently, and
`SalesforceCrmAdapter` is `@ApplicationScoped` — a single instance shared across all request threads.

## Decision

All collaborators are injected through the constructor and held in `final` fields:

```java
@ApplicationScoped
public class SalesforceCrmAdapter implements ClienteCrmPort {

    private final SalesforceClient sf;
    private final SalesforceTokenService tokenService;

    @Inject
    public SalesforceCrmAdapter(
            @RestClient SalesforceClient sf,
            SalesforceTokenService tokenService) {
        this.sf = sf;
        this.tokenService = tokenService;
    }
```

The domain and application layers already had no choice — `ConsultarCuentaUseCase` is framework-free
and takes its port through the constructor, which is what makes `UseCaseProducer` possible. This
decision extends the same rule to infrastructure classes, so the codebase has **one** wiring style.

## Consequences

**Positive**

- Every class can be instantiated with `new` in a test, passing fakes or mocks directly. No CDI
  container, no reflection, no `@QuarkusTest` for logic that does not need one.
- The constructor is an honest dependency list. A constructor with seven parameters is uncomfortable
  to write, and that discomfort is useful design feedback that a hidden `@Inject` field suppresses.
- `final` fields are safely published under the JMM — important for `@ApplicationScoped` singletons
  shared across request threads.
- Objects are fully initialised the moment they exist. There is no window in which a collaborator is
  still `null`.

**Negative / accepted trade-offs**

- More boilerplate: a constructor, an assignment, and a field declaration per dependency, where
  field injection needed one line.
- Circular dependencies between beans, which field injection tolerates by resolving lazily, now fail
  at construction. This is arguably a benefit — a cycle is a design smell — but it can force a
  refactor at an inconvenient moment.
- CDI requires a no-arg constructor for normal-scoped beans to be proxyable; Quarkus generates it,
  but the constraint occasionally surfaces in unusual cases.
- Mixed styles remain possible. Nothing in the build enforces this rule, so it relies on review.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Field injection (`@Inject` on fields) | The status quo we moved away from. Concise, but hides dependencies, prevents `final`, and makes plain unit tests impossible without container or reflection. |
| Setter injection | Also allows `new`, but leaves objects temporarily half-built and still cannot use `final`. Worst of both. |
| Lombok `@RequiredArgsConstructor` | Removes the boilerplate, but adds an annotation processor and a compile-time dependency to save a handful of lines in a reference implementation whose job is to be readable. |
