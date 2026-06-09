package com.app.url_shortener.iam.infrastructure.persistence.mapper;

import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.domain.model.RefreshToken;
import com.app.url_shortener.iam.infrastructure.entity.RefreshTokenEntity;
import com.app.url_shortener.iam.infrastructure.entity.UserEntity;
import com.app.url_shortener.iam.infrastructure.mapper.RefreshTokenPersistenceMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("Testes de Unidade - RefreshTokenPersistenceMapper")
class RefreshTokenPersistenceMapperTest {

  private final RefreshTokenPersistenceMapper mapper = createMapper();

  @Nested
  @DisplayName("Mapeamento para Domínio")
  class ToDomainTests {

    @Test
    @DisplayName("Deve mapear entidade de refresh token para domínio com usuário e token substituto")
    void shouldMapRefreshTokenEntityToDomainWithUserAndReplacement() {
      // 1. Arrange
      var tokenId = UUID.randomUUID();
      var userId = UUID.randomUUID();
      var replacedByTokenId = UUID.randomUUID();
      var createdAt = Instant.parse("2026-05-07T10:00:00Z");
      var expiresAt = Instant.parse("2026-05-14T10:00:00Z");
      var revokedAt = Instant.parse("2026-05-08T10:00:00Z");
      var user = userEntity(userId);
      var replacedByToken = new RefreshTokenEntity(
              replacedByTokenId,
              user,
              "replacement-token-hash",
              createdAt,
              expiresAt,
              null,
              null
      );
      var entity = new RefreshTokenEntity(
              tokenId,
              user,
              "token-hash",
              createdAt,
              expiresAt,
              revokedAt,
              replacedByToken
      );

      // 2. Act
      var domain = mapper.toDomain(entity);

      // 3. Assert
      assertThat(domain).isNotNull();
      assertThat(domain.getId()).isEqualTo(tokenId);
      assertThat(domain.getUserId()).isEqualTo(userId);
      assertThat(domain.getTokenHash()).isEqualTo("token-hash");
      assertThat(domain.getCreatedAt()).isEqualTo(createdAt);
      assertThat(domain.getExpiresAt()).isEqualTo(expiresAt);
      assertThat(domain.getRevokedAt()).isEqualTo(revokedAt);
      assertThat(domain.getReplacedByTokenId()).isEqualTo(replacedByTokenId);
    }

    @Test
    @DisplayName("Deve mapear entidade sem token substituto para domínio")
    void shouldMapRefreshTokenEntityWithoutReplacementToDomain() {
      // 1. Arrange
      var tokenId = UUID.randomUUID();
      var userId = UUID.randomUUID();
      var createdAt = Instant.parse("2026-05-07T10:00:00Z");
      var expiresAt = Instant.parse("2026-05-14T10:00:00Z");
      var entity = new RefreshTokenEntity(
              tokenId,
              userEntity(userId),
              "token-hash",
              createdAt,
              expiresAt,
              null,
              null
      );

      // 2. Act
      var domain = mapper.toDomain(entity);

      // 3. Assert
      assertThat(domain).isNotNull();
      assertThat(domain.getId()).isEqualTo(tokenId);
      assertThat(domain.getUserId()).isEqualTo(userId);
      assertThat(domain.getRevokedAt()).isNull();
      assertThat(domain.getReplacedByTokenId()).isNull();
    }

