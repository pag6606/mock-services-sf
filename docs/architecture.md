# Architecture Reference — Salesforce Banking Integration

> **Audience:** Solution architects evaluating patterns for integrating a system of record (Salesforce CRM) with a domain service under real-world reliability and security constraints.
>
> This repository is a **reference implementation**, not a product. It demonstrates how to isolate a volatile external dependency behind a clean domain boundary, make the integration resilient, and keep it fully testable without a live Salesforce org.

## Table of contents

1. [What this reference demonstrates](#1-what-this-reference-demonstrates)
2. [System context (C4 L1)](#2-system-context-c4-l1)
3. [Container view (C4 L2)](#3-container-view-c4-l2)
4. [Component view — `cuentas-service` (hexagonal)](#4-component-view--cuentas-service-hexagonal)
5. [Runtime behaviour (sequence diagrams)](#5-runtime-behaviour-sequence-diagrams)
6. [Cross-cutting concerns](#6-cross-cutting-concerns)
7. [Architecture decisions (ADRs)](#7-architecture-decisions)
8. [Deployment view](#8-deployment-view)
9. [CI/CD pipeline](#9-cicd-pipeline)

---

## 1. What this reference demonstrates

| Concern | Pattern applied | Where to look |
| --- | --- | --- |
| **Decoupling from the CRM** | Hexagonal architecture (ports & adapters) | `domain.port.out.ClienteCrmPort` ↔ `infrastructure.out.salesforce.SalesforceCrmAdapter` |
| **Protecting the domain from CRM data shapes** | Anti-corruption layer (DTO ↔ domain mapping) | `SalesforceCrmAdapter.aDominio(...)`, `EstadoCuenta.DESCONOCIDO` fallback |
| **Surviving CRM instability** | Retry + Timeout + Circuit Breaker (MicroProfile Fault Tolerance) | Annotations on `SalesforceCrmAdapter` |
| **Zero-touch authentication** | OAuth 2.0 JWT Bearer flow with token caching | `SalesforceTokenService`, `SalesforceAuthFilter` |
| **Business vs. technical error separation** | Exception translation + edge mapping | `traducir(...)`, `DomainExceptionMapper` |
| **Testability without a live org** | A protocol-faithful mock + chaos injection | `sf-mock` module |

| **A single, stable entry point** | Edge router (Traefik) with label-driven routing | `gateway/traefik.yml`, `traefik.*` labels in `docker-compose.yml` |

The containers:

| Container | Published port | Role |
| --- | --- | --- |
| `gateway` (Traefik v3) | `80` · `8090` (dashboard) | Edge router. The only published business port; routes `/api/cuentas/**` and `/mock/**` to the services behind it. |
| `cuentas-service` | — (internal `8080`) | Banking account API. Owns the domain, resilience, and CRM integration. |
| `sf-mock` | — (internal `8081`) | Stand-in for Salesforce REST API v60.0. Enables local dev, CI, and fault-injection testing. |

Only the gateway is reachable from the host. The two Quarkus containers publish no ports and are
addressable only over the compose network — see [ADR-0013](adr/0013-traefik-edge-router.md).

---

## 2. System context (C4 L1)

```mermaid
flowchart LR
    consumer["Consumer<br/>(web / mobile / BFF)"]
    subgraph platform["Platform boundary"]
        gw["Edge router (Traefik)<br/>single entry point :80"]
        cuentas["cuentas-service<br/>Banking Account API"]
        gw -->|"routes · strips /api"| cuentas
    end
    crm["Salesforce CRM<br/>System of record<br/>(real org in prod · sf-mock in dev/CI)"]

    consumer -->|"HTTPS · JSON<br/>GET /api/cuentas/{id}"| gw
    cuentas -->|"HTTPS · OAuth2 JWT Bearer<br/>REST API v60.0 · SOQL"| crm

    classDef ext fill:#eef,stroke:#557,color:#113;
    classDef sys fill:#dfe,stroke:#484,color:#031;
    classDef edge fill:#fee,stroke:#a55,color:#300;
    class consumer,crm ext;
    class cuentas sys;
    class gw edge;
```

**Key point for architects:** consumers never see Salesforce semantics. The service exposes a small, stable contract (`CuentaResponse`) and absorbs every detail of the CRM — its auth flow, its field names, its error catalogue, and its failure modes.

The edge router is what makes that boundary addressable: consumers know one host and one path space
(`/api/cuentas/**`), never a service host or port. Note the asymmetry — the gateway handles
**north-south** traffic only. The call to the CRM leaves the platform directly, because Salesforce is
an external SaaS dependency, not a service behind our own router ([ADR-0013](adr/0013-traefik-edge-router.md)).

---

## 3. Container view (C4 L2)

```mermaid
flowchart TB
    consumer["Consumer"]

    subgraph edge["gateway (:80) — Traefik v3"]
        router["Routers<br/>PathPrefix(/api/cuentas) → cuentas<br/>PathPrefix(/mock) → sf-mock"]
        strip["StripPrefix middlewares<br/>/api · /mock"]
        disco["Container provider<br/>exposedByDefault: false"]
        router --> strip
        disco -.->|"reads traefik.* labels"| router
    end

    subgraph svc["cuentas-service (internal :8080) — Quarkus"]
        rest["REST inbound adapter<br/>CuentaResource"]
        app["Application<br/>ConsultarCuentaUseCase"]
        adapter["Salesforce outbound adapter<br/>SalesforceCrmAdapter<br/>@Retry · @Timeout · @CircuitBreaker"]
        token["SalesforceTokenService<br/>JWT mint + token cache"]
    end

    subgraph mock["sf-mock (internal :8081) — Quarkus"]
        auth["TokenResource<br/>/services/oauth2/token"]
        sobj["SObjectResource<br/>/services/data/v60.0/*"]
        admin["AdminResource<br/>/mock-admin/* (chaos)"]
        store["AccountStore<br/>in-memory · 315 accounts"]
    end

    consumer -->|"JSON<br/>GET /api/cuentas/{id}"| router
    strip -->|"GET /cuentas/{id}"| rest
    strip -->|"/mock-admin/* · /services/*"| admin
    rest --> app
    app -->|"ClienteCrmPort"| adapter
    adapter -->|"MicroProfile REST Client<br/>east-west · bypasses the gateway"| sobj
    token -->|"OAuth2 JWT Bearer"| auth
    adapter -.->|"getAccessToken()"| token
    sobj --> store
    admin -.->|"mutates fault mode"| sobj

    classDef ext fill:#eef,stroke:#557,color:#113;
    classDef edge fill:#fee,stroke:#a55,color:#300;
    class consumer ext;
    class router,strip,disco edge;
```

**Reading the edge.** The router matches on the *public* path and the middleware strips the prefix,
so `cuentas-service` receives `/cuentas/{id}` — exactly the path its JAX-RS annotations declare. The
service has no knowledge that it is mounted under `/api`, which is what lets that mapping change at
deploy time without a code change. Routes are not configured centrally: Traefik reads the `traefik.*`
labels off each container, so a route lives beside the service it routes to
([ADR-0013](adr/0013-traefik-edge-router.md)).

In production the `sf-mock` box is replaced by a real Salesforce org — **no code change** in `cuentas-service`, only the `SF_BASE_URL` / `SF_LOGIN_URL` configuration and real Connected App credentials. The `/mock/**` route disappears with it; a real deployment has no test double at the edge.

---

## 4. Component view — `cuentas-service` (hexagonal)

The service is organized in three concentric layers. Dependencies point **inward only**: infrastructure depends on the domain, never the reverse.

```mermaid
flowchart TB
    subgraph infra_in["Infrastructure · inbound"]
        res["CuentaResource<br/>@Path /cuentas"]
        dem["DomainExceptionMapper<br/>domain error → HTTP"]
    end

    subgraph app["Application"]
        uc["ConsultarCuentaUseCase<br/>consultar() · listar()"]
        prod["UseCaseProducer<br/>(CDI wiring)"]
    end

    subgraph domain["Domain (framework-free)"]
        cuenta["Cuenta (record)<br/>operativa()"]
        estado["EstadoCuenta enum"]
        port["«port» ClienteCrmPort"]
        exc["CuentaNoEncontradaException<br/>CrmNodisponibleException"]
    end

    subgraph infra_out["Infrastructure · outbound (Salesforce adapter)"]
        adapter["SalesforceCrmAdapter<br/>implements ClienteCrmPort"]
        client["«REST client» SalesforceClient"]
        authclient["«REST client» SalesforceAuthClient"]
        tokensvc["SalesforceTokenService"]
        filter["SalesforceAuthFilter<br/>injects Bearer header"]
        mapper["SalesforceExceptionMapper<br/>HTTP 4xx/5xx → SalesforceApiException"]
        health["SalesforceReadinessCheck"]
    end

    res --> uc
    uc -->|"depends on port"| port
    prod -. "produces" .-> uc
    adapter -. "implements" .-> port
    adapter --> client
    adapter --> tokensvc
    tokensvc --> authclient
    filter -. "provider on" .-> client
    filter --> tokensvc
    mapper -. "provider on" .-> client
    adapter --> cuenta
    cuenta --> estado
    res --> dem
    health --> tokensvc

    classDef dom fill:#dfe,stroke:#484,color:#031;
    class cuenta,estado,port,exc dom;
```

### Layer responsibilities

| Layer | Package | Rule | Contents |
| --- | --- | --- | --- |
| **Domain** | `domain.*` | No framework imports. Pure Java. | `Cuenta`, `EstadoCuenta`, `ClienteCrmPort`, domain exceptions |
| **Application** | `application.*` | Orchestrates use cases against ports. | `ConsultarCuentaUseCase` (holds the business cap `min(limite, 10_000)`) |
| **Infrastructure** | `infrastructure.*` | All the "how". Swappable. | REST endpoints, Salesforce adapter, REST clients, auth, health, exception mappers |

**Why this matters:** `ConsultarCuentaUseCase` is a plain constructor-injected class with **zero Quarkus/JAX-RS dependencies** — it can be unit-tested with a hand-written fake `ClienteCrmPort` in microseconds. The `UseCaseProducer` is the single seam where CDI wires the concrete adapter into the port.

---

## 5. Runtime behaviour (sequence diagrams)

### 5.1 Account lookup — happy path

Shows the token being minted lazily on first use, cached, and attached transparently by the client filter.

```mermaid
sequenceDiagram
    autonumber
    actor C as Consumer
    participant R as CuentaResource
    participant U as ConsultarCuentaUseCase
    participant A as SalesforceCrmAdapter
    participant F as SalesforceAuthFilter
    participant T as SalesforceTokenService
    participant SF as Salesforce / sf-mock

    C->>R: GET /cuentas/001AAA
    R->>U: consultar("001AAA")
    U->>A: buscarPorId("001AAA")
    A->>F: sf.getAccount(id)  [via REST client]
    F->>T: getAccessToken()
    alt no valid cached token
        T->>SF: POST /oauth2/token (JWT assertion)
        SF-->>T: access_token (+ instance_url)
        T-->>T: cache token (TTL 20 min)
    end
    T-->>F: access_token
    F->>SF: GET /sobjects/Account/001AAA<br/>Authorization: Bearer …
    SF-->>A: 200 · AccountDto (PascalCase JSON)
    A-->>A: aDominio(dto) → Cuenta (anti-corruption)
    A-->>U: Optional[Cuenta]
    U-->>R: Cuenta
    R-->>C: 200 · CuentaResponse (our contract)
```

### 5.2 Expired session — transparent 401 refresh

Salesforce sessions expire independently of the local TTL. The adapter recovers once, invisibly.

```mermaid
sequenceDiagram
    autonumber
    participant A as SalesforceCrmAdapter
    participant T as SalesforceTokenService
    participant SF as Salesforce / sf-mock

    A->>SF: GET /sobjects/Account/{id} (Bearer old-token)
    SF-->>A: 401 INVALID_SESSION_ID
    Note over A: conReintento401() catches 401
    A->>T: invalidate()
    A->>SF: retry GET (new token minted on demand)
    SF-->>A: 200 · AccountDto
```

### 5.3 CRM instability — retry, then circuit breaker

`5xx` and `429` are translated to `CrmNodisponibleException`, which is what `@Retry` and `@CircuitBreaker` act on. Business `4xx` (e.g. 404) is **not** retried.

```mermaid
sequenceDiagram
    autonumber
    participant A as SalesforceCrmAdapter<br/>@Retry(2) @Timeout(8s) @CircuitBreaker
    participant SF as Salesforce / sf-mock
    participant M as DomainExceptionMapper

    A->>SF: getAccount(id)
    SF-->>A: 500 → CrmNodisponibleException
    A->>SF: retry 1 (after 500ms ± jitter)
    SF-->>A: 500
    A->>SF: retry 2
    SF-->>A: 500
    Note over A: retries exhausted →<br/>breaker records failures
    A-->>M: CrmNodisponibleException
    M-->>M: map to HTTP
    Note over M: 503 + Retry-After: 15

    Note over A,SF: After 10 requests @ ≥50% failures,<br/>breaker OPENS for 15s →<br/>calls fail fast (no CRM hit)
```

### 5.4 Paginated listing (SOQL)

```mermaid
sequenceDiagram
    autonumber
    participant A as SalesforceCrmAdapter
    participant SF as Salesforce / sf-mock

    A->>SF: query("SELECT … FROM Account LIMIT n")
    SF-->>A: QueryResultDto (page 1 · done=false · nextRecordsUrl)
    loop while !done
        A->>SF: queryMore(soql, offset)
        SF-->>A: QueryResultDto (next 2000 · done=?)
    end
    A-->>A: flatten records → List<Cuenta>
```

---

## 6. Cross-cutting concerns

### 6.1 Resilience

| Mechanism | Scope | Configuration | Rationale |
| --- | --- | --- | --- |
| `@Retry` | all ops (2×; PATCH 1×) | 500 ms delay, 200 ms jitter, `retryOn = CrmNodisponibleException` | Absorb transient `5xx`/`429`/timeouts. **PATCH is idempotent** (same final state), so retrying is safe. A creating `POST` would *not* carry `@Retry` without an idempotency key — this is called out in the code. |
| `@Timeout` | all ops | 8 s | Bound the worst-case latency a consumer can experience. |
| `@CircuitBreaker` | account lookup | 10 requests, 50% failure ratio, 15 s open delay | Stop hammering a CRM that is already down; fail fast and shed load. |
| 401 refresh | all ops | invalidate token + 1 retry | Recover from server-side session expiry without surfacing an error. |

**Error taxonomy** — the adapter deliberately splits errors into two lanes:

```mermaid
flowchart LR
    e["Salesforce response"]
    e -->|"404"| biz["Business: not found<br/>→ Optional.empty() / 404"]
    e -->|"other 4xx"| biz2["Business: propagate as-is<br/>(no retry)"]
    e -->|"5xx / 429"| tech["Technical: CrmNodisponibleException<br/>→ retry → breaker → 503"]
    e -->|"connection / timeout"| tech
```

### 6.2 Security — OAuth 2.0 JWT Bearer

Server-to-server auth with **no user interaction and no stored passwords**:

```
SalesforceTokenService
  ├─ mints an RS256-signed JWT assertion (privateKey.pem, iss=clientId, sub=username, aud, exp=3min)
  ├─ POST /services/oauth2/token  (grant_type = urn:ietf:params:oauth:grant-type:jwt-bearer)
  ├─ caches access_token (local TTL 20 min, refreshed 60 s early)
  └─ refresh() is synchronized → no token stampede under concurrency
```

- The private key is the only secret; in production it belongs in a secrets manager / mounted volume, **not** in the image.
- REST-client request/response logging is enabled **only** in the `%dev` profile — never with production data.

### 6.3 Observability

| Endpoint | Purpose | Notes |
| --- | --- | --- |
| `/q/health/live` | Liveness (K8s) | Process up. |
| `/q/health/ready` | Readiness (K8s) | Backed by `SalesforceReadinessCheck` — verifies it can obtain a Salesforce token. If the CRM auth is down, the pod is pulled from rotation. |
| `/q/health` | Aggregate | — |

### 6.4 Anti-corruption layer

The domain never imports a Salesforce DTO. Mapping happens in one place (`SalesforceCrmAdapter.aDominio`), and unknown CRM values are defensively coerced:

```java
// Any unexpected Estado_Cliente__c value from the CRM → DESCONOCIDO, never a crash.
estado = EstadoCuenta.valueOf(dto.estadoCliente());   // guarded by try/catch → DESCONOCIDO
```

This keeps CRM-side configuration drift (new picklist values, renamed fields) from propagating as `500`s into the consumer.

### 6.5 Edge routing and exposure

A Traefik v3 container fronts the stack ([ADR-0013](adr/0013-traefik-edge-router.md)). Its role is
deliberately narrow — **routing and path rewriting, nothing else**:

| Concern | Where it lives | Why there |
| --- | --- | --- |
| Routing, path rewriting, single entry point | **Edge** | Deployment concerns. The public path is not the service's business. |
| Retry, timeout, circuit breaking | **Service** | The policy is *semantic*: it depends on which operations are idempotent and which CRM errors are technical rather than business — knowledge a routing rule cannot express. |
| Auth to the system of record | **Service** | OAuth 2.0 JWT Bearer is integration-specific, not perimeter-specific. |
| Consumer auth, rate limiting, TLS | **Neither, yet** | Traefik's job in a real deployment; out of scope here, since without real credentials or certificates it would be theatre. |

**Exposure is opt-in.** `exposedByDefault: false` means a container is invisible from the edge until
it carries a `traefik.enable=true` label. A service added to the stack cannot reach the perimeter by
being forgotten — the failure mode of a permissive default.

**Side effect worth knowing:** because the only routers match `/api/cuentas/**` and `/mock/**`, the
Quarkus management endpoints (`/q/health/*`, `/q/openapi`) are **no longer reachable from the host**.
That is the correct posture — they are for probes and internal tooling on the container network, not
for consumers — but it means health checks are now run against the container rather than
`localhost:8080`, and readiness/liveness probes attach to the pod directly in Kubernetes, never
through the ingress.

---

## 7. Architecture decisions

Every structural choice in this reference is recorded as an **ADR** in [`docs/adr/`](adr/README.md).
Each record states the forces in play, what was decided, what it costs, and which alternatives were
rejected and why. Records are immutable once accepted — a changed decision gets a new ADR that
supersedes the old one.

| # | Decision | Rationale | Trade-off accepted |
| --- | --- | --- | --- |
| [1](adr/0001-hexagonal-architecture.md) | **Hexagonal architecture** | Isolate a volatile external system; keep domain unit-testable and the CRM swappable. | More classes/indirection for a small service. |
| [2](adr/0002-own-consumer-contract-dto.md) | **Own DTO (`CuentaResponse`) distinct from `AccountDto`** | Consumer contract must not leak Salesforce field names or evolve with the CRM. | Extra mapping code. |
| [3](adr/0003-two-lane-error-taxonomy.md) | **Translate `5xx`/`429` → single `CrmNodisponibleException`** | One technical failure lane keeps fault-tolerance annotations and edge mapping simple. | Loses some granularity (all collapse to `503`). |
| [4](adr/0004-oauth2-jwt-bearer-flow.md) | **JWT Bearer flow (no interactive OAuth)** | Server-to-server integration; no user in the loop; no password storage. | Requires key management + Connected App setup. |
| [5](adr/0005-token-cache-and-401-refresh.md) | **Local token cache with early refresh + `synchronized`** | Avoid a token request per call and prevent stampedes. | Slight risk of using a token invalidated server-side → covered by the 401 retry. |
| [6](adr/0006-retry-on-idempotent-operations-only.md) | **`@Retry` on idempotent ops only** | Safe to repeat `GET`/`PATCH`; unsafe to repeat creating `POST` without an idempotency key. | Team must keep this discipline as new ops are added. |
| [7](adr/0007-protocol-faithful-mock.md) | **Protocol-faithful mock (`sf-mock`) over recorded stubs** | Same wire format + chaos modes → production code path runs unchanged in dev & CI. | A second app to maintain. |
| [8](adr/0008-in-memory-mock-state.md) | **In-memory state in the mock** | Zero infra for local/CI; fast boot. | Not durable — by design. |
| [9](adr/0009-single-503-at-the-edge.md) | **One `503 + Retry-After` for all three "CRM unavailable" signals** | Retries exhausted, breaker open, and timeout are one fact to a consumer: retry later. | Cause is visible only in logs, not in the response. |
| [10](adr/0010-constructor-injection.md) | **Constructor injection over field injection** | `final` collaborators, honest dependency lists, and `new`-able classes in unit tests. | More boilerplate than `@Inject` on a field. |
| [11](adr/0011-code-first-openapi-contract.md) | **Code-first OpenAPI, generated from the JAX-RS annotations** | The document cannot drift from the implementation; `404`/`503` become published contract, not README folklore. | The contract can now change by accident — no build gate on breaking changes. |
| [12](adr/0012-expose-openapi-in-production.md) | **`/q/openapi` in every profile; Swagger UI dev-only** | A deployed service should describe the code it is actually running; an interactive request console should not ship to prod. | The endpoint is unauthenticated — safety rests on the network perimeter, not on the app. |
| [13](adr/0013-traefik-edge-router.md) | **Traefik edge router; routes as container labels** | Make the perimeter ADR-0012 assumes into a running component; keep public paths out of the service code. | One more hop and failure mode; the demo dashboard runs unauthenticated. |

**How to read these:** the table is the summary; the linked records carry the reasoning. If you are
evaluating whether a pattern here transfers to your context, the *Alternatives considered* and
*Negative consequences* sections are the parts worth your time — they say when each choice stops
paying off.

---

## 8. Deployment view

```mermaid
flowchart LR
    host["Host<br/>localhost"]

    subgraph compose["docker compose / podman (local)"]
        g["gateway (Traefik v3)<br/>:80 · dashboard :8090"]
        c["cuentas-service<br/>internal :8080<br/>SF_BASE_URL=http://sf-mock:8081"]
        m["sf-mock<br/>internal :8081"]

        g -->|"/api/cuentas/** → strip /api"| c
        g -->|"/mock/** → strip /mock"| m
        c -->|"east-west (not routed)"| m
    end

    sock[("container socket<br/>read-only")]

    host -->|"the only published port"| g
    sock -.->|"service discovery via labels"| g

    classDef edge fill:#fee,stroke:#a55,color:#300;
    class g edge;
```

The published surface is one port. `cuentas-service` and `sf-mock` declare no host port mapping at
all, so the only way in is through the router — which is what makes the "internal service behind a
perimeter" assumption in [ADR-0012](adr/0012-expose-openapi-in-production.md) true in the demo and
not just in prose.

| Public path | Routed to | Service sees |
| --- | --- | --- |
| `/api/cuentas/**` | `cuentas-service:8080` | `/cuentas/**` |
| `/mock/**` | `sf-mock:8081` | `/**` (e.g. `/mock-admin/chaos/…`) |

> ⚠️ **Laboratory posture.** The Traefik dashboard is served with `insecure: true` on `:8090` — an
> unauthenticated admin UI — and the gateway mounts the container runtime socket to discover
> services. Both are acceptable on a developer machine and in no shared environment. In Kubernetes
> the equivalents are an Ingress/Gateway API resource and a narrowly-scoped RBAC role.

**Packaging options** (both modules): JVM jar, uber-jar, GraalVM native, and native-micro — Dockerfiles under each module's `src/main/docker/`. Native images boot in milliseconds, suited to scale-to-zero / serverless.

**Environment contract for `cuentas-service`:**

| Variable | Meaning | Local default |
| --- | --- | --- |
| `SF_BASE_URL` | Salesforce data API base | `http://localhost:8081` |
| `SF_LOGIN_URL` | Salesforce token endpoint base | `http://localhost:8081` |
| `app.salesforce.auth.*` | Connected App client id / username / audience | fake values for the mock |
| `smallrye.jwt.sign.key.location` | RS256 private key | `classpath:privateKey.pem` (replace in prod) |

**Kubernetes:** wire `/q/health/live` and `/q/health/ready` to liveness/readiness probes. Readiness gating on CRM auth means a pod only receives traffic once it can actually reach Salesforce.

---

## 9. CI/CD pipeline

Path-filtered, per-module matrix build (GitHub Actions, self-hosted runner):

```mermaid
flowchart LR
    push["push / PR → main"] --> changes{"paths-filter<br/>which module changed?"}
    changes --> build["build-test<br/>./mvnw -pl MODULE -am package"]
    build --> sonar["Sonar analysis<br/>(JaCoCo coverage)"]
    build --> docker["Podman image build<br/>(main pushes only)"]

    classDef gate fill:#eef,stroke:#557,color:#113;
    class changes gate;
```

- **`changes`** — `dorny/paths-filter` decides whether `sf-mock`, `cuentas-service`, or both need to run; a change to `pom.xml` triggers both.
- **`build-test`** — matrix over both modules; `-am` also builds needed reactor dependencies.
- **`sonar`** — quality gate fed by JaCoCo XML coverage; `sonar.projectKey` is set per module.
- **`docker`** — builds JVM images with Podman, tagged `latest` and the commit SHA; runs only on pushes to `main`.

---

## Where to go next

| You want to… | Read |
| --- | --- |
| Understand why a decision was made, and what it cost | [`docs/adr/`](adr/README.md) |
| Consume or operate the account API | [`cuentas-service/README.md`](../cuentas-service/README.md) |
| Understand / extend the Salesforce mock | [`sf-mock/README.md`](../sf-mock/README.md) |
| Build and run the whole stack | [root `README.md`](../README.md) |
