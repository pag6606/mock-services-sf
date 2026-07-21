# Mock Services for Salesforce

Monorepo for a Quarkus-based banking integration with Salesforce, including a fully mocked Salesforce REST API for local development.

## Structure

```
├── sf-mock/             Salesforce REST API v60.0 mock server
│   ├── OAuth2 JWT Bearer token endpoint
│   ├── Account / Case sObject CRUD
│   ├── SOQL queries with pagination
│   └── Chaos testing (500, timeout, rate-limit)
│
├── cuentas-service/     Banking account service (hexagonal)
│   ├── REST API for account lookup
│   ├── Salesforce client with fault tolerance
│   └── JWT-based authentication
│
├── docker-compose.yml   Run both services together
└── pom.xml              Maven parent POM
```

## Build

```bash
./mvnw clean package
```

## Run (dev mode)

```bash
./mvnw quarkus:dev -pl sf-mock
./mvnw quarkus:dev -pl cuentas-service
```

## Run (Docker / Podman)

```bash
docker compose up --build
```

## Modules

| Module | Port | Description |
|---|---|---|
| `sf-mock` | 8081 | Mock Salesforce REST API |
| `cuentas-service` | 8080 | Banking account service |

## Stack

- **Java 21** + **Quarkus 3.37.3**
- REST clients via MicroProfile
- Fault tolerance with SmallRye
- Hexagonal architecture (cuentas-service)
