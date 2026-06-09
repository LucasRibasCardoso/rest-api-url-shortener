package com.app.url_shortener.iam.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Testes de Unidade - Entidade Permission")
class PermissionTest {

  @Nested
  @DisplayName("Criação e restauração")
  class CreationAndRestoreTests {

    @Test
    @DisplayName("Deve normalizar o nome ao restaurar uma permissão")
    void shouldNormalizeNameWhenRestoringPermission() {
      // 1. Arrange
      var id = UUID.randomUUID();

      // 2. Act
      var permission = Permission.restore(id, "  url:create  ", "Criar URL");

      // 3. Assert
      assertThat(permission.getName()).isEqualTo("url:create");
    }

    @Test
    @DisplayName("Deve rejeitar nome em branco")
    void shouldRejectBlankName() {
      // 1. Arrange

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> Permission.create("   ", "Criar URL"));

      // 3. Assert
      throwableAssert
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("name must not be blank");
    }
  }
}
