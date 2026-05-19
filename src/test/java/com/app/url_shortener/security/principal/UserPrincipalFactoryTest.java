package com.app.url_shortener.security.principal;

import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.infrastructure.persistence.entity.PermissionEntity;
import com.app.url_shortener.iam.infrastructure.persistence.entity.RoleEntity;
import com.app.url_shortener.iam.infrastructure.persistence.entity.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@Tag("unit")
@DisplayName("Testes de Unidade - UserPrincipalFactory")
class UserPrincipalFactoryTest {

  private final UserPrincipalFactory factory = new UserPrincipalFactory();

  @Nested
  @DisplayName("Criação de Principal")
  class FromTests {

    @Test
    @DisplayName("Deve mapear campos da entidade de usuário para o principal")
    void shouldMapUserEntityFieldsToUserPrincipal() {
      // 1. Arrange
      var userId = UUID.randomUUID();
      var role = role("USER", permission("url:create"));
      var user = userEntity(
              userId,
              "Maria Silva",
              "maria@email.com",
              "password-hash",
              UserStatus.ACTIVE,
              PlanType.PREMIUM,
              roles(role)
      );

      // 2. Act
      var principal = factory.from(user);

      // 3. Assert
      assertAll(
              () -> assertThat(principal.getId()).isEqualTo(userId),
              () -> assertThat(principal.getName()).isEqualTo("Maria Silva"),
              () -> assertThat(principal.getEmail()).isEqualTo("maria@email.com"),
              () -> assertThat(principal.getPasswordHash()).isEqualTo("password-hash"),
              () -> assertThat(principal.getPassword()).isEqualTo("password-hash"),
              () -> assertThat(principal.getUsername()).isEqualTo("maria@email.com"),
              () -> assertThat(principal.getStatus()).isEqualTo(UserStatus.ACTIVE),
              () -> assertThat(principal.getPlan()).isEqualTo(PlanType.PREMIUM),
              () -> assertThat(principal.getAuthorities())
                      .extracting("authority")
                      .containsExactly("ROLE_USER", "url:create")
      );
    }

    @Test
    @DisplayName("Deve prefixar roles sem prefixo e preservar permissões exatamente pelo nome")
    void shouldPrefixRolesWithoutPrefixAndMapPermissionsExactlyByName() {
      // 1. Arrange
      var adminRole = role("ADMIN", permission("url:create"), permission("url:delete"));
      var supportRole = role("ROLE_SUPPORT", permission("users:read"));
      var user = userEntity(roles(adminRole, supportRole));

      // 2. Act
      var principal = factory.from(user);

      // 3. Assert
      assertThat(principal.getAuthorities())
              .extracting("authority")
              .containsExactlyInAnyOrder(
                      "ROLE_ADMIN",
                      "url:create",
                      "url:delete",
                      "ROLE_SUPPORT",
                      "users:read"
              );
    }

    @Test
    @DisplayName("Deve remover authorities duplicadas")
    void shouldRemoveDuplicateAuthorities() {
      // 1. Arrange
      var userRole = role("USER", permission("url:read"), permission("url:create"));
      var prefixedUserRole = role("ROLE_USER", permission("url:read"), permission("url:create"));
      var user = userEntity(roles(userRole, prefixedUserRole));

      // 2. Act
      var principal = factory.from(user);

      // 3. Assert
      assertThat(principal.getAuthorities())
              .extracting("authority")
              .contains("ROLE_USER", "url:read", "url:create");
    }

    @Test
    @DisplayName("Deve retornar principal sem authorities quando usuário não possuir roles")
    void shouldReturnPrincipalWithoutAuthoritiesWhenUserHasNoRoles() {
      // 1. Arrange
      var user = userEntity(Set.of());

      // 2. Act
      var principal = factory.from(user);

      // 3. Assert
      assertThat(principal.getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("Deve retornar apenas a role quando ela não possuir permissões")
    void shouldReturnOnlyRoleAuthorityWhenRoleHasNoPermissions() {
      // 1. Arrange
      var roleWithoutPermissions = role("USER");
      var user = userEntity(roles(roleWithoutPermissions));

      // 2. Act
      var principal = factory.from(user);

      // 3. Assert
      assertThat(principal.getAuthorities())
              .extracting("authority")
              .containsExactly("ROLE_USER");
    }
  }

  private static UserEntity userEntity(Set<RoleEntity> roles) {
    return userEntity(
            UUID.randomUUID(),
            "John Doe",
            "john.doe@email.com",
            "password-hash",
            UserStatus.ACTIVE,
            PlanType.FREE,
            roles
    );
  }

  private static UserEntity userEntity(
          UUID id,
          String name,
          String email,
          String passwordHash,
          UserStatus status,
          PlanType plan,
          Set<RoleEntity> roles
  ) {
    return new UserEntity(
            id,
            name,
            email,
            passwordHash,
            status,
            plan,
            true,
            null,
            null,
            roles
    );
  }

  private static RoleEntity role(String name, PermissionEntity... permissions) {
    return new RoleEntity(UUID.randomUUID(), name, false, orderedSet(permissions));
  }

  private static PermissionEntity permission(String name) {
    return new PermissionEntity(UUID.randomUUID(), name, name);
  }

  private static Set<RoleEntity> roles(RoleEntity... roles) {
    return orderedSet(roles);
  }

  @SafeVarargs
  private static <T> Set<T> orderedSet(T... values) {
    var orderedValues = new LinkedHashSet<T>();
    Collections.addAll(orderedValues, values);
    return orderedValues;
  }
}
