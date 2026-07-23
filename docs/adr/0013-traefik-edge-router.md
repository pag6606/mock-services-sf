# ADR-0013 — Traefik as the edge router, with no gateway logic in the services

| | |
| --- | --- |
| **Status** | Accepted |
| **Date** | 2026-07-22 |
| **Scope** | cross-cutting (deployment topology) |
| **Related** | [ADR-0009](0009-single-503-at-the-edge.md), [ADR-0012](0012-expose-openapi-in-production.md), [ADR-0007](0007-protocol-faithful-mock.md) |

## Context

Until now the compose stack published both containers directly: `cuentas-service` on `:8080`,
`sf-mock` on `:8081`. A consumer had to know two hosts and two ports, and the topology on a laptop
looked nothing like the topology this service is designed for — where a banking API sits behind a
gateway and is never addressed directly.

That gap has cost us twice in the documentation. [ADR-0012](0012-expose-openapi-in-production.md)
accepts an unauthenticated `/q/openapi` on the explicit grounds that "it sits behind the bank's
gateway" — a perimeter that existed in prose but nowhere you could run. And the `/q/*` management
endpoints (health, metrics, OpenAPI) are described as internal while being, in the demo, exactly as
reachable as the business API.

The forces:

- **The demo should show the real shape.** An architect reading this reference should see where the
  perimeter is, not be asked to imagine it.
- **The perimeter must not leak into the services.** The whole point of
  [ADR-0001](0001-hexagonal-architecture.md) is that infrastructure concerns stay outside the domain.
  Routing, path rewriting, and exposure policy are deployment concerns; a service that hardcodes its
  own public path has absorbed a decision that belongs to whoever deploys it.
- **Weight matters.** This is a laboratory that must `docker compose up` on a laptop. A full API
  management platform (Kong + database, Apigee, Gravitee) would dominate the stack it is meant to
  illustrate.
- **We already have a container runtime.** Both services are containers with labels; a router that
  reads that metadata needs no separate route registry to drift out of sync.

A distinction worth stating, because the term "API gateway" invites confusion: what this stack needs
is an **edge router** — routing, path rewriting, single entry point. It is *not* an API management
product: no plan/quota management, no developer portal, no key issuance, no monetization.

## Decision

We put **Traefik v3 in front of both services as an edge router**, and we express routing as
**container labels on the routed service**, not as central configuration.

`gateway/traefik.yml` is deliberately minimal — one entrypoint, the container provider, and an
opt-in posture:

```yaml
entryPoints:
  web:
    address: ":80"

providers:
  docker:
    exposedByDefault: false   # a container is routable only if it says so
```

Each service declares its own route next to its own definition:

```yaml
# cuentas-service
- "traefik.http.routers.cuentas.rule=PathPrefix(`/api/cuentas`)"
- "traefik.http.middlewares.strip-cuentas.stripprefix.prefixes=/api"
- "traefik.http.routers.cuentas.middlewares=strip-cuentas"
```

Three properties follow from this, and they are the decision:

**1. One entry point.** `:80` is the only published business port. `cuentas-service` and `sf-mock`
no longer publish ports at all — they are reachable only over the compose network. The public
surface becomes `/api/cuentas/**` and `/mock/**`.

**2. The public path is not the service path.** `PathPrefix(/api/cuentas)` matches, `stripprefix /api`
rewrites, and the service receives `/cuentas/{id}` — exactly the path its JAX-RS annotations declare.
The service is unaware it is mounted under `/api`; that mapping can change at deploy time without
touching code, and `/q/openapi` keeps describing the service's own paths
([ADR-0011](0011-code-first-openapi-contract.md)).

**3. `exposedByDefault: false` makes exposure a positive act.** A new container added to the stack is
invisible from the edge until someone writes a label. The failure mode of a permissive default —
something reaching the perimeter because nobody remembered to exclude it — cannot occur.

**What we deliberately did *not* put in the gateway.** Authentication, rate limiting, and TLS
termination are all things Traefik can do and all things a real deployment would use it for. They
are out of scope here because each would need real credentials or certificates to be anything but
theatre, and the resilience story this reference tells ([ADR-0003](0003-two-lane-error-taxonomy.md),
[ADR-0009](0009-single-503-at-the-edge.md)) lives in the service, not at the edge. The gateway is
present to establish *where the perimeter is*, not to implement it.

