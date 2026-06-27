# Integration Tests Style Guide

## Purpose

This guide defines the project conventions for full Spring integration tests. Use these tests only
when the scenario requires a complete `ApplicationContext` and real infrastructure clients against
PostgreSQL, Redis, or AWS services emulated by LocalStack.

Unit tests follow `docs/testing/unit-tests-style.md`. Slice tests follow
`docs/testing/slice-tests-style.md`.

## Test Scope

Integration tests in this project fall into two categories:

- **HTTP end-to-end tests:** exercise the application through a real HTTP request on a random port
  and validate persistence, cache, or messaging side effects.
- **Infrastructure-flow tests:** exercise a worker, messaging consumer, publisher, or external
  service strategy through a Spring-managed entrypoint and validate the real infrastructure
  interaction. These tests do not need an artificial HTTP request.

Use an integration test when the behavior depends on wiring or collaboration across multiple
layers. Prefer a unit or slice test when the same behavior can be verified without loading the full
application.

## General Rules

- Create tests under `src/test/java` in the package of the primary entrypoint under test.
- All integration tests must extend
  `src/test/java/com/app/url_shortener/config/AbstractIntegrationTest.java`.
- Class names must end with `IT`.
- The inherited `@Tag("integration")` is the canonical integration-test tag. Do not duplicate it.
- Do not declare containers or duplicate `@DynamicPropertySource` in concrete test classes.
- Do not use `MockMvc`; use REST Assured for application HTTP requests.
- Do not mock PostgreSQL, Redis, DynamoDB, SQS, or SES.
- Do not use `@Transactional` as a cleanup mechanism.
- Use AssertJ for state and extracted-value assertions. REST Assured response matchers may use
  Hamcrest through its fluent API.
- Use exact AAA comments: `// Arrange`, `// Act`, `// Assert`.
- Use `@DisplayName` in Brazilian Portuguese and English test method names.
- Keep classes and methods package-private.
- Integration tests must run sequentially. Shared containers, mutable infrastructure, and REST
  Assured global configuration make parallel execution unsafe.

## Shared Test Infrastructure

`AbstractIntegrationTest` provides:

- `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)`;
- the inherited `integration` tag;
- dynamic PostgreSQL, Redis, DynamoDB, SQS, and SES properties;
- the random HTTP port configured in REST Assured;
- disabled redirect following, so redirect responses can be asserted directly;
- idempotent LocalStack resource setup before tests in each concrete class;
- PostgreSQL, Redis, DynamoDB, SQS, and SES cleanup before every test.

The container support classes use the Testcontainers singleton-container pattern. Each container is
started once per test JVM/fork when its support class is initialized. Ryuk handles container cleanup
when the JVM exits. Do not combine this manual lifecycle with concrete-test `@Testcontainers` or
`@Container` declarations.

The setup and reset sequence is:

1. Start the singleton PostgreSQL, Redis, and LocalStack containers.
2. Register container endpoints before the Spring context is created.
3. Create LocalStack tables, queues, DLQs, and SES identity idempotently.
4. Before each test, reset PostgreSQL, Redis, DynamoDB, SQS, and captured SES messages.
5. Configure the current random server port and redirect behavior in REST Assured.

### Redis State

`AbstractIntegrationTest` calls `RedisContainerSupport.resetRedis()` in its inherited `@BeforeEach`,
together with the PostgreSQL and LocalStack resets. The reset executes `FLUSHDB` against the isolated
test container, so every integration test starts with an empty current Redis database.

Concrete integration tests must not call `FLUSHDB`, `FLUSHALL`, or delete broad key patterns. Arrange
only the Redis state required by the scenario and rely on the base-class reset for isolation.

### User Test Data

Use `src/test/java/com/app/url_shortener/config/UserTestDataFactory.java` when an integration test
needs a persisted user as Arrange data. Inject the factory through the test constructor instead of
duplicating `UserEntity`, role-repository, password-encoding, or user-repository setup in concrete
test classes.

The factory currently provides:

- `createActiveUser(email, rawPassword)`: creates an active, verified user with the default role;
- `createPendingUser(email, rawPassword)`: creates a user pending email verification without roles.

Example:

