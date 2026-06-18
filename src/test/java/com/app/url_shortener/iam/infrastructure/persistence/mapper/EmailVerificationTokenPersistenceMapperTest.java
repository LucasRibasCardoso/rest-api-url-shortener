package com.app.url_shortener.iam.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.domain.model.EmailVerificationToken;
import com.app.url_shortener.iam.infrastructure.entity.EmailVerificationTokenEntity;
import com.app.url_shortener.iam.infrastructure.entity.UserEntity;
import com.app.url_shortener.iam.infrastructure.mapper.EmailVerificationTokenPersistenceMapper;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

@Tag("unit")
@DisplayName("Testes de Unidade - EmailVerificationTokenPersistenceMapper")
class EmailVerificationTokenPersistenceMapperTest {

  private static final UUID TOKEN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final Instant CREATED_AT = Instant.parse("2026-06-16T10:00:00Z");
  private static final Instant EXPIRES_AT = CREATED_AT.plus(Duration.ofMinutes(10));

  private final EmailVerificationTokenPersistenceMapper mapper = createMapper();

  @Nested
  @DisplayName("Mapeamento para Domínio")
  class ToDomainTests {

    @Test
    @DisplayName("Deve mapear entidade de token para domínio")
    void shouldMapEmailVerificationTokenEntityToDomain() {
      // 1. Arrange
      var consumedAt = CREATED_AT.plus(Duration.ofMinutes(2));
      var lastAttemptAt = CREATED_AT.plus(Duration.ofMinutes(1));
      var updatedAt = consumedAt;
      var entity =
          new EmailVerificationTokenEntity(
              TOKEN_ID,
              userEntity(USER_ID),
              "user@example.com",
              "verification-code-hash",
              "encrypted-code",
              EXPIRES_AT,
              consumedAt,
              null,
              1,
              lastAttemptAt,
              CREATED_AT,
              updatedAt);

      // 2. Act
      var domain = mapper.toDomain(entity);

      // 3. Assert
      assertThat(domain).isNotNull();
      assertThat(domain.getId()).isEqualTo(TOKEN_ID);
      assertThat(domain.getUserId()).isEqualTo(USER_ID);
      assertThat(domain.getEmail()).isEqualTo("user@example.com");
      assertThat(domain.getHashedCode()).isEqualTo("verification-code-hash");
      assertThat(domain.getEncryptedCode()).isEqualTo("encrypted-code");
      assertThat(domain.getExpiresAt()).isEqualTo(EXPIRES_AT);
      assertThat(domain.getConsumedAt()).isEqualTo(consumedAt);
      assertThat(domain.getRevokedAt()).isNull();
      assertThat(domain.getFailedAttempts()).isEqualTo(1);
      assertThat(domain.getLastAttemptAt()).isEqualTo(lastAttemptAt);
      assertThat(domain.getCreatedAt()).isEqualTo(CREATED_AT);
      assertThat(domain.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("Deve retornar nulo quando a entidade for nula")
    void shouldReturnNullWhenEmailVerificationTokenEntityIsNull() {
      // 1. Arrange
      EmailVerificationTokenEntity entity = null;

      // 2. Act
      var domain = mapper.toDomain(entity);

      // 3. Assert
      assertThat(domain).isNull();
    }
  }

  @Nested
  @DisplayName("Mapeamento para Entidade")
  class ToEntityTests {

    @Test
    @DisplayName("Deve mapear domínio de token para entidade")
    void shouldMapEmailVerificationTokenDomainToEntity() {
      // 1. Arrange
      var revokedAt = CREATED_AT.plus(Duration.ofMinutes(2));
      var updatedAt = revokedAt;
      var domain =
          EmailVerificationToken.restore(
              TOKEN_ID,
              USER_ID,
              "user@example.com",
              "verification-code-hash",
              "encrypted-code",
              EXPIRES_AT,
              null,
              revokedAt,
              0,
              null,
              CREATED_AT,
              updatedAt);

      // 2. Act
      var entity = mapper.toEntity(domain);

      // 3. Assert
      assertThat(entity).isNotNull();
      assertThat(entity.getId()).isEqualTo(TOKEN_ID);
      assertThat(entity.getUser()).isNotNull();
      assertThat(entity.getUser().getId()).isEqualTo(USER_ID);
      assertThat(entity.getEmail()).isEqualTo("user@example.com");
      assertThat(entity.getHashedCode()).isEqualTo("verification-code-hash");
      assertThat(entity.getEncryptedCode()).isEqualTo("encrypted-code");
      assertThat(entity.getExpiresAt()).isEqualTo(EXPIRES_AT);
      assertThat(entity.getConsumedAt()).isNull();
      assertThat(entity.getRevokedAt()).isEqualTo(revokedAt);
      assertThat(entity.getFailedAttempts()).isZero();
      assertThat(entity.getLastAttemptAt()).isNull();
      assertThat(entity.getCreatedAt()).isEqualTo(CREATED_AT);
      assertThat(entity.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("Deve retornar nulo quando o domínio for nulo")
    void shouldReturnNullWhenEmailVerificationTokenDomainIsNull() {
      // 1. Arrange
      EmailVerificationToken domain = null;

      // 2. Act
      var entity = mapper.toEntity(domain);

      // 3. Assert
      assertThat(entity).isNull();
    }
  }

  private static EmailVerificationTokenPersistenceMapper createMapper() {
    var mapper = Mappers.getMapper(EmailVerificationTokenPersistenceMapper.class);
    setEntityManager(mapper, entityManagerProxy());
    return mapper;
  }

  private static void setEntityManager(
      EmailVerificationTokenPersistenceMapper mapper, EntityManager entityManager) {
    try {
      Field field = EmailVerificationTokenPersistenceMapper.class.getDeclaredField("entityManager");
      field.setAccessible(true);
      field.set(mapper, entityManager);
    } catch (NoSuchFieldException | IllegalAccessException exception) {
      throw new IllegalStateException("Could not configure mapper dependency", exception);
    }
  }

  private static EntityManager entityManagerProxy() {
    return (EntityManager)
        Proxy.newProxyInstance(
            EntityManager.class.getClassLoader(),
            new Class<?>[] {EntityManager.class},
            (proxy, method, args) -> {
              if ("getReference".equals(method.getName())) {
                return getReference(args);
              }
              if ("toString".equals(method.getName())) {
                return "EntityManager test proxy";
              }
              throw new UnsupportedOperationException("Unsupported EntityManager method: " + method.getName());
            });
  }

  private static Object getReference(Object[] args) {
    var entityClass = (Class<?>) args[0];
    var id = (UUID) args[1];

    if (entityClass.equals(UserEntity.class)) {
      return userEntity(id);
    }

    throw new UnsupportedOperationException("Unsupported reference type: " + entityClass.getName());
  }

  private static UserEntity userEntity(UUID id) {
    return new UserEntity(
        id,
        "Reference User",
        "reference@email.com",
        "password-hash",
        UserStatus.ACTIVE,
        PlanType.FREE,
        true,
        null,
        null,
        Set.of());
  }
}
