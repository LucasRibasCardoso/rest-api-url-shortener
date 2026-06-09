package com.app.url_shortener.iam.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Testes de Unidade - Entidade Role")
class RoleTest {

  @Nested
  @DisplayName("Criação e restauração")
  class CreationAndRestoreTests {

    @Test
    @DisplayName("Deve canonicalizar o nome ao restaurar uma role")
    void shouldNormalizeNameWhenRestoringRole() {
      // 1. Arrange
      var id = UUID.randomUUID();

      // 2. Act
      var role = Role.restore(id, "  user  ", true, Set.of());

      // 3. Assert
      assertThat(role.getName()).isEqualTo("USER");
    }

    @Test
    @DisplayName("Deve manter nome canônico sem prefixo de authority")
    void shouldKeepCanonicalNameWithoutAuthorityPrefix() {
      // 1. Arrange

      // 2. Act
      var role = Role.create("admin", Set.of());

      // 3. Assert
      assertThat(role.getName()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("Deve rejeitar nome com prefixo reservado para authorities")
    void shouldRejectNameWithAuthorityPrefix() {
      // 1. Arrange

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> Role.create("ROLE_USER", Set.of()));

      // 3. Assert
      throwableAssert
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("name must not use the ROLE_ authority prefix");
    }

    @Test
    @DisplayName("Deve rejeitar nome em branco")
    void shouldRejectBlankName() {
      // 1. Arrange

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> Role.create("   ", Set.of()));

      // 3. Assert
      throwableAssert
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("name must not be blank");
    }
  }
}
