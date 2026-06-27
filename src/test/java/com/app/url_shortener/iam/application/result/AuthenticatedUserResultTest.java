package com.app.url_shortener.iam.application.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Testes de Unidade - AuthenticatedUserResult")
class AuthenticatedUserResultTest {

  @Test
  @DisplayName("Deve criar cópias imutáveis de roles e authorities")
  void shouldCreateImmutableCopiesOfRolesAndAuthorities() {
    // 1. Arrange
    var roles = new ArrayList<>(List.of("USER"));
    var authorities = new ArrayList<>(List.of("url:create"));

    // 2. Act
    var result =
        new AuthenticatedUserResult(
            UUID.randomUUID(), "User Name", "user@email.com", roles, authorities, "FREE");
    roles.add("ADMIN");
    authorities.add("url:delete:any");

    // 3. Assert
    assertThat(result.roles()).containsExactly("USER");
    assertThat(result.authorities()).containsExactly("url:create");
    assertThatThrownBy(() -> result.roles().add("ADMIN"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> result.authorities().add("url:delete:any"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
