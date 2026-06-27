package com.app.url_shortener.iam.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.app.url_shortener.iam.application.command.VerifyEmailCommand;
import com.app.url_shortener.iam.application.port.output.CheckAuthRateLimitPort;
import com.app.url_shortener.iam.application.port.output.EmailVerificationTokenRepositoryPort;
import com.app.url_shortener.iam.application.port.output.RoleRepositoryPort;
import com.app.url_shortener.iam.application.port.output.UserAccountRepositoryPort;
import com.app.url_shortener.iam.application.port.output.VerificationCodeProtectorPort;
import com.app.url_shortener.iam.application.usecase.impl.VerifyEmailUseCaseImpl;
import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.domain.exception.auth.InvalidOrExpiredEmailVerificationCodeException;
import com.app.url_shortener.iam.domain.model.EmailVerificationToken;
import com.app.url_shortener.iam.domain.model.Role;
import com.app.url_shortener.iam.domain.model.UserAccount;
import com.app.url_shortener.iam.domain.valueobject.VerificationCode;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Caso de Uso Verificação de Email")
class VerifyEmailUseCaseTest {

  private static final UUID USER_ID = UUID.fromString("019a45c6-6005-7e50-a2a7-4a3bbf160101");
  private static final UUID TOKEN_ID = UUID.fromString("019a45c6-6005-7e50-a2a7-4a3bbf160201");
  private static final UUID ROLE_ID = UUID.fromString("019a45c6-6005-7e50-a2a7-4a3bbf160301");
  private static final String EMAIL = "user@email.com";
  private static final String HASHED_CODE = "verification-code-hash";
  private static final String SUCCESS_MESSAGE =
      "E-mail verificado com sucesso. Agora você pode fazer login na sua conta.";

  @Mock private RoleRepositoryPort roleRepositoryPort;

  @Mock private CheckAuthRateLimitPort checkAuthRateLimitPort;

  @Mock private UserAccountRepositoryPort userAccountRepositoryPort;

  @Mock private VerificationCodeProtectorPort verificationCodeProtectorPort;

  @Mock private EmailVerificationTokenRepositoryPort emailVerificationTokenRepositoryPort;

  @InjectMocks private VerifyEmailUseCaseImpl verifyEmailUseCase;

  @Nested
  @DisplayName("Execução da verificação de email")
  class ExecuteTests {

    @Test
    @DisplayName("Deve consumir token ativo e ativar conta com sucesso")
    void shouldConsumeActiveTokenAndActivateAccountSuccessfully() {
      // 1. Arrange
      var command = command();
      var userAccount = pendingUser();
      var token = activeToken();
      var defaultRole = defaultRole();

      given(userAccountRepositoryPort.findByEmailWithRoles(command.email()))
          .willReturn(Optional.of(userAccount));
      given(
              emailVerificationTokenRepositoryPort.findActiveByUserIdAndEmail(
                  any(UUID.class), any(String.class), any(Instant.class)))
          .willReturn(Optional.of(token));
      given(verificationCodeProtectorPort.matches(command.code(), HASHED_CODE)).willReturn(true);
      given(
              emailVerificationTokenRepositoryPort.consumeIfActive(
                  any(UUID.class), any(Instant.class)))
          .willReturn(true);
      given(roleRepositoryPort.findDefaultRole()).willReturn(defaultRole);
      given(userAccountRepositoryPort.save(userAccount)).willReturn(userAccount);

      // 2. Act
      var result = verifyEmailUseCase.execute(command);

      // 3. Assert
      assertThat(result.message()).isEqualTo(SUCCESS_MESSAGE);
      assertThat(userAccount.isActive()).isTrue();
      assertThat(userAccount.isEmailVerified()).isTrue();
      assertThat(userAccount.getRoles()).containsExactly(defaultRole);

      var inOrder =
          inOrder(
              checkAuthRateLimitPort,
              userAccountRepositoryPort,
              emailVerificationTokenRepositoryPort,
              verificationCodeProtectorPort,
              roleRepositoryPort);
      inOrder.verify(checkAuthRateLimitPort).checkVerifyEmail(command.email());
      inOrder.verify(userAccountRepositoryPort).findByEmailWithRoles(command.email());
      inOrder
          .verify(emailVerificationTokenRepositoryPort)
          .findActiveByUserIdAndEmail(any(UUID.class), any(String.class), any(Instant.class));
      inOrder.verify(verificationCodeProtectorPort).matches(command.code(), HASHED_CODE);
      inOrder
          .verify(emailVerificationTokenRepositoryPort)
          .consumeIfActive(any(UUID.class), any(Instant.class));
      inOrder.verify(roleRepositoryPort).findDefaultRole();
      verify(userAccountRepositoryPort).save(userAccount);
      verifyNoMoreInteractions(
          checkAuthRateLimitPort,
          userAccountRepositoryPort,
          emailVerificationTokenRepositoryPort,
          verificationCodeProtectorPort,
          roleRepositoryPort);
    }

