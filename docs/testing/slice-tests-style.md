# Slice Tests Style Guide

## Purpose

This guide defines the project conventions for Spring Slice Tests in this URL shortener backend.
Use it for tests that need a limited Spring ApplicationContext for one framework slice: Web MVC,
Data JPA, or Redis.

Unit tests follow `docs/testing/unit-tests-style.md`. Slice tests follow this file. Full
end-to-end scenarios belong in integration tests based on `AbstractIntegrationTest`.

## Difference Between Unit Tests and Slice Tests

Unit tests are pure Java tests. They must not load Spring, use `MockMvc`, connect to databases,
start Redis, or use Testcontainers. They use Mockito with `@ExtendWith(MockitoExtension.class)`.

Slice tests may load a limited Spring ApplicationContext for a specific layer. They validate
framework integration such as MVC validation and JSON serialization, JPA mappings and queries, or
Redis serialization and TTL behavior. Slice tests must never use `@SpringBootTest` and must never
load the full application context.

## General Rules

- Create tests under `src/test/java` using the same package as the class under test.
- Use the correct base class:
  - controllers: `extends BaseWebSliceTest`
  - JPA repositories: `extends BaseDataJpaSliceTest`
  - Redis/cache components: `extends BaseRedisSliceTest`
- Inspect the base class before writing a test.
- Do not duplicate annotations, imports, containers, properties, or `@DynamicPropertySource`
  methods already provided by the base class.
- Do not create new Testcontainer configurations.
- Do not use `@SpringBootTest`.
- Do not load the full Spring context.
- Use AssertJ for assertions.
- Use AAA comments: `// 1. Arrange`, `// 2. Act`, `// 3. Assert`.
- Use `@DisplayName` in Brazilian Portuguese.
- Use test method names in English.
- Use package-private test classes and methods.
- Use `@Nested` classes grouped by endpoint, query, or behavior.
- Use parameterized tests for repeated validation scenarios.
- Do not modify production code just to make a slice test pass. If a slice test reveals a
  production bug, report it and propose the minimal fix.

## Base Test Classes

The slice base classes are in
`src/test/java/com/app/url_shortener/integrationTest/config`.

- `BaseWebSliceTest` provides `SpringExtension`, autowired `MockMvc`, autowired
  `tools.jackson.databind.ObjectMapper`, and a `@MockitoBean JwtDecoder`.
- `BaseDataJpaSliceTest` provides `@DataJpaTest`,
  `@AutoConfigureTestDatabase(replace = NONE)`, and PostgreSQL dynamic datasource properties.
- `BaseRedisSliceTest` provides `@DataRedisTest`, Redis dynamic connection properties, and a
  centralized `FLUSHDB` reset before every test.

Container support is centralized:

- `PostgresContainerSupport` starts `postgres:16-alpine`, enables Flyway, uses Hibernate
  `ddl-auto=validate`, and sets JDBC time zone to UTC.
- `RedisContainerSupport` starts `redis:7-alpine`.
- `LocalStackContainerSupport` is currently used by full integration tests for DynamoDB, not by a
  dedicated DynamoDB slice base class.

Do not copy these containers or dynamic properties into concrete tests. Reuse the base classes so
Spring context caching and container lifecycle stay consistent.

## Web MVC Slice Tests

Use Web MVC slice tests for controllers in `iam.presentation` and `url.presentation`.

- Extend `BaseWebSliceTest`.
- Add `@WebMvcTest(TargetController.class)` on the concrete test class unless a future base class
  provides an equivalent setup.
- Use `@Tag("web-slice")`.
- Mock dependencies outside the MVC slice using `@MockitoBean`.
- Mock use cases/services. Do not test business logic in controller slice tests.
- Use `mockMvc.perform(...)`.
- Use the autowired `objectMapper` for JSON bodies.
- Test HTTP status, request validation, JSON request/response serialization, headers and cookies
  when relevant, error response format, and security behavior when relevant.
- Keep controllers thin: the slice test should verify mapping, validation, status, cookies,
  headers, and delegation to mocked use cases.
- Ensure `GlobalExceptionHandler` and `ProblemDetailFactory` are part of the MVC slice when testing
  expected error responses. Import only the missing MVC advice/configuration needed by the slice.

## Data JPA Slice Tests

Use Data JPA slice tests for Spring Data repositories and persistence behavior under
`iam.infrastructure.persistence`.

- Extend `BaseDataJpaSliceTest`.
- Do not add `@DataJpaTest`; the base class already declares it.
- Use `@Tag("jpa-slice")`.
- Do not mock the database.
- Use the PostgreSQL Testcontainer already provided by the base class.
- Prefer PostgreSQL Testcontainers over H2 for Flyway migrations, JPA mappings, constraints,
  indexes, SQL-specific behavior, and transaction behavior.
