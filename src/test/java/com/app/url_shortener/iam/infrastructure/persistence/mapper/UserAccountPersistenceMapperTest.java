package com.app.url_shortener.iam.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.domain.model.Permission;
import com.app.url_shortener.iam.domain.model.Role;
import com.app.url_shortener.iam.domain.model.UserAccount;
import com.app.url_shortener.iam.infrastructure.persistence.entity.PermissionEntity;
import com.app.url_shortener.iam.infrastructure.persistence.entity.RoleEntity;
import com.app.url_shortener.iam.infrastructure.persistence.entity.UserEntity;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

@Tag("unit")
@DisplayName("Testes de Unidade - UserAccountPersistenceMapper")
class UserAccountPersistenceMapperTest {

  private final UserAccountPersistenceMapper mapper = createMapper();

  @Nested
  @DisplayName("Mapeamento para Domínio")
  class ToDomainTests {

    @Test
    @DisplayName("Deve mapear entidade de usuário para domínio sem roles")
    void shouldMapUserEntityToDomainWithoutRoles() {
      // 1. Arrange
      var userId = UUID.randomUUID();
      var roleId = UUID.randomUUID();
      var permissionId = UUID.randomUUID();
      var permissionEntity = new PermissionEntity(permissionId, "url:create", "Criar URLs");
      var roleEntity = new RoleEntity(roleId, "ROLE_USER", true, Set.of(permissionEntity));
      var entity = new UserEntity(
              userId,
              "Maria Silva",
              "maria@email.com",
              "password-hash",
              UserStatus.ACTIVE,
              PlanType.FREE,
              true,
              null,
              null,
              Set.of(roleEntity)
      );

      // 2. Act
      var domain = mapper.toDomain(entity);

      // 3. Assert
      assertBasicUser(domain, userId);
      assertThat(domain.getRoles()).isEmpty();
    }

    @Test
    @DisplayName("Deve mapear entidade de usuário para domínio com roles sem permissões")
    void shouldMapUserEntityToDomainWithRoles() {
      // 1. Arrange
      var userId = UUID.randomUUID();
      var roleId = UUID.randomUUID();
      var permissionEntity = new PermissionEntity(
              UUID.randomUUID(),
              "url:create",
              "Criar URLs"
      );
      var roleEntity = new RoleEntity(roleId, "ROLE_USER", true, Set.of(permissionEntity));
      var entity = userEntity(userId, Set.of(roleEntity));

      // 2. Act
      var domain = mapper.toDomainWithRoles(entity);

      // 3. Assert
      assertBasicUser(domain, userId);
      assertThat(domain.getRoles())
              .singleElement()
              .satisfies(role -> {
                assertThat(role.getId()).isEqualTo(roleId);
                assertThat(role.getName()).isEqualTo("ROLE_USER");
                assertThat(role.isDefault()).isTrue();
                assertThat(role.getPermissions()).isEmpty();
              });
    }

    @Test
    @DisplayName("Deve mapear entidade de usuário para domínio com roles e permissões")
    void shouldMapUserEntityToDomainWithRolesAndPermissions() {
      // 1. Arrange
      var userId = UUID.randomUUID();
      var roleId = UUID.randomUUID();
      var permissionId = UUID.randomUUID();
      var permissionEntity = new PermissionEntity(permissionId, "url:create", "Criar URLs");
      var roleEntity = new RoleEntity(roleId, "ROLE_USER", true, Set.of(permissionEntity));
      var entity = userEntity(userId, Set.of(roleEntity));

      // 2. Act
      var domain = mapper.toDomainWithRolesAndPermissions(entity);

      // 3. Assert
      assertBasicUser(domain, userId);
      assertThat(domain.getRoles())
              .singleElement()
              .satisfies(role -> {
                assertThat(role.getId()).isEqualTo(roleId);
                assertThat(role.getName()).isEqualTo("ROLE_USER");
                assertThat(role.isDefault()).isTrue();
                assertThat(role.getPermissions())
                        .singleElement()
                        .satisfies(permission -> {
                          assertThat(permission.getId()).isEqualTo(permissionId);
                          assertThat(permission.getName()).isEqualTo("url:create");
                          assertThat(permission.getDescription()).isEqualTo("Criar URLs");
                        });
              });
    }

    @Test
    @DisplayName("Deve retornar nulo quando entidade for nula nos mapeamentos explícitos")
    void shouldReturnNullWhenUserEntityIsNullForExplicitMappings() {
      // 1. Arrange
      UserEntity entity = null;

      // 2. Act
      var withoutRoles = mapper.toDomainWithoutRoles(entity);
      var withRoles = mapper.toDomainWithRoles(entity);
      var withRolesAndPermissions = mapper.toDomainWithRolesAndPermissions(entity);

      // 3. Assert
      assertThat(withoutRoles).isNull();
      assertThat(withRoles).isNull();
      assertThat(withRolesAndPermissions).isNull();
    }

