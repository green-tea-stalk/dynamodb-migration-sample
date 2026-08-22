# AGENTS.md

This document serves as the system guideline, architectural design principles, and migration gotchas for AI coding agents and developers working in this repository.

---

## 1. Project Overview

- **Goal**: A reference implementation for a safe, incremental migration from AWS SDK for Java v1 (`DynamoDBMapper`) to AWS SDK for Java v2 (`DynamoDbEnhancedClient`).
- **Key Features**:
  - Full support for CRUD, optimistic locking, batch operations (BatchWrite/BatchGet), transactions (TransactWriteItems), queries (PK/SK, GSI), scans, and pagination.
  - Verification of 100% behavioral equivalence between v1 and v2 via a shared contract test (`OrderRepositoryContractTest`).

---

## 2. Architecture Principles for Safe Migration

To safely migrate live, production systems, strictly adhere to these three core architectural principles:

```
                  ┌──────────────────────────────┐
                  │    Domain Model (Order)      │  ← Pure POJO / Record (SDK-Independent)
                  └──────────────▲───────────────┘
                                 │
                  ┌──────────────┴───────────────┐
                  │ <<Interface>> OrderRepository│  ← Unified Repository Interface
                  └──────────────▲───────────────┘
                                 │
              ┌───────────────────┴───────────────────┐
              │                                       │
┌────────────────────────────┐       ┌────────────────────────────┐
│ V1DynamoDbOrderRepository  │       │ V2DynamoDbEnhancedOrder    │
│  (AWS SDK v1 DynamoDBMapper│       │  Repository                │
│   + OrderItemV1 DTO)       │       │  (AWS SDK v2 Enhanced      │
│                            │       │   + OrderItemV2 DTO)       │
└────────────────────────────┘       └────────────────────────────┘
```

1. **Domain Independence**:
   - Classes under `com.example.dynamodb.domain` (`Order`, `OrderKey`, `OrderLineItem`, `OrderStatus`) must NEVER contain AWS SDK annotations or types.
2. **Repository Encapsulation**:
   - The business layer interacts only through the `OrderRepository` interface. Never expose SDK-specific types (`DynamoDBMapper`, `DynamoDbEnhancedClient`, `AttributeValue`, etc.).
3. **Contract Testing for Parity**:
   - Whenever adding or modifying operations, add corresponding test cases to `OrderRepositoryContractTest` so both v1 (`V1OrderRepositoryTest`) and v2 (`V2OrderRepositoryTest`) run identical assertions (12 scenarios, 24 total test cases).

---

## 3. Prerequisites & Technology Stack

| Item | Version / Requirement | Notes |
| :--- | :--- | :--- |
| **Java (JDK)** | **Java 25** | Managed via Gradle Toolchain |
| **Gradle** | **9.1.0** | Use bundled Gradle Wrapper (`./gradlew`) |
| **Dependency Management** | **Gradle Version Catalog** | Centralized in `gradle/libs.versions.toml` (`libs.xxx`) |
| **Code Formatter** | **Spotless 7.0.2** | 4-space indentation, Eclipse JDT formatter |
| **Boilerplate Reduction** | **Lombok 1.18.46** | Leverage `@Data`, `@Builder`, `@AllArgsConstructor` |
| **Testing** | **JUnit 5, AssertJ, Testcontainers** | Auto-starts `amazon/dynamodb-local:latest` |

---

## 4. Coding Conventions & Best Practices

### 4.1. Javadoc Conventions
- Write precise Javadoc for all `public` classes, interfaces, methods, constants, and constructors.
- Provide Javadoc for `protected` methods/fields intended for subclass overriding or extension.
- Always include `@param`, `@return`, and `@throws` with clear explanations.
- Ensure `./gradlew javadoc` produces **0 warnings**.

### 4.2. Lombok Conventions
- Use `@Data`, `@Builder`, and `@AllArgsConstructor` on domain models and nested DTOs.
- To avoid missing Javadoc warnings on default constructors, define an **explicit default constructor with Javadoc** rather than relying solely on `@NoArgsConstructor`.
- Do NOT use `@Value` on DTO classes because DynamoDB ORM JavaBean conventions require a no-arg constructor and mutable setters.

### 4.3. DynamoDB DTO Conventions
- **v1 (`OrderItemV1`, `OrderLineItemV1`)**:
  - Class annotated with `@DynamoDBTable(tableName = "...")` / `@DynamoDBDocument`.
  - Accessors annotated with `@DynamoDBHashKey`, `@DynamoDBRangeKey`, `@DynamoDBVersionAttribute`, etc.
- **v2 (`OrderItemV2`, `OrderLineItemV2`)**:
  - Class annotated with `@DynamoDbBean`.
  - Getter methods annotated with `@DynamoDbPartitionKey`, `@DynamoDbSortKey`, `@DynamoDbVersionAttribute`, etc. for `TableSchema.fromBean` scanning.

---

## 5. Development & Verification Commands

After making any changes, run the following verification pipeline:

```bash
# 1. Code format check and apply (Spotless)
./gradlew spotlessCheck   # Check format
./gradlew spotlessApply   # Auto-apply format

# 2. Compile source and test code
./gradlew compileJava compileTestJava

# 3. Generate and verify Javadoc (0 warnings required)
./gradlew javadoc

# 4. Full build check (excluding tests)
./gradlew check -x test

# 5. Run all contract tests (Requires Docker daemon)
./gradlew test
```

---

## 6. DynamoDB Migration Gotchas & Best Practices

1. **`Instant` (Date/Time) Serialization**:
   - v1 (`DynamoDBMapper`): Cannot serialize `Instant` by default; requires a custom `DynamoDBTypeConverter<String, Instant>`.
   - v2 (`DynamoDbEnhancedClient`): Automatically serialized to ISO-8601 strings (e.g., `2026-08-22T01:45:00Z`).
2. **Version Attribute Value After Optimistic Lock**:
   - v1: `mapper.save(item)` mutates the passed Java object in-place with the incremented version.
   - v2: `table.putItem(item)` does NOT mutate the passed Java object. Fetch the latest version via `getItem` or track manual increments.
3. **Class Name Collisions**:
   - Classes like `AttributeValue` exist in both v1 (`com.amazonaws.services.dynamodbv2.model...`) and v2 (`software.amazon.awssdk.services.dynamodb.model...`). Always physically isolate them in `repository.v1` and `repository.v2` packages.
4. **HTTP Client Selection**:
   - SDK v2 supports `url-connection-client` (lightweight), `apache-client` (high throughput), and `netty-nio-client` (async). This project adopts the lightweight `url-connection-client`.