- Use `TestEntityManager` or repositories to arrange data according to the test need.
- Test custom queries, derived queries with business relevance, joins, constraints, and mappings.
- Do not test basic CRUD methods unless there is project-specific behavior.
- Flush the persistence context when validating database constraints.
- Keep each test independent.

## Redis Slice Tests

Use Redis slice tests for Redis adapters, cache components, idempotency storage, counters, and TTL
behavior.

- Extend `BaseRedisSliceTest`.
- Do not add `@DataRedisTest`; the base class already declares it.
- Use `@Tag("redis-slice")`.
- Do not mock Redis.
- Use the Redis Testcontainer already provided by the base class.
- Test serialization/deserialization, TTL, key format, save/read/delete behavior, custom Redis
  queries, and idempotency/cache behavior when implemented by Redis.
- Rely on the `BaseRedisSliceTest` reset for isolation. Do not add broad key deletion, `FLUSHDB`, or
  `FLUSHALL` cleanup to concrete tests.
- Prefer exercising the real Redis-facing component with Spring-managed `StringRedisTemplate` or
  `RedisTemplate` support instead of mocking Redis operations.

## Spring Context Caching

- Preserve stable slice configuration across tests.
- Avoid unnecessary `@TestPropertySource`, `@Import`, `@ActiveProfiles`, `@DynamicPropertySource`,
  or bean overrides.
- Use consistent `@MockitoBean` field names for the same mocked bean across tests.
- Do not use `@DirtiesContext` unless truly necessary.
- Prefer cleaning database or Redis state over dirtying the Spring context.

## Security in Web Slice Tests

Spring Security is part of the Web MVC behavior for protected endpoints.

- Do not disable security filters unless that is already the explicit convention for the target
  slice.
- Use Spring Security test support for authenticated requests, usually `.with(jwt())`.
- Configure JWT claims and authorities explicitly when authorization depends on roles, permissions,
  user id, plan, or custom claims.
- This project preserves authorities from the JWT `authorities` claim with no authority prefix;
  match that behavior in tests.
- `BaseWebSliceTest` already provides a mocked `JwtDecoder`; do not duplicate it in concrete
  controller tests.
- Test unauthenticated and forbidden responses when the endpoint contract depends on security.
- Never log or assert real access tokens, refresh tokens, passwords, OTPs, or credentials.

## MockitoBean Rules

- Use `@MockitoBean` from Spring Framework test support:
  `org.springframework.test.context.bean.override.mockito.MockitoBean`.
- Do not use deprecated Spring Boot `@MockBean`.
- Mock only beans outside the current slice.
- Use consistent field names for mocked beans across tests to preserve Spring context caching when
  possible.
- Use qualifiers when multiple beans of the same type exist.
- Prefer BDD Mockito (`given(...).willReturn(...)`) when consistent with nearby tests.
- Avoid over-verifying framework interactions; verify delegation to mocked use cases/services only
  when it improves confidence.

## Test Naming and Formatting

- Class names should end with `Test`.
- Use `@DisplayName` in Brazilian Portuguese.
- Use test method names in English, for example `shouldReturnBadRequestWhenEmailIsInvalid`.
- Use `@Nested` classes for endpoint, query, or behavior groups.
- Use `@ParameterizedTest` for repeated validation cases.
- Use AAA comments exactly:
  - `// 1. Arrange`
  - `// 2. Act`
  - `// 3. Assert`
- Use AssertJ assertions. For `MockMvc`, combine MVC result matchers with AssertJ when extracting
  values improves readability.
- Use these tags:
  - `@Tag("web-slice")` for Web MVC slice tests
  - `@Tag("jpa-slice")` for JPA slice tests
  - `@Tag("redis-slice")` for Redis slice tests

## Validation Commands

After creating or updating a slice test, run the smallest relevant command:

```bash
./mvnw -Dtest=TargetClassTest test
```

For documentation-only changes to this guide, run:

```bash
./mvnw clean compile
```

If a test fails because the test is wrong, fix the test. If a test reveals a production bug, do not
silently change production code; explain the bug and propose the minimal correction.

## Do Not

- Do not use `@SpringBootTest` in slice tests.
- Do not load the full Spring context.
- Do not create or duplicate Testcontainer configurations.
- Do not duplicate base-class annotations, dynamic properties, imports, or mocked infrastructure.
- Do not mock PostgreSQL in JPA slice tests.
- Do not mock Redis in Redis slice tests.
- Do not use H2 for PostgreSQL-specific behavior.
- Do not use deprecated Spring Boot `@MockBean`.
- Do not test controller business logic in Web MVC slice tests.
- Do not test basic repository CRUD unless project-specific behavior is involved.
- Do not disable security filters without an existing project convention.
- Do not modify production code, migrations, endpoint contracts, JWT claims, cookie names, or
  schemas while creating slice tests unless explicitly requested.
- Do not introduce secrets, real credentials, or real tokens in tests.