```java
private final UserTestDataFactory userTestDataFactory;

@Autowired
TargetIT(UserTestDataFactory userTestDataFactory) {
  this.userTestDataFactory = userTestDataFactory;
}

// Arrange
UserEntity user =
    userTestDataFactory.createActiveUser(
        "user.integration@example.com", "secure-password");
```

Use this factory only when user creation is test setup. When registration itself is the behavior
under test, create the user through the real registration endpoint and assert its persisted state.
The base-class PostgreSQL reset remains responsible for removing users created by the factory.

## State Isolation

- Every test must be independently executable and must not depend on method or class order.
- Rely on the base-class resets for PostgreSQL, Redis, DynamoDB, SQS, and SES. Do not duplicate
  repository `deleteAll`, Redis flush, or infrastructure purge calls in concrete tests.
- Clean state before a test is preferred to cleanup-only `@AfterEach`: a failed or interrupted test
  must not contaminate the next test.
- Do not use `@Transactional` around HTTP or messaging tests. The application executes on different
  threads and transactions, and external publications cannot be rolled back with the test method.
- Use unique, deterministic identifiers in Arrange data when they make failures easier to diagnose.
- If a new infrastructure service is added, add setup and reset support centrally before writing
  tests that depend on it.

## HTTP Tests with REST Assured

Use REST Assured only for requests to the application. Use the AWS SDK or a narrowly scoped LocalStack
diagnostic API for infrastructure inspection.

The base class configures the random server port. Concrete tests must not mutate global
`RestAssured.port`, `baseURI`, authentication, filters, or specifications. Configure scenario-specific
headers, cookies, and authentication on the request.

Example:

```java
var requestBody =
    """
    {
      "originalUrl": "https://example.com/resource"
    }
    """;

var response =
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + accessToken)
        .body(requestBody)
    .when()
        .post("/api/v1/urls")
    .then()
        .log().ifValidationFails()
        .statusCode(201)
        .contentType(ContentType.JSON)
        .body("shortCode", not(blankOrNullString()))
        .extract()
        .response();
```

### Request Contracts

- Prefer JSON text blocks for API request bodies. This keeps contract tests independent from
  production DTO constructors and serializers.
- A shared JSON fixture is acceptable for large, stable payloads. Keep fixtures close to the test
  and make relevant values explicit.
- Always set the request content type when a body is sent.
- Assert status, response content type, required headers or cookies, and contract-relevant fields.
- Do not assert every response field when it does not contribute to the behavior under test.
- Extract values only after validating the HTTP response, then use AssertJ for subsequent state
  assertions.
- Because redirect following is disabled, explicitly assert the status and `Location` header for
  redirect endpoints.

### Logging and Sensitive Data

- Use `.log().ifValidationFails()` instead of unconditional request or response logging.
- Never log access tokens, refresh tokens, passwords, verification codes, credentials, or secure
  cookies.
- If common REST Assured logging is configured in the base class, blacklist at least
  `Authorization`, `Cookie`, and `Set-Cookie` headers.
- Do not include real secrets or tokens in assertion failure messages.

## Side-Effect Assertions

An HTTP assertion alone is insufficient when the contract includes a durable side effect. After the
HTTP response, verify only the relevant observable outcomes, for example:

- PostgreSQL state through a Spring Data repository or `JdbcTemplate`;
- DynamoDB state through the enhanced or low-level AWS SDK client;
- Redis state and TTL through the real Redis-facing component or template;
- an SQS message through `SqsTemplate` or `SqsClient`;
- a captured SES message through the LocalStack diagnostic endpoint.

Do not expose persistence entities through HTTP merely to simplify a test.

## Asynchronous Flows

Use Awaitility for consumers, publishers, scheduled work, eventual cache changes, and other
asynchronous outcomes. Awaitility is available transitively through `spring-boot-starter-test`.

```java
await()
    .atMost(Duration.ofSeconds(5))
    .pollInterval(Duration.ofMillis(200))
    .untilAsserted(() -> assertThat(repository.findById(id)).isPresent());
```

- Never use `Thread.sleep()`.
- Keep timeouts bounded and short. A timeout is a failure, not a reason to wait indefinitely.
- Put only repeatable assertions inside `untilAsserted`.
- Do not perform destructive reads inside polling unless consuming the message is the behavior under
  test.
