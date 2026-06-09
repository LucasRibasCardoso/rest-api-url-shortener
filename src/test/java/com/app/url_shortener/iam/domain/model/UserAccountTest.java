package com.app.url_shortener.iam.domain.model;

import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.domain.exception.user.UserAccountLockedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

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

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.app.url_shortener.iam.domain.model.UserAccountTest#blankRequiredFields")
    @DisplayName("Deve rejeitar campos textuais obrigatórios em branco")
    void shouldRejectBlankRequiredTextFields(
        String scenario, String name, String email, String passwordHash, String expectedMessage) {
      // 1. Arrange

      // 2. Act
      var throwableAssert =
          assertThatThrownBy(
              () -> UserAccount.createPendingRegistration(name, email, passwordHash));

      // 3. Assert
      throwableAssert.isInstanceOf(IllegalArgumentException.class).hasMessage(expectedMessage);
    }

    @Test
    @DisplayName("Deve rejeitar restauração quando o status for nulo")
    void shouldRejectRestoreWhenStatusIsNull() {
      // 1. Arrange
      var userId = UUID.randomUUID();

      // 2. Act
      var throwableAssert =
          assertThatThrownBy(
              () ->
                  UserAccount.restore(
                      userId, "Maria", "maria@mail.com", "hash", null, PlanType.FREE, false, Set.of()));

      // 3. Assert
      throwableAssert
          .isInstanceOf(NullPointerException.class)
          .hasMessage("status is required");
    }

    @Test
    @DisplayName("Deve rejeitar restauração quando o plano for nulo")
    void shouldRejectRestoreWhenPlanIsNull() {
      // 1. Arrange
      var userId = UUID.randomUUID();

      // 2. Act
      var throwableAssert =
          assertThatThrownBy(
              () ->
                  UserAccount.restore(
                      userId,
                      "Maria",
                      "maria@mail.com",
                      "hash",
                      UserStatus.PENDING_EMAIL_VERIFICATION,
                      null,
                      false,
                      Set.of()));

      // 3. Assert
      throwableAssert
          .isInstanceOf(NullPointerException.class)
          .hasMessage("planType is required");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.app.url_shortener.iam.domain.model.UserAccountTest#invalidStatusCombinations")
    @DisplayName("Deve rejeitar combinações inconsistentes de status e verificação de e-mail")
    void shouldRejectInconsistentStatusAndEmailVerification(
        String scenario, UserStatus status, boolean emailVerified, String expectedMessage) {
      // 1. Arrange
      var userId = UUID.randomUUID();

      // 2. Act
      var throwableAssert =
          assertThatThrownBy(
              () ->
                  UserAccount.restore(
                      userId,
                      "Maria",
                      "maria@mail.com",
                      "hash",
                      status,
                      PlanType.FREE,
                      emailVerified,
                      Set.of()));

      // 3. Assert
      throwableAssert
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage(expectedMessage);
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
      var defaultRole = defaultRole();

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
      var defaultRole = defaultRole();
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
      var defaultRole = defaultRole();
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

    @Test
    @DisplayName("Deve rejeitar role que não seja padrão sem alterar o usuário")
    void shouldRejectNonDefaultRoleWithoutChangingUser() {
      // 1. Arrange
      var user = UserAccount.createPendingRegistration("Maria", "maria@mail.com", "hash");
      var nonDefaultRole = Role.create("ROLE_USER", Set.of());

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> user.verifyEmail(nonDefaultRole));

      // 3. Assert
      throwableAssert
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("defaultRole must be a default role");
      assertThat(user.isEmailVerified()).isFalse();
      assertThat(user.isPending()).isTrue();
      assertThat(user.getRoles()).isEmpty();
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

  private static Stream<Arguments> invalidStatusCombinations() {
    return Stream.of(
        Arguments.of(
            "ACTIVE sem e-mail verificado",
            UserStatus.ACTIVE,
            false,
            "Active user account must have a verified email"),
        Arguments.of(
            "PENDING_EMAIL_VERIFICATION com e-mail verificado",
            UserStatus.PENDING_EMAIL_VERIFICATION,
            true,
            "Pending user account must not have a verified email"));
  }

  private static Stream<Arguments> blankRequiredFields() {
    return Stream.of(
        Arguments.of("nome em branco", "   ", "maria@mail.com", "hash", "name must not be blank"),
        Arguments.of("e-mail em branco", "Maria", "   ", "hash", "email must not be blank"),
        Arguments.of(
            "hash da senha em branco",
            "Maria",
            "maria@mail.com",
            "   ",
            "passwordHash must not be blank"));
  }

  private static Role defaultRole() {
    return Role.restore(UUID.randomUUID(), "ROLE_USER", true, Set.of());
  }
}
