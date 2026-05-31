package com.app.url_shortener.iam.domain.model;

import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.domain.exception.user.UserAccountLockedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@DisplayName("Testes de Unidade - Entidade UserAccount")
class UserAccountTest {

  @Nested
  @DisplayName("Criação e Sanitização de Dados")
  class CreationTests {

    @Test
    @DisplayName("Deve criar um usuário pendente de registro com espaços removidos e plano FREE")
    void shouldCreatePendingUserAndTrimInputs() {
      // Arrange
      var unformattedName = "   João Silva   ";
      var unformattedEmail = "  joao@email.com  ";
      var passwordHash = "hash123";

      // Act
      var user =
              UserAccount.createPendingRegistration(unformattedName, unformattedEmail, passwordHash);

      // Assert
      assertThat(user.getId()).isNotNull();
      assertThat(user.getName()).isEqualTo("João Silva");
      assertThat(user.getEmail()).isEqualTo("joao@email.com");
      assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_EMAIL_VERIFICATION);
      assertThat(user.getPlan()).isEqualTo(PlanType.FREE);
      assertThat(user.isEmailVerified()).isFalse();
      assertThat(user.getRoles()).isEmpty();
    }

    @Test
    @DisplayName("Deve garantir que a lista de roles retorne um conjunto imodificável")
    void shouldReturnUnmodifiableRolesSet() {
      // Arrange
      var user = UserAccount.createPendingRegistration("Nome", "email@mail.com", "hash");
      var role = Role.create("ADMIN", Set.of());

      // Act & Assert
      var roles = user.getRoles();
      assertThatThrownBy(() -> roles.add(role)).isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("Verificação de E-mail (Transição de Estado)")
  class EmailVerificationTests {

    @Test
    @DisplayName("Deve verificar o e-mail, ativar a conta e atribuir a role padrão com sucesso")
    void shouldVerifyEmailAndActivateAccount() {
      // Arrange
      var user = UserAccount.createPendingRegistration("Maria", "maria@mail.com", "hash");
      var defaultRole = Role.create("ROLE_USER", Set.of());

      // Act
      user.verifyEmail(defaultRole);

      // Assert
      assertThat(user.isEmailVerified()).isTrue();
      assertThat(user.isActive()).isTrue();
      assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
      assertThat(user.getRoles()).containsExactly(defaultRole);
    }

    @Test
    @DisplayName("Não deve alterar o estado se a conta já estiver ativada e verificada")
    void shouldDoNothingIfAlreadyVerifiedAndActive() {
      // Arrange
      var defaultRole = Role.create("ROLE_USER", Set.of());
      var user =
              UserAccount.restore(
                      UUID.randomUUID(),
                      "Maria",
                      "maria@mail.com",
                      "hash",
                      UserStatus.ACTIVE,
                      PlanType.FREE,
                      true,
                      Set.of(defaultRole));

      // Act
      user.verifyEmail(defaultRole);

      // Assert
      assertThat(user.getRoles()).hasSize(1);
      assertThat(user.isActive()).isTrue();
    }

    @Test
    @DisplayName("Deve lançar exceção ao tentar verificar e-mail de conta bloqueada")
    void shouldThrowExceptionWhenAccountIsLocked() {
      // Arrange
      var defaultRole = Role.create("ROLE_USER", Set.of());
      var lockedUser = UserAccount.restore(
              UUID.randomUUID(),
              "Maria",
              "maria@mail.com",
              "hash",
              UserStatus.LOCKED,
              PlanType.FREE,
              false,
              null);

      // Act & Assert
      assertThatThrownBy(() -> lockedUser.verifyEmail(defaultRole))
              .isInstanceOf(UserAccountLockedException.class);
    }

    @Test
    @DisplayName("Deve lançar NullPointerException se a role padrão fornecida for nula")
    void shouldThrowExceptionIfDefaultRoleIsNull() {
      // Arrange
      var user = UserAccount.createPendingRegistration("Maria", "maria@mail.com", "hash");

      // Act & Assert
      assertThatThrownBy(() -> user.verifyEmail(null))
              .isInstanceOf(NullPointerException.class)
              .hasMessage("defaultRole must not be null");
    }
  }

  @Nested
  @DisplayName("Representação textual segura")
  class SafeToStringTests {

    @Test
    @DisplayName("Não deve expor passwordHash no toString")
    void shouldNotExposePasswordHashInToString() {
      // 1. Arrange
      var passwordHash = "sensitive-password-hash";
      var user = UserAccount.createPendingRegistration("Nome", "email@mail.com", passwordHash);

      // 2. Act
      var text = user.toString();

      // 3. Assert
      assertThat(text)
          .doesNotContain(passwordHash)
          .doesNotContain("passwordHash");
    }
  }
}