- Prefer polling the final observable state over internal implementation details.

The test profile currently disables SQS listener auto-startup and the outbox publisher. Tests that
exercise these flows must enable only the required feature for that test context. A small,
class-level `@TestPropertySource` is acceptable for selecting a conditional strategy or enabling a
worker. Keep property combinations stable and reuse them across related tests to preserve Spring
context caching.

## LocalStack and AWS Services

- Use the AWS SDK configured by the Spring context whenever practical.
- Do not hard-code port `4566`; use the endpoint registered by `LocalStackContainerSupport`.
- Queue names, table names, region, and credentials come from test configuration or dynamic
  properties.
- Do not instantiate a second LocalStack container or create duplicate queues and tables in a
  concrete test.
- Publishing directly to SQS is valid Arrange behavior for a consumer test.
- Reading a queue or LocalStack diagnostic endpoint is valid Assert behavior.
- Tests must not make calls to real AWS endpoints.

## Security Tests

- Do not disable or mock Spring Security.
- Authentication-flow tests should obtain tokens through the real login or refresh endpoint.
- Endpoint tests may use a test helper that issues a valid application JWT when authentication is
  setup rather than the behavior under test.
- Pass tokens using `Authorization: Bearer <token>` and preserve secure refresh-cookie behavior.
- Cover `401 Unauthorized` and `403 Forbidden` when they are part of the endpoint contract.
- Never print or assert complete access tokens, refresh tokens, passwords, OTPs, or credentials.

## Mocking and External Systems

- Do not use `@Mock`, `@MockBean`, or `@MockitoBean` for application components in an integration
  flow.
- A third-party service outside the repository's managed infrastructure may be replaced by a
  protocol-level test server such as WireMock when necessary.
- Do not add WireMock or another dependency until a concrete integration scenario requires it.
- A protocol stub must preserve the external HTTP contract and must not replace application business
  logic.

## Naming and Organization

- Name classes after the behavior or entrypoint, ending in `IT`.
- In HTTP tests, include the expected HTTP status code in `@DisplayName`, for example:
  `@DisplayName("Deve retornar 201 ao cadastrar usuário")`. Infrastructure-flow tests without an
  HTTP response do not need a status code in the display name.
- Use `@Nested` only when a class covers multiple related operations or endpoint branches.
- Keep Arrange helpers private and focused on test data or infrastructure inspection.
- Do not build a second application architecture inside test helpers.
- Keep one behavioral reason for failure per test.

## Maven Execution

The Maven build separates test categories by class-name convention:

- unit and slice tests end with `Test` and run through Surefire in the `test` phase;
- full integration tests end with `IT` and run through Failsafe in the `integration-test` and
  `verify` phases.

Use these commands:

```bash
# Smallest relevant unit or slice test
./mvnw -Dtest=TargetTest test

# Smallest relevant integration test
./mvnw -Dit.test=TargetIT verify

# Complete project validation
./mvnw clean verify
```

Do not name full integration tests `*IntegrationTest`; that suffix matches Surefire's default
`*Test` selection and bypasses the intended Failsafe lifecycle.

## Definition of Done

- The test extends `AbstractIntegrationTest`.
- The scenario uses real managed infrastructure and no application-component mocks.
- Initial state is independent of test order.
- HTTP calls use REST Assured; infrastructure inspection uses the appropriate real client.
- Relevant HTTP contracts and durable side effects are asserted.
- Asynchronous assertions use Awaitility with a bounded timeout.
- Sensitive material is not logged or exposed in failures.
- The smallest relevant integration test passes.
- No unrelated context variants, containers, dependencies, or production changes are introduced.

## Do Not

- Do not use `MockMvc` for integration tests.
- Do not declare containers in concrete tests.
- Do not mock managed infrastructure or application components.
- Do not use `@Transactional` for cleanup.
- Do not depend on test execution order.
- Do not enable parallel execution.
- Do not use `Thread.sleep()`.
- Do not unconditionally log requests or responses containing authentication material.
- Do not hard-code mapped container ports.
- Do not add broad `@TestPropertySource` overrides that create unnecessary Spring contexts.