    @Test
    @DisplayName("Deve rejeitar quando usuário não existir")
    void shouldRejectWhenUserDoesNotExist() {
      // 1. Arrange
      var command = command();

      given(userAccountRepositoryPort.findByEmailWithRoles(command.email()))
          .willReturn(Optional.empty());

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> verifyEmailUseCase.execute(command));

      // 3. Assert
      throwableAssert.isInstanceOf(InvalidOrExpiredEmailVerificationCodeException.class);

      verify(checkAuthRateLimitPort).checkVerifyEmail(command.email());
      verify(userAccountRepositoryPort).findByEmailWithRoles(command.email());
      verifyNoInteractions(
          emailVerificationTokenRepositoryPort, verificationCodeProtectorPort, roleRepositoryPort);
      verify(userAccountRepositoryPort, never()).save(any(UserAccount.class));
      verifyNoMoreInteractions(checkAuthRateLimitPort, userAccountRepositoryPort);
    }

    @Test
    @DisplayName("Deve rejeitar quando não houver token ativo")
    void shouldRejectWhenThereIsNoActiveToken() {
      // 1. Arrange
      var command = command();
      var userAccount = pendingUser();

      given(userAccountRepositoryPort.findByEmailWithRoles(command.email()))
          .willReturn(Optional.of(userAccount));
      given(
              emailVerificationTokenRepositoryPort.findActiveByUserIdAndEmail(
                  any(UUID.class), any(String.class), any(Instant.class)))
          .willReturn(Optional.empty());

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> verifyEmailUseCase.execute(command));

      // 3. Assert
      throwableAssert.isInstanceOf(InvalidOrExpiredEmailVerificationCodeException.class);