    @Test
    @DisplayName("Deve retornar nulo quando a entidade for nula")
    void shouldReturnNullWhenUserEntityIsNull() {
      // 1. Arrange
      UserEntity entity = null;

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
    @DisplayName("Deve mapear domínio de usuário para entidade com roles e permissões")
    void shouldMapUserDomainToEntityWithRolesAndPermissions() {
      // 1. Arrange
      var userId = UUID.randomUUID();
      var roleId = UUID.randomUUID();
      var permissionId = UUID.randomUUID();
      var permission = Permission.restore(permissionId, "url:read", "Consultar URLs");
      var role = Role.restore(roleId, "ROLE_ADMIN", false, Set.of(permission));
      var domain = UserAccount.restore(
              userId,
              "João Silva",
              "joao@email.com",
              "password-hash",
              UserStatus.PENDING_EMAIL_VERIFICATION,
              PlanType.PREMIUM,
              false,
              Set.of(role)
      );

      // 2. Act
      var entity = mapper.toEntity(domain);

      // 3. Assert
      assertThat(domain).isNotNull();
      assertThat(entity).isNotNull();
      assertThat(entity.getId()).isEqualTo(userId);
      assertThat(entity.getName()).isEqualTo("João Silva");
      assertThat(entity.getEmail()).isEqualTo("joao@email.com");
      assertThat(entity.getPasswordHash()).isEqualTo("password-hash");
      assertThat(entity.getStatus()).isEqualTo(UserStatus.PENDING_EMAIL_VERIFICATION);
      assertThat(entity.getPlan()).isEqualTo(PlanType.PREMIUM);
      assertThat(entity.isEmailVerified()).isFalse();
      assertThat(entity.getCreatedAt()).isNull();
      assertThat(entity.getUpdatedAt()).isNull();
      assertThat(entity.getCreatedBy()).isNull();
      assertThat(entity.getUpdatedBy()).isNull();
      assertThat(entity.getRoles())
              .singleElement()
              .satisfies(roleEntity -> {
                assertThat(roleEntity.getId()).isEqualTo(roleId);
                assertThat(roleEntity.getName()).isEqualTo("ROLE_ADMIN");
                assertThat(roleEntity.isDefault()).isFalse();
                assertThat(roleEntity.getPermissions())
                        .singleElement()
                        .satisfies(permissionEntity -> {
                          assertThat(permissionEntity.getId()).isEqualTo(permissionId);
                          assertThat(permissionEntity.getName()).isEqualTo("url:read");
                          assertThat(permissionEntity.getDescription()).isEqualTo("Consultar URLs");
                        });
              });
    }

    @Test
    @DisplayName("Deve retornar nulo quando o domínio for nulo")
    void shouldReturnNullWhenUserDomainIsNull() {
      // 1. Arrange
      UserAccount domain = null;

      // 2. Act
      var entity = mapper.toEntity(domain);

      // 3. Assert
      assertThat(entity).isNull();
    }
  }

  private static UserAccountPersistenceMapper createMapper() {
    var permissionMapper = Mappers.getMapper(PermissionPersistenceMapper.class);
    var roleMapper = Mappers.getMapper(RolePersistenceMapper.class);
    setField(roleMapper, "permissionPersistenceMapper", permissionMapper);

    var mapper = Mappers.getMapper(UserAccountPersistenceMapper.class);
    setField(mapper, "rolePersistenceMapper", roleMapper);
    return mapper;
  }

  private static UserEntity userEntity(UUID id, Set<RoleEntity> roles) {
    return new UserEntity(
            id,
            "Maria Silva",
            "maria@email.com",
            "password-hash",
            UserStatus.ACTIVE,
            PlanType.FREE,
            true,
            null,
            null,
            roles
    );
  }

  private static void assertBasicUser(UserAccount domain, UUID id) {
    assertThat(domain).isNotNull();
    assertThat(domain.getId()).isEqualTo(id);
    assertThat(domain.getName()).isEqualTo("Maria Silva");
    assertThat(domain.getEmail()).isEqualTo("maria@email.com");
    assertThat(domain.getPasswordHash()).isEqualTo("password-hash");
    assertThat(domain.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(domain.getPlan()).isEqualTo(PlanType.FREE);
    assertThat(domain.isEmailVerified()).isTrue();
  }

  private static void setField(Object target, String fieldName, Object value) {
    try {
      setFields(target, fieldName, value);
    } catch (NoSuchFieldException | IllegalAccessException exception) {
      throw new IllegalStateException("Could not configure mapper dependency", exception);
    }
  }

  private static void setFields(Object target, String fieldName, Object value)
          throws NoSuchFieldException, IllegalAccessException {
    Class<?> currentType = target.getClass();
    boolean found = false;
    while (currentType != null) {
      try {
        Field field = currentType.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
        found = true;
      } catch (NoSuchFieldException exception) {
        // Continue searching fields declared in superclasses.
      }
      currentType = currentType.getSuperclass();
    }

    if (!found) {
      throw new NoSuchFieldException(fieldName);
    }
  }
}
