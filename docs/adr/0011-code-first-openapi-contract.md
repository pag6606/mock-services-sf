# ADR-0011 — Generate the OpenAPI contract from the code, not alongside it

| | |
| --- | --- |
| **Status** | Accepted |
| **Date** | 2026-07-22 |
| **Scope** | `cuentas-service`, `sf-mock` |
| **Related** | [ADR-0002](0002-own-consumer-contract-dto.md), [ADR-0009](0009-single-503-at-the-edge.md), [ADR-0012](0012-expose-openapi-in-production.md) |

## Context

`cuentas-service` exists to be consumed by other teams. Those consumers need to know the shape of
`CuentaResponse`, which status codes they must handle, and what `limite` does — and the two facts
that matter most to them are decisions recorded elsewhere: the response DTO is deliberately *not*
the Salesforce `AccountDto` ([ADR-0002](0002-own-consumer-contract-dto.md)), and `503` is a normal,
retryable outcome rather than a defect ([ADR-0009](0009-single-503-at-the-edge.md)). Neither is
discoverable from the endpoint alone.

Until now that knowledge lived in `cuentas-service/README.md` — prose, maintained by hand, with
nothing tying it to the resource classes. Prose drifts silently: a renamed field or a new status
code breaks the document without breaking anything that would notice.

The two usual shapes for an API contract:

- **Contract-first** — hand-write an OpenAPI document, generate or validate the code from it.
- **Code-first** — derive the document from the running resource classes.

Contract-first earns its cost when the contract is negotiated across teams *before* implementation,
or when several services must conform to one shared schema. Neither applies here: this is a single
service whose contract is a design output of this codebase, and there is no consumer waiting to
co-design it.

## Decision

We add `quarkus-smallrye-openapi` to both modules and let the extension derive the document from
the JAX-RS annotations already present. Quarkus serves it at `/q/openapi` (and `/q/swagger-ui`).

Generated structure is left to the extension. We annotate **only where the code cannot express the
intent** — which in practice means the failure contract and non-obvious parameters:

```java
@GET
@Path("/{id}")
@APIResponse(responseCode = "200", description = "Cuenta encontrada")
@APIResponse(responseCode = "404", description = "Cuenta no encontrada en el CRM")
@APIResponse(responseCode = "503", description = "CRM no disponible temporalmente")
public CuentaResponse porId(@PathParam("id") String id) { … }

@GET
@Operation(summary = "Lista cuentas con límite configurable")
public List<CuentaResponse> listar(@QueryParam("limite") @DefaultValue("10") int limite) { … }
```

The `404`/`503` annotations exist because those responses are produced by `DomainExceptionMapper`,
not by the method signature — no generator could infer them.

Document-level metadata stays in `application.properties` rather than a `@OpenAPIDefinition` class,
so title, version and description are configuration rather than code:

```properties
mp.openapi.extensions.smallrye.info.title=API de Cuentas - Integración Salesforce
mp.openapi.extensions.smallrye.info.version=1.0.0
mp.openapi.extensions.smallrye.info.description=Servicio de consulta de cuentas bancarias, …
```

`sf-mock` gets the same extension for a different reason: its Salesforce-facing endpoints are an
imitation of someone else's contract ([ADR-0007](0007-protocol-faithful-mock.md)), and a generated
document makes the extent of that imitation — and the `/mock-admin` chaos surface — inspectable.

## Consequences

**Positive**

- The document cannot drift from the implementation. It *is* the implementation, read reflectively
  at build time; a renamed field changes the contract in the same commit.
- Consumers get a machine-readable artifact: client generation, request validation, contract tests,
  and API-gateway import all become possible without us maintaining anything extra.
- The `503` and `404` responses are now part of the published contract rather than folklore in a
  README, which is what makes [ADR-0009](0009-single-503-at-the-edge.md)'s retry semantics
  actionable for a consumer.
- Annotating is incremental. The document is useful with zero annotations and improves as we add
  them; there is no all-or-nothing migration.
- `sf-mock`'s admin surface is self-describing, so a developer can find the chaos modes without
  reading its source.

**Negative / accepted trade-offs**

- **The contract can now change by accident.** With code-first, any refactor of `CuentaResponse` is
  a potential breaking change for consumers, and nothing in the build stops it. Contract-first would
  have made the contract a reviewed artifact.
- `mp.openapi.…info.version=1.0.0` is a hand-maintained constant with no link to the actual API
  shape. It will lie the first time someone forgets to bump it.
- Descriptions live in annotations on the resource, mixing documentation concerns into the adapter.
  They are also in Spanish while the ADRs are in English — deliberate, since they are consumer-facing
  in a Spanish-speaking context, but it makes the codebase bilingual.
- The extension adds a build-time scan and a small runtime footprint to both modules, including
  `sf-mock`, which no external consumer will ever integrate against.
- What is *not* annotated is still invisible: the generated schema describes types, not meaning. A
  consumer learns `saldo` is a `BigDecimal`, not what currency it is in.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Contract-first — hand-written `openapi.yaml`, code validated against it | The right choice when a contract is negotiated ahead of implementation or shared across services. Here it would add a second artifact to keep in sync, with no second party to negotiate with — and drift between file and code is exactly the failure we are trying to remove. |
| Keep prose documentation in `README.md` only | Readable, but unverifiable, not machine-consumable, and already the source of the drift problem. It remains as the *narrative* layer; the generated document is now the reference. |
| Annotate exhaustively (`@Schema` on every DTO field, examples everywhere) | Produces a richer document, but the annotation weight lands in adapter classes and rots as fast as prose. We annotate only what the code cannot express. |
| Skip OpenAPI in `sf-mock` | Defensible — it has no external consumers. Added anyway because the mock's fidelity claim ([ADR-0007](0007-protocol-faithful-mock.md)) is easier to audit against a generated document than against source. |