      verify(checkAuthRateLimitPort).checkVerifyEmail(command.email());
      verify(userAccountRepositoryPort).findByEmailWithRoles(command.email());
      verify(emailVerificationTokenRepositoryPort)
          .findActiveByUserIdAndEmail(any(UUID.class), any(String.class), any(Instant.class));
      verifyNoInteractions(verificationCodeProtectorPort, roleRepositoryPort);
      verify(userAccountRepositoryPort, never()).save(any(UserAccount.class));
      verifyNoMoreInteractions(
          checkAuthRateLimitPort, userAccountRepositoryPort, emailVerificationTokenRepositoryPort);
    }

    @Test
    @DisplayName("Deve registrar tentativa falha e rejeitar quando código não conferir")
    void shouldRegisterFailedAttemptAndRejectWhenCodeDoesNotMatch() {
      // 1. Arrange
      var command = command();
      var userAccount = pendingUser();
      var token = activeToken();

      given(userAccountRepositoryPort.findByEmailWithRoles(command.email()))
          .willReturn(Optional.of(userAccount));
      given(
              emailVerificationTokenRepositoryPort.findActiveByUserIdAndEmail(
                  any(UUID.class), any(String.class), any(Instant.class)))
          .willReturn(Optional.of(token));
      given(verificationCodeProtectorPort.matches(command.code(), HASHED_CODE)).willReturn(false);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> verifyEmailUseCase.execute(command));

      // 3. Assert
      throwableAssert.isInstanceOf(InvalidOrExpiredEmailVerificationCodeException.class);

      verify(checkAuthRateLimitPort).checkVerifyEmail(command.email());
      verify(userAccountRepositoryPort).findByEmailWithRoles(command.email());
      verify(emailVerificationTokenRepositoryPort)
          .findActiveByUserIdAndEmail(any(UUID.class), any(String.class), any(Instant.class));
      verify(verificationCodeProtectorPort).matches(command.code(), HASHED_CODE);
      verify(emailVerificationTokenRepositoryPort)
          .registerFailedAttempt(any(UUID.class), any(Instant.class));
      verify(emailVerificationTokenRepositoryPort, never())
          .consumeIfActive(any(UUID.class), any(Instant.class));
      verifyNoInteractions(roleRepositoryPort);
      verify(userAccountRepositoryPort, never()).save(any(UserAccount.class));
      verifyNoMoreInteractions(
          checkAuthRateLimitPort,
          userAccountRepositoryPort,
          emailVerificationTokenRepositoryPort,
          verificationCodeProtectorPort);
    }

    @Test
    @DisplayName("Deve rejeitar quando token já tiver sido consumido por outro processo")
    void shouldRejectWhenTokenWasAlreadyConsumedByAnotherProcess() {
      // 1. Arrange
      var command = command();
      var userAccount = pendingUser();
      var token = activeToken();

      given(userAccountRepositoryPort.findByEmailWithRoles(command.email()))
          .willReturn(Optional.of(userAccount));
      given(
              emailVerificationTokenRepositoryPort.findActiveByUserIdAndEmail(
                  any(UUID.class), any(String.class), any(Instant.class)))
          .willReturn(Optional.of(token));
      given(verificationCodeProtectorPort.matches(command.code(), HASHED_CODE)).willReturn(true);
      given(
              emailVerificationTokenRepositoryPort.consumeIfActive(
                  any(UUID.class), any(Instant.class)))
          .willReturn(false);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> verifyEmailUseCase.execute(command));

      // 3. Assert
      throwableAssert.isInstanceOf(InvalidOrExpiredEmailVerificationCodeException.class);

      verify(checkAuthRateLimitPort).checkVerifyEmail(command.email());
      verify(userAccountRepositoryPort).findByEmailWithRoles(command.email());
      verify(emailVerificationTokenRepositoryPort)
          .findActiveByUserIdAndEmail(any(UUID.class), any(String.class), any(Instant.class));
      verify(verificationCodeProtectorPort).matches(command.code(), HASHED_CODE);
      verify(emailVerificationTokenRepositoryPort)
          .consumeIfActive(any(UUID.class), any(Instant.class));
      verifyNoInteractions(roleRepositoryPort);
      verify(userAccountRepositoryPort, never()).save(any(UserAccount.class));
      verifyNoMoreInteractions(
          checkAuthRateLimitPort,
          userAccountRepositoryPort,
          emailVerificationTokenRepositoryPort,
          verificationCodeProtectorPort);
    }
  }

  private static VerifyEmailCommand command() {
    return new VerifyEmailCommand(" USER@EMAIL.COM ", VerificationCode.of("123456"));
  }

  private static UserAccount pendingUser() {
    return UserAccount.restore(
        USER_ID,
        "User Name",
        EMAIL,
        "password-hash",
        UserStatus.PENDING_EMAIL_VERIFICATION,
        PlanType.FREE,
        false,
        Set.of());
  }

  private static EmailVerificationToken activeToken() {
    var now = Instant.parse("2026-06-18T10:00:00Z");

    return EmailVerificationToken.restore(
        TOKEN_ID,
        USER_ID,
        EMAIL,
        HASHED_CODE,
        "encrypted-code",
        now.plusSeconds(600),
        null,
        null,
        0,
        null,
        now,
        now);
  }

  private static Role defaultRole() {
    return Role.restore(ROLE_ID, "USER", true, Set.of());
  }
}
