# CLAUDE.md

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
