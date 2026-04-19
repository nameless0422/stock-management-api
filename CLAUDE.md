# CLAUDE.md

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

Tradeoff: These guidelines bias toward caution over speed. For trivial tasks, use judgment.

1. Think Before Coding
   Don't assume. Don't hide confusion. Surface tradeoffs.

Before implementing:

State your assumptions explicitly. If uncertain, ask.
If multiple interpretations exist, present them - don't pick silently.
If a simpler approach exists, say so. Push back when warranted.
If something is unclear, stop. Name what's confusing. Ask.
2. Simplicity First
   Minimum code that solves the problem. Nothing speculative.

No features beyond what was asked.
No abstractions for single-use code.
No "flexibility" or "configurability" that wasn't requested.
No error handling for impossible scenarios.
If you write 200 lines and it could be 50, rewrite it.
Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

3. Surgical Changes
   Touch only what you must. Clean up only your own mess.

When editing existing code:

Don't "improve" adjacent code, comments, or formatting.
Don't refactor things that aren't broken.
Match existing style, even if you'd do it differently.
If you notice unrelated dead code, mention it - don't delete it.
When your changes create orphans:

Remove imports/variables/functions that YOUR changes made unused.
Don't remove pre-existing dead code unless asked.
The test: Every changed line should trace directly to the user's request.

4. Goal-Driven Execution
   Define success criteria. Loop until verified.

Transform tasks into verifiable goals:

"Add validation" → "Write tests for invalid inputs, then make them pass"
"Fix the bug" → "Write a test that reproduces it, then make it pass"
"Refactor X" → "Ensure tests pass before and after"
For multi-step tasks, state a brief plan:

1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
   Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---



This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A shopping mall inventory management API built with Spring Boot 3.5.11 + Java 17. The system manages the full lifecycle: **inventory → order → payment → user**. Currently in early development (only the Spring Boot skeleton exists); the README documents the full planned architecture.

## Build & Run Commands

```bash
# Run application (requires MySQL and Redis running)
./gradlew bootRun

# Build JAR
./gradlew clean build

# Run all tests
./gradlew test

# Run single test class
./gradlew test --tests "com.stockmanagement.inventory.InventoryServiceTest"

# Run single test method
./gradlew test --tests "com.stockmanagement.inventory.InventoryServiceTest.methodName"

# Generate test coverage report
./gradlew jacocoTestReport
# Report at: build/reports/jacoco/test/html/index.html
```

## Local Infrastructure (Docker)

```bash
# MySQL
docker run -d --name stock-mysql \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=stock_management \
  -p 3306:3306 mysql:8

# Redis
docker run -d --name stock-redis -p 6379:6379 redis:7-alpine

# Or run the full stack via Docker Compose (once docker/ directory is created)
docker-compose up -d
```

## Architecture

### Package Structure

All code lives under `com.stockmanagement` with a domain-driven layout:

```
com.stockmanagement/
├── domain/
│   ├── inventory/     # entity, repository, service, controller, dto
│   ├── order/         # entity, repository, service, controller, dto
│   ├── payment/       # entity, repository, service, controller, dto
│   └── user/          # entity, repository, service, controller, dto
├── common/
│   ├── config/        # JpaConfig, RedisConfig, SecurityConfig
│   ├── exception/     # GlobalExceptionHandler, BusinessException subclasses
│   └── dto/           # ApiResponse (unified response wrapper)
└── security/          # JwtTokenProvider, JwtAuthenticationFilter
```

### Core Inventory Model

The `Inventory` entity tracks three quantity states:
- `onHand` — physical stock in warehouse
- `reserved` — held for pending orders (not yet paid)
- `allocated` — confirmed after successful payment
- `available` = `onHand - reserved - allocated` (computed)

State transitions:
- Order created → `reserved += quantity`
- Payment success → `reserved -= quantity`, `allocated += quantity`
- Payment failed / order cancelled → `reserved -= quantity`

### Concurrency Control Strategy

All inventory mutations must use one of:
- **Pessimistic lock**: `@Lock(LockModeType.PESSIMISTIC_WRITE)` on repository query for high-contention writes
- **Optimistic lock**: `@Version` field on `Inventory` entity for lower-contention cases
- **Distributed lock**: Redisson (planned for stage 5) for multi-instance deployments

### API Response Wrapper

All endpoints return `ApiResponse<T>` from `common/dto/`:
```json
{ "success": true, "data": { ... } }
```

### Database Migrations

Flyway manages schema evolution under `src/main/resources/db/migration/`:
- `V1__init_schema.sql` — products, warehouses, inventory, inventory_transactions
- `V2__create_order_tables.sql` — users, orders, order_items
- `V3__create_payment_tables.sql` — payments

### Security

- Spring Security 6 with stateless JWT (jjwt library)
- Public endpoints: `/api/auth/**`, `/actuator/**`, `/api/payments/webhook`
- ADMIN-only: product create/update/delete, inventory receive/adjust, admin order views
- JWT secret via environment variable `JWT_SECRET`

### Service Layer Conventions

- Services are `@Transactional(readOnly = true)` by default
- Write operations override with `@Transactional`
- `InsufficientStockException` extends `BusinessException` and is handled globally by `GlobalExceptionHandler`
- Idempotency key on orders prevents duplicate order creation

## Key Dependencies

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-data-jpa` | JPA / Hibernate ORM |
| `spring-boot-starter-validation` | Bean Validation (`@Valid`) |
| `spring-boot-starter-web` | REST controllers |
| `mysql-connector-j` | MySQL 8 driver |
| `lombok` | Boilerplate reduction |
| Flyway (planned) | DB schema migrations |
| Spring Security + jjwt (planned) | JWT auth |
| Spring Data Redis + Redisson (planned) | Cache, TTL reservations, distributed locks |
| Testcontainers (planned) | Integration tests with real MySQL/Redis |