**Traffic that does not pass through the gateway.** `cuentas-service` still calls `sf-mock` directly
at `http://sf-mock:8081` over the compose network. This is intentional: the gateway handles
**north-south** traffic (consumer → platform). East-west traffic between the service and its system
of record is not a gateway concern, and routing it through the edge would misrepresent the
production topology, where Salesforce is an external SaaS endpoint reached over the internet, not a
neighbour behind our own router.

## Consequences

**Positive**

- The perimeter that [ADR-0012](0012-expose-openapi-in-production.md) relies on is now a running
  component. "It is behind the gateway" is demonstrable, and the natural next step — restricting
  `/q/**` at the edge — is a label change, with an obvious place to put it.
- Consumers see one host and one stable path space. Replacing `sf-mock` with a real Salesforce org,
  or scaling `cuentas-service` to several replicas, changes nothing a consumer can observe.
- Routes live beside the service they route to. Adding a service means adding labels to that
  service — there is no central file that must be edited in lockstep and that silently rots when
  someone forgets.
- The topology transfers. `PathPrefix` + `stripprefix` + label-driven discovery is the same shape as
  a Kubernetes `Ingress`/`Gateway API` rule, so what the demo shows is a rehearsal of the real
  deployment rather than a laptop-only trick.

**Negative / accepted trade-offs**

- **The dashboard runs with `insecure: true`.** That is an unauthenticated admin UI on `:8090`,
  bound to the host. Acceptable only because this stack is a laboratory on a developer machine; it
  is flagged in the file itself and must never be carried into a shared environment.
- **The gateway mounts the container runtime socket.** Read-only, but a container that can read the
  Podman/Docker socket can enumerate the whole stack — a real deployment gives it a scoped API
  credential or moves discovery to Kubernetes, where the equivalent is a narrowly-bound RBAC role.
- **One more hop, one more failure mode.** If Traefik is down, everything is down, and a routing
  mistake now presents as a `404` from the gateway rather than from the service. The distinction
  matters when debugging: the first question becomes "did the request reach the container at all?"
- **The socket path is host-specific.** `/run/user/1000/podman/podman.sock` assumes rootless Podman
  and UID 1000. On Docker, or a different UID, the mount must be adjusted — the price of using the
  runtime as a discovery mechanism.
- **Direct port access is gone.** `curl localhost:8080` no longer works; every documented call goes
  through `:80` with its public prefix. Dev mode (`quarkus:dev`, no gateway) still uses the direct
  ports, so the two run modes now address the services differently — a small but real source of
  confusion, called out in the README.
- **`/mock/**` is routed at all.** Exposing the mock through the same edge as the business API is a
  demo convenience; the mock is a test double and would not exist in an environment that has a real
  perimeter ([ADR-0007](0007-protocol-faithful-mock.md)).

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| **Keep publishing ports directly** (the status quo) | Zero moving parts, and it leaves the perimeter imaginary. The reference is aimed at architects evaluating a topology; omitting the component every real deployment has is the more misleading simplification. |
| **Nginx as reverse proxy** | Lighter still and universally understood, but routes live in a static config file that must be hand-edited and reloaded when a service is added. Loses the label-driven discovery that keeps routing next to the routed service — the property that makes this scale past two containers. |
| **Kong / Gravitee / a full API management platform** | The right answer when you need key management, quotas, plans, and a developer portal. Here it would add a database and an admin plane to a two-service demo, and dominate the architecture it exists to illustrate. Revisit if this reference ever grows a consumer-onboarding story. |
| **Quarkus-based gateway (a third module)** | Tempting for stack consistency, and it would put routing logic in application code we maintain — the opposite of the decision above. Edge routing is a solved infrastructure problem; writing it in Java means owning it. |
| **Kubernetes Ingress from the start** | Where this topology actually lands in production, and the labels above map onto it directly. Rejected as the *demo* substrate because it requires a cluster to run `docker compose up`, raising the cost of the reference for every reader. |
| **Route east-west traffic through the gateway too** | Would centralize all traffic and give a single observability point. Rejected because it misrepresents production: Salesforce is an external SaaS dependency reached over the internet, and modelling it as a service behind our own edge router would teach the wrong topology. |
| **Put resilience (retry, circuit breaking) at the edge** | Traefik supports both, and a service mesh would argue for exactly this. Rejected because the retry policy here is *semantic* — it depends on knowing which operations are idempotent ([ADR-0006](0006-retry-on-idempotent-operations-only.md)) and which CRM errors are technical rather than business ([ADR-0003](0003-two-lane-error-taxonomy.md)). That knowledge lives in the adapter and cannot be expressed in a routing rule. |
