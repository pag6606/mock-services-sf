# cuentas-service · Banking Account API

A Quarkus microservice that provides a simplified REST API for querying and updating bank accounts, backed by Salesforce CRM behind a hexagonal (ports-and-adapters) architecture.

```
GET  /cuentas/{id}           Lookup account by CRM ID
GET  /cuentas?limite=10      Paginated account list
```

## Architecture

```
┌──────────────┐     ┌─────────────────────────────────────────────┐
│   Client     │     │              cuentas-service                 │
│  (HTTP)      │     │                                              │
└──────┬───────┘     │  ┌───────────────────────────────────────┐   │
       │             │  │         Inbound Adapter (REST)         │   │
       │  GET/POST   │  │  CuentaResource / DomainExceptionMapper│   │
       ├─────────────▶  └────────────────┬──────────────────────┘   │
       │             │                   │                          │
       │             │  ┌────────────────▼──────────────────────┐   │
       │             │  │       Application (Use Case)          │   │
       │             │  │      ConsultarCuentaUseCase           │   │
       │             │  └────────────────┬──────────────────────┘   │
       │             │                   │                          │
       │             │                   │  ClienteCrmPort          │
       │             │  ┌────────────────▼──────────────────────┐   │
       │             │  │       Outbound Adapter                 │   │
       │             │  │   SalesforceCrmAdapter                 │   │
       │             │  │   ┌────────────────────────────────┐   │   │
       │             │  │   │  SalesforceClient (REST Client) │   │   │
       │             │  │   │  SalesforceTokenService         │   │   │
       │             │  │   │  @Retry / @CircuitBreaker        │   │   │
       │             │  │   └────────────────────────────────┘   │   │
       │             │  └────────────────┬──────────────────────┘   │
       │             └───────────────────┼──────────────────────────┘
       │                                 │
       │                                 │  OAuth2 JWT Bearer
       │                                 │  API v60.0
       │         ┌───────────────────────▼───────────────────┐
       │         │              Salesforce                   │
       └─────────▶     sf-mock (dev) · real org (prod)       │
                 └───────────────────────────────────────────┘
```

In development, all Salesforce calls go to `sf-mock` (port 8081) — a full-featured mock that supports OAuth2, Account/Case CRUD, SOQL queries, and chaos injection for fault-tolerance testing.

## REST API

### Lookup account by ID

```bash
curl http://localhost:8080/cuentas/001AAA
```

```json
{
  "id": "001AAA",
  "nombre": "Juan Perez",
  "numero": "ACC-12345",
  "estado": "ACTIVO",
  "operativa": true
}
```

### List accounts

```bash
curl http://localhost:8080/cuentas?limite=5
```

### Error responses

| Status | Body | When |
|---|---|---|
| `404` | `{"error": "CUENTA_NO_ENCONTRADA", ...}` | Account not found in CRM |
| `503` | `{"error": "CRM_NO_DISPONIBLE", ...}` + `Retry-After: 15` | Salesforce down, rate-limited, or circuit-breaker open |
| `500` | `{"error": "ERROR_INTERNO"}` | Unexpected error |

## Salesforce integration

### Authentication

The service uses **OAuth2 JWT Bearer flow** — no user interaction required:

```
SalesforceTokenService
  ├── Builds RS256-signed JWT assertion
  │     (privateKey.pem, issuer=clientId, subject=username)
  ├── POST /services/oauth2/token (grant_type=jwt-bearer)
  └── Caches access_token (TTL: 20 min)
```

On `401`, the token is transparently refreshed and the request retried once.

### Query examples

All calls go through `SalesforceClient` (`@RegisterRestClient`):

```java
// By ID
AccountDto acc = sf.getAccount("001AAA");     // GET /sobjects/Account/{id}

// SOQL (paginated every 2000)
QueryResultDto res = sf.query("SELECT Id, Name FROM Account LIMIT 10");

// Update status
sf.updateAccount("001AAA", Map.of("Estado_Cliente__c", "SUSPENDIDO"));
```

### Fault tolerance

| Mechanism | Scope | Config |
|---|---|---|
| `@Retry` | All operations | Max 2 retries, 500ms delay |
| `@Timeout` | All operations | 8 seconds |
| `@CircuitBreaker` | Account lookup | 10 requests, 50% failure ratio, 15s delay |
| 401 retry | All operations | Invalidate token + 1 retry |

## Chaos testing (with sf-mock)

`sf-mock` provides a mock Salesforce with **315 pre-loaded accounts** and chaos modes:

```bash
# Simulate Salesforce outage
curl -X POST http://localhost:8081/mock-admin/chaos/ERROR_500
curl http://localhost:8080/cuentas/001AAA  # → 503 (triggers retry + circuit breaker)

# Simulate timeout
curl -X POST http://localhost:8081/mock-admin/chaos/TIMEOUT

# Simulate expired session
curl -X POST http://localhost:8081/mock-admin/revocar-tokens
curl http://localhost:8080/cuentas/001AAA  # → auto-refresh token → success
```

## Run

### Dev mode (hot reload, points to sf-mock on :8081)

```bash
# Terminal 1: start mock
cd ../sf-mock && ./mvnw quarkus:dev

# Terminal 2: start service
./mvnw quarkus:dev   # http://localhost:8080
```

### JAR

```bash
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

### Native executable

```bash
./mvnw package -Dnative
./target/cuentas-service-1.0.0-SNAPSHOT-runner
```

### Podman / Docker

```bash
./mvnw package
podman build -f src/main/docker/Dockerfile.jvm -t cuentas-service .
podman run -d --rm -p 8080:8080 -e SF_BASE_URL=http://host.containers.internal:8081 -e SF_LOGIN_URL=http://host.containers.internal:8081 --name cuentas-service cuentas-service
```

Or via docker-compose from the root:

```bash
docker compose up --build
```

## Health

| Endpoint | Purpose |
|---|---|
| `/q/health` | Overall status |
| `/q/health/live` | Liveness probe (Kubernetes) |
| `/q/health/ready` | Readiness probe (Kubernetes) |

Custom `SalesforceReadinessCheck` validates connectivity to Salesforce auth.

## Stack

- **Java 21** + **Quarkus 3.37.3**
- Hexagonal architecture (domain → application → infrastructure)
- REST endpoints via `quarkus-rest-jackson`
- Salesforce client via MicroProfile REST Client
- Fault tolerance via SmallRye (`@Retry`, `@Timeout`, `@CircuitBreaker`)
- JWT assertion via SmallRye JWT Build
