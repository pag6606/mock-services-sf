# sf-mock · Salesforce REST API Mock

A lightweight Quarkus mock server that simulates the Salesforce REST API v60.0 — including OAuth2 JWT Bearer flow, Account/Case sObjects, and SOQL queries — so you can develop and test integrations without a real Salesforce org.

```
cuentas-service (port 8080)
        |
        |  OAuth2 JWT Bearer · API v60.0
        |  MicroProfile REST Client
        |
        v
sf-mock (port 8081)   ←  mocks Salesforce
```

## Real use

**sf-mock** exists because `cuentas-service` needs a Salesforce backend to talk to during development. Every request the real service makes — token acquisition, account queries, SOQL pagination, case creation, account updates — is handled by this mock.

It ships with **315 pre-loaded accounts** (`accounts.json`) and supports identity-aware responses via the `Estado_Cliente__c` custom field (values: `ACTIVO`, `SUSPENDIDO`, `EN_REVISION`, `MOROSO`, `vip`).

```java
// What cuentas-service calls — all served by sf-mock
salesforceClient.getAccount("001AAA");        // GET  /services/data/v60.0/sobjects/Account/{id}
salesforceClient.query("SELECT+Id+FROM+Account+LIMIT+10"); // GET  /services/data/v60.0/query
salesforceClient.createCase(caseDto);          // POST /services/data/v60.0/sobjects/Case
salesforceClient.updateAccount(id, updates);   // PATCH /services/data/v60.0/sobjects/Account/{id}
```

The token response, error payloads (`401`, `404`, `429`, `500`), and the `QueryResult` pagination format all match real Salesforce wire protocols — so the production code path runs unchanged against the mock.

## Chaos testing

Four fault modes simulate real Salesforce instability without touching a real org:

| Mode | Behaviour | Use case |
|---|---|---|
| `OK` | Normal operation | Baseline |
| `ERROR_500` | All API calls return HTTP 500 | CircuitBreaker / retry logic |
| `TIMEOUT` | 30-second sleep before responding | ProcessingException handling |
| `RATE_LIMIT` | HTTP 429 with retry-after | Rate-limit backoff |

```bash
# Activate fault mode
curl -X POST http://localhost:8081/mock-admin/chaos/ERROR_500

# Revoke all issued tokens (subsequent calls get 401)
curl -X POST http://localhost:8081/mock-admin/revocar-tokens
```

These let `cuentas-service` exercise its token refresh, `@Retry`, `@CircuitBreaker`, and health-check logic in a controlled environment.

## API reference

### Authentication

```
POST /services/oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=<JWT>
```

Returns a mock `access_token` and `instance_url` pointing to `http://localhost:8081`.

### Salesforce REST API (v60.0)

| Method | Path | Description |
|---|---|---|
| `GET` | `/services/data/v60.0/sobjects/Account/{id}` | Fetch account by ID |
| `POST` | `/services/data/v60.0/sobjects/Case` | Create a case (always 201) |
| `PATCH` | `/services/data/v60.0/sobjects/Account/{id}` | Update `Estado_Cliente__c` |
| `GET` | `/services/data/v60.0/query?q=...` | SOQL query (reads `LIMIT`, paginated every 2000) |
| `GET` | `/services/data/v60.0/query/more?q=...&offset=...` | Next page of results |

All `/services/data/...` endpoints require `Authorization: Bearer <token>`. Tokens are validated against the in-memory store.

### Admin

| Method | Path | Description |
|---|---|---|
| `POST` | `/mock-admin/chaos/{modo}` | Set fault mode |
| `POST` | `/mock-admin/revocar-tokens` | Invalidate all tokens |

## Run

### Dev mode (hot reload)

```bash
./mvnw quarkus:dev
```

### JAR

```bash
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

### Uber-JAR

```bash
./mvnw package -Dquarkus.package.jar.type=uber-jar
java -jar target/*-runner.jar
```

### Native executable

```bash
./mvnw package -Dnative
./target/sf-mock-1.0.0-SNAPSHOT-runner
```

## Podman / Docker

All Dockerfiles are in `src/main/docker/` and work with both `docker` and `podman`.

### JVM container

```bash
./mvnw package
podman build -f src/main/docker/Dockerfile.jvm -t sf-mock .
podman run -d --rm -p 8081:8081 --name sf-mock sf-mock
```

### Native container (smaller, faster boot)

```bash
./mvnw package -Dnative
podman build -f src/main/docker/Dockerfile.native -t sf-mock-native .
podman run -d --rm -p 8081:8081 --name sf-mock sf-mock-native
```

Both images use Red Hat UBI base images. The native variant boots in milliseconds.

## Stack

- **Java 21** + **Quarkus 3.37.3**
- REST endpoints via `quarkus-rest-jackson`
- In-memory state (`ConcurrentHashMap`), no database
- 4 container images: JVM, legacy-jar, native, native-micro
