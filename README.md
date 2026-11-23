# OrchestrAI — Java/Spring Boot Backend Engineering Sample

**Developer:** Mehmet Arslanca  
**Original project:** OrchestrAI, an independently developed AI architecture and orchestration platform.  
**Stack:** Java 21, Spring Boot 4, Spring AI, PostgreSQL, Flyway, Redis, OAuth/JWT, SSE, JPA  
**Purpose:** Technical code review / software engineering portfolio.

This repository is a **curated, source-only snapshot of real backend code** from OrchestrAI. It has a fresh public history. The full product repository, web frontend, personal configurations, development history and deployment credentials remain private. It is **not** a hosted production service or a turnkey deployed application.

## The engineering problem

Complex AI-assisted software generation needs more than a single free-form model request. OrchestrAI separates product intent and security analysis from heavier architecture/blueprint generation. It maintains explicit application state, exposes progress over streaming interfaces and puts authentication, rate limits, provider credentials and usage accounting under backend control.

## What to inspect

| Area | Implementation | Why it matters |
|---|---|---|
| AI orchestration and planning | `backend/src/main/java/com/arslanca/orchestrai/business/` | Intent analysis, blueprint planning, provider resolution, execution boundaries and cancellation/recovery |
| REST and streaming | `backend/src/main/java/com/arslanca/orchestrai/api/` | Public HTTP entry points, adapter boundaries, SSE progress and API contracts |
| Auth and credentials | `backend/src/main/java/com/arslanca/orchestrai/core/security/` | JWT, encryption and credential protection; integrations and auth orchestration are separated from DTOs |
| Persistence | `backend/src/main/java/com/arslanca/orchestrai/dataaccess/`, `entity/` and `backend/src/main/resources/db/migration/` | Repository boundaries, relational schema evolution and workflow state |
| Runtime quality | `backend/src/test/java/` | Unit, mocked integration and HTTP/controller tests for actual engineering behaviors |
| Distributed support | `backend/src/main/java/com/arslanca/orchestrai/core/idempotency/` and related services | Redis-backed coordination, rate limits and idempotent request handling |

Some systems are still evolving. The source illustrates implemented architecture and test coverage, **not** an externally certified reliability guarantee.

## Technical design highlights

- **Dual-stage orchestration:** A lighter analysis/routing stage precedes heavyweight blueprint generation; model providers are abstracted from product logic.
- **Modular monolith:** Domain and service boundaries remain explicit while a single backend carries workflow state and validation responsibilities.
- **Protected user accounts:** GitHub OAuth/JWT access and encrypted provider credentials (bring-your-own-key).
- **Long-running operations:** SSE progress, cancellation/failure handling, work-state persistence, Redis coordination and idempotency.
- **Data evolution:** PostgreSQL plus Flyway migrations for auth, projects, chat, generated artifacts, model/provider preferences and usage ledgers.

## Compile verification

Tested on Java 21 with the Maven wrapper:

```sh
cd backend
./mvnw -DskipTests compile
```

**Result:** Successful compilation of the included backend source. This sample deliberately omits deployment and runtime configuration, so it is not intended to start without separately supplied, environment-specific settings, services and credentials. No third-party keys or live account data are included.

## Scope and security

Only the selected source, Maven build definition, schema migrations, scripts and relevant tests were exported from the private default branch. Environment files, app property files, runtime databases, personal workspace folders, operational documents and all private Git history were **excluded**. The original repository remains private. This snapshot is for evaluation of engineering work; no general reuse license is granted by the publication of this source sample.
