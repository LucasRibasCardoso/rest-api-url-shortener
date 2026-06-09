package com.app.url_shortener.iam.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.url_shortener.iam.domain.model.Permission;
import com.app.url_shortener.iam.domain.model.Role;
import com.app.url_shortener.iam.infrastructure.persistence.entity.PermissionEntity;
import com.app.url_shortener.iam.infrastructure.persistence.entity.RoleEntity;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

@Tag("unit")
@DisplayName("Testes de Unidade - RolePersistenceMapper")
class RolePersistenceMapperTest {

  private final RolePersistenceMapper mapper = createMapper();

  @Nested
  @DisplayName("Mapeamento para Domínio")
  class ToDomainTests {

    @Test
    @DisplayName("Deve mapear entidade de role para domínio com permissões")
    void shouldMapRoleEntityToDomainWithPermissions() {
      // 1. Arrange
      var roleId = UUID.randomUUID();
      var permissionId = UUID.randomUUID();
      var permissionEntity = new PermissionEntity(
              permissionId,
              "url:create",
              "Criar URLs encurtadas"
      );
      var entity = new RoleEntity(roleId, "ADMIN", true, Set.of(permissionEntity));

      // 2. Act
      var domain = mapper.toDomainWithPermissions(entity);

      // 3. Assert
      assertThat(domain).isNotNull();
      assertThat(domain.getId()).isEqualTo(roleId);
      assertThat(domain.getName()).isEqualTo("ADMIN");
      assertThat(domain.isDefault()).isTrue();
      assertThat(domain.getPermissions())
              .singleElement()
              .satisfies(permission -> {
                assertThat(permission.getId()).isEqualTo(permissionId);
                assertThat(permission.getName()).isEqualTo("url:create");
                assertThat(permission.getDescription()).isEqualTo("Criar URLs encurtadas");
              });
    }

    @Test
    @DisplayName("Deve mapear entidade de role para domínio sem permissões")
    void shouldMapRoleEntityToDomainWithoutPermissions() {
      // 1. Arrange
      var roleId = UUID.randomUUID();
      var permissionEntity = new PermissionEntity(
              UUID.randomUUID(),
              "url:create",
              "Criar URLs encurtadas"
      );
      var entity = new RoleEntity(roleId, "USER", true, Set.of(permissionEntity));

      // 2. Act
      var domain = mapper.toDomainWithoutPermissions(entity);

      // 3. Assert
      assertThat(domain).isNotNull();
      assertThat(domain.getId()).isEqualTo(roleId);
      assertThat(domain.getName()).isEqualTo("USER");
      assertThat(domain.isDefault()).isTrue();
      assertThat(domain.getPermissions()).isEmpty();
    }

    @Test
    @DisplayName("Deve retornar nulo quando a entidade for nula")
    void shouldReturnNullWhenRoleEntityIsNull() {
      // 1. Arrange
      RoleEntity entity = null;

      // 2. Act
      var domain = mapper.toDomainWithPermissions(entity);

      // 3. Assert
      assertThat(domain).isNull();
    }
  }

  @Nested
  @DisplayName("Mapeamento para Entidade")
  class ToEntityTests {

    @Test
    @DisplayName("Deve mapear domínio de role para entidade com permissões")
    void shouldMapRoleDomainToEntityWithPermissions() {
      // 1. Arrange
      var roleId = UUID.randomUUID();
      var permissionId = UUID.randomUUID();
      var permission = Permission.restore(
              permissionId,
              "url:read",
              "Consultar URLs encurtadas"
      );
      var domain = Role.restore(roleId, "USER", false, Set.of(permission));

      // 2. Act
      var entity = mapper.toEntity(domain);

      // 3. Assert
      assertThat(entity).isNotNull();
      assertThat(entity.getId()).isEqualTo(roleId);
      assertThat(entity.getName()).isEqualTo("USER");
      assertThat(entity.isDefault()).isFalse();
      assertThat(entity.getCreatedAt()).isNull();
      assertThat(entity.getUpdatedAt()).isNull();
      assertThat(entity.getPermissions())
              .singleElement()
              .satisfies(permissionEntity -> {
                assertThat(permissionEntity.getId()).isEqualTo(permissionId);
                assertThat(permissionEntity.getName()).isEqualTo("url:read");
                assertThat(permissionEntity.getDescription()).isEqualTo("Consultar URLs encurtadas");
              });
    }

    @Test
    @DisplayName("Deve retornar nulo quando o domínio for nulo")
    void shouldReturnNullWhenRoleDomainIsNull() {
      // 1. Arrange
      Role domain = null;

      // 2. Act
      var entity = mapper.toEntity(domain);

      // 3. Assert
      assertThat(entity).isNull();
    }
  }

  private static RolePersistenceMapper createMapper() {
    var mapper = Mappers.getMapper(RolePersistenceMapper.class);
    var permissionMapper = Mappers.getMapper(PermissionPersistenceMapper.class);
    setField(mapper, "permissionPersistenceMapper", permissionMapper);
    return mapper;
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