    @Test
    @DisplayName("Deve retornar nulo quando a entidade for nula")
    void shouldReturnNullWhenRefreshTokenEntityIsNull() {
      // 1. Arrange
      RefreshTokenEntity entity = null;

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
    @DisplayName("Deve mapear domínio de refresh token para entidade com referências JPA")
    void shouldMapRefreshTokenDomainToEntityWithJpaReferences() {
      // 1. Arrange
      var tokenId = UUID.randomUUID();
      var userId = UUID.randomUUID();
      var replacedByTokenId = UUID.randomUUID();
      var createdAt = Instant.parse("2026-05-07T10:00:00Z");
      var expiresAt = Instant.parse("2026-05-14T10:00:00Z");
      var revokedAt = Instant.parse("2026-05-08T10:00:00Z");
      var domain = RefreshToken.restore(
              tokenId,
              userId,
              "token-hash",
              createdAt,
              expiresAt,
              revokedAt,
              replacedByTokenId
      );

      // 2. Act
      var entity = mapper.toEntity(domain);

      // 3. Assert
      assertThat(entity).isNotNull();
      assertThat(entity.getId()).isEqualTo(tokenId);
      assertThat(entity.getUser()).isNotNull();
      assertThat(entity.getUser().getId()).isEqualTo(userId);
      assertThat(entity.getTokenHash()).isEqualTo("token-hash");
      assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
      assertThat(entity.getExpiresAt()).isEqualTo(expiresAt);
      assertThat(entity.getRevokedAt()).isEqualTo(revokedAt);
      assertThat(entity.getReplacedByToken()).isNotNull();
      assertThat(entity.getReplacedByToken().getId()).isEqualTo(replacedByTokenId);
    }

    @Test
    @DisplayName("Deve mapear domínio sem token substituto para entidade")
    void shouldMapRefreshTokenDomainWithoutReplacementToEntity() {
      // 1. Arrange
      var tokenId = UUID.randomUUID();
      var userId = UUID.randomUUID();
      var createdAt = Instant.parse("2026-05-07T10:00:00Z");
      var expiresAt = Instant.parse("2026-05-14T10:00:00Z");
      var domain = RefreshToken.restore(
              tokenId,
              userId,
              "token-hash",
              createdAt,
              expiresAt,
              null,
              null
      );

      // 2. Act
      var entity = mapper.toEntity(domain);

      // 3. Assert
      assertThat(entity).isNotNull();
      assertThat(entity.getId()).isEqualTo(tokenId);
      assertThat(entity.getUser()).isNotNull();
      assertThat(entity.getUser().getId()).isEqualTo(userId);
      assertThat(entity.getReplacedByToken()).isNull();
    }

    @Test
    @DisplayName("Deve retornar nulo quando o domínio for nulo")
    void shouldReturnNullWhenRefreshTokenDomainIsNull() {
      // 1. Arrange
      RefreshToken domain = null;

      // 2. Act
      var entity = mapper.toEntity(domain);

      // 3. Assert
      assertThat(entity).isNull();
    }
  }

  private static RefreshTokenPersistenceMapper createMapper() {
    var mapper = Mappers.getMapper(RefreshTokenPersistenceMapper.class);
    setEntityManager(mapper, entityManagerProxy());
    return mapper;
  }

  private static void setEntityManager(RefreshTokenPersistenceMapper mapper, EntityManager entityManager) {
    try {
      Field field = RefreshTokenPersistenceMapper.class.getDeclaredField("entityManager");
      field.setAccessible(true);
      field.set(mapper, entityManager);
    } catch (NoSuchFieldException | IllegalAccessException exception) {
      throw new IllegalStateException("Could not configure mapper dependency", exception);
    }
  }

  private static EntityManager entityManagerProxy() {
    return (EntityManager) Proxy.newProxyInstance(
            EntityManager.class.getClassLoader(),
            new Class<?>[]{EntityManager.class},
            (proxy, method, args) -> {
              if ("getReference".equals(method.getName())) {
                return getReference(args);
              }
              if ("toString".equals(method.getName())) {
                return "EntityManager test proxy";
              }
              throw new UnsupportedOperationException("Unsupported EntityManager method: " + method.getName());
            }
    );
  }

  private static Object getReference(Object[] args) {
    var entityClass = (Class<?>) args[0];
    var id = (UUID) args[1];

    if (entityClass.equals(UserEntity.class)) {
      return userEntity(id);
    }

    if (entityClass.equals(RefreshTokenEntity.class)) {
      var user = userEntity(UUID.randomUUID());
      var createdAt = Instant.parse("2026-05-07T10:00:00Z");
      var expiresAt = Instant.parse("2026-05-14T10:00:00Z");
      return new RefreshTokenEntity(id, user, "reference-token-hash", createdAt, expiresAt, null, null);
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
            Set.of()
    );
  }
}
