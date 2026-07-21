# Mock Services for Salesforce

![Build](https://github.com/pag6606/mock-services-sf/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Quarkus](https://img.shields.io/badge/Quarkus-3.37.3-blue?logo=quarkus)
![Podman](https://img.shields.io/badge/Podman-container-892CA0?logo=podman)

Monorepo for a Quarkus-based banking integration with Salesforce, including a fully mocked Salesforce REST API for local development.

```
                    ┌─────────────────────────────────┐
                    │      cuentas-service (:8080)     │
                    │  Banking account API             │
                    │  Hexagonal · @Retry · @CB        │
                    └──────────────┬──────────────────┘
                                   │ OAuth2 JWT · API v60.0
                                   │ MicroProfile REST Client
                    ┌──────────────▼──────────────────┐
                    │        sf-mock (:8081)           │
                    │  Salesforce REST API mock        │
                    │  Accounts · SOQL · Chaos         │
                    └─────────────────────────────────┘
```

## Modules

| Module                                         | Port    | What it does                                                                                                  |
| ---------------------------------------------- | ------- | ------------------------------------------------------------------------------------------------------------- |
| [`sf-mock`](sf-mock/README.md)                 | `:8081` | Simulates Salesforce REST API v60.0 — OAuth2 JWT Bearer, Account/Case sObjects, SOQL queries, chaos injection |
| [`cuentas-service`](cuentas-service/README.md) | `:8080` | Banking account lookup service — hexagonal architecture, fault-tolerant Salesforce client                     |

## Build

```bash
./mvnw clean package
```

## Run

### Dev mode (two terminals)

```bash
./mvnw quarkus:dev -pl sf-mock            # start mock first
./mvnw quarkus:dev -pl cuentas-service     # start service
```

### Podman / Docker

```bash
docker compose up --build
```

Or individually:

```bash
# sf-mock
./mvnw package -pl sf-mock
podman build -f sf-mock/src/main/docker/Dockerfile.jvm -t sf-mock sf-mock
podman run -d --rm -p 8081:8081 --name sf-mock sf-mock

# cuentas-service
./mvnw package -pl cuentas-service
podman build -f cuentas-service/src/main/docker/Dockerfile.jvm -t cuentas-service cuentas-service
podman run -d --rm -p 8080:8080 -e SF_BASE_URL=http://host.containers.internal:8081 --name cuentas-service cuentas-service
```

## Stack

- **Java 21** + **Quarkus 3.37.3**
- MicroProfile REST Client · SmallRye Fault Tolerance
- Hexagonal architecture (cuentas-service)
