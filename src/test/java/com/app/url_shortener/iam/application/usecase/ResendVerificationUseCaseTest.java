package com.app.url_shortener.iam.application.usecase;

import com.app.url_shortener.iam.application.command.ResendVerificationCommand;
import com.app.url_shortener.iam.application.port.output.CheckAuthRateLimitPort;
import com.app.url_shortener.iam.application.port.output.EmailVerificationTokenPort;
import com.app.url_shortener.iam.application.port.output.UserAccountRepositoryPort;
import com.app.url_shortener.iam.application.usecase.impl.ResendVerificationUseCaseImpl;
import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.domain.event.EmailVerificationEvent;
import com.app.url_shortener.iam.domain.model.UserAccount;
import com.app.url_shortener.iam.domain.valueobject.EmailVerificationToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Caso de Uso Reenvio de Verificação")
class ResendVerificationUseCaseTest {

  private static final Duration VERIFICATION_CODE_TTL = Duration.ofMinutes(10);
  private static final String RESPONSE_MESSAGE = "Enviamos um novo código de verificação para o seu e-mail.";

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @Mock
  private UserAccountRepositoryPort userAccountRepositoryPort;

  @Mock
  private EmailVerificationTokenPort emailVerificationTokenStore;

  @Mock
  private CheckAuthRateLimitPort checkAuthRateLimitPort;

  @Captor
  private ArgumentCaptor<EmailVerificationToken> emailVerificationTokenCaptor;

  @Captor
  private ArgumentCaptor<EmailVerificationEvent> emailVerificationEventCaptor;

  @InjectMocks
  private ResendVerificationUseCaseImpl resendVerificationUseCase;

  @Nested
  @DisplayName("Execução do reenvio de verificação")
  class ExecuteTests {

    @Test
    @DisplayName("Deve armazenar novo token e publicar evento quando o usuário estiver pendente")
    void shouldStoreNewTokenAndPublishEventWhenUserIsPending() {
      // 1. Arrange
      var command = new ResendVerificationCommand(" USER@EMAIL.COM ");
      var pendingUser = pendingUser();
      var beforeExecution = Instant.now();

      given(userAccountRepositoryPort.findByEmail("user@email.com")).willReturn(Optional.of(pendingUser));

      // 2. Act
      var result = resendVerificationUseCase.execute(command);

      // 3. Assert
      assertThat(result.message()).isEqualTo(RESPONSE_MESSAGE);

      verify(emailVerificationTokenStore)
              .store(emailVerificationTokenCaptor.capture(), eq(VERIFICATION_CODE_TTL));
      var storedToken = emailVerificationTokenCaptor.getValue();
      var expectedExpiration = beforeExecution.plus(VERIFICATION_CODE_TTL);

      assertAll(
              () -> assertThat(storedToken.userId()).isEqualTo(pendingUser.getId()),
              () -> assertThat(storedToken.email()).isEqualTo(pendingUser.getEmail()),
              () -> assertThat(storedToken.code()).isNotNull(),
              () -> assertThat(storedToken.code().value()).matches("\\d{6}"),
              () -> assertThat(storedToken.expiresAt()).isCloseTo(expectedExpiration, within(2, ChronoUnit.SECONDS))
      );

      verify(eventPublisher).publishEvent(emailVerificationEventCaptor.capture());
      var publishedEvent = emailVerificationEventCaptor.getValue();

      assertAll(
              () -> assertThat(publishedEvent.userId()).isEqualTo(pendingUser.getId()),
              () -> assertThat(publishedEvent.email()).isEqualTo(pendingUser.getEmail()),
              () -> assertThat(publishedEvent.code()).isEqualTo(storedToken.code())
      );

      InOrder inOrder = inOrder(checkAuthRateLimitPort, userAccountRepositoryPort);
      inOrder.verify(checkAuthRateLimitPort).checkResendVerification("user@email.com");
      inOrder.verify(userAccountRepositoryPort).findByEmail("user@email.com");

      verifyNoMoreInteractions(checkAuthRateLimitPort, userAccountRepositoryPort, emailVerificationTokenStore, eventPublisher);
    }

    @Test
    @DisplayName("Deve retornar mensagem padrão sem armazenar token quando o usuário não existir")
    void shouldReturnDefaultMessageWithoutStoringTokenWhenUserDoesNotExist() {
      // 1. Arrange
      var command = new ResendVerificationCommand("unknown@email.com");

      given(userAccountRepositoryPort.findByEmail(command.email())).willReturn(Optional.empty());

      // 2. Act
      var result = resendVerificationUseCase.execute(command);

      // 3. Assert
      assertThat(result.message()).isEqualTo(RESPONSE_MESSAGE);

      InOrder inOrder = inOrder(checkAuthRateLimitPort, userAccountRepositoryPort);
      inOrder.verify(checkAuthRateLimitPort).checkResendVerification(command.email());
      inOrder.verify(userAccountRepositoryPort).findByEmail(command.email());

      verifyNoInteractions(emailVerificationTokenStore, eventPublisher);
      verifyNoMoreInteractions(checkAuthRateLimitPort, userAccountRepositoryPort);
    }

    @Test
    @DisplayName("Deve retornar mensagem padrão sem armazenar token quando o usuário não estiver pendente")
    void shouldReturnDefaultMessageWithoutStoringTokenWhenUserIsNotPending() {
      // 1. Arrange
      var command = new ResendVerificationCommand("user@email.com");
      var activeUser = activeUser();

      given(userAccountRepositoryPort.findByEmail(command.email())).willReturn(Optional.of(activeUser));

      // 2. Act
      var result = resendVerificationUseCase.execute(command);

      // 3. Assert
      assertThat(result.message()).isEqualTo(RESPONSE_MESSAGE);

      InOrder inOrder = inOrder(checkAuthRateLimitPort, userAccountRepositoryPort);
      inOrder.verify(checkAuthRateLimitPort).checkResendVerification(command.email());
      inOrder.verify(userAccountRepositoryPort).findByEmail(command.email());

      verifyNoInteractions(emailVerificationTokenStore, eventPublisher);
      verifyNoMoreInteractions(checkAuthRateLimitPort, userAccountRepositoryPort);
    }

    @Test
    @DisplayName("Deve propagar exceção e não publicar evento quando o armazenamento do token falhar")
    void shouldPropagateExceptionAndNotPublishEventWhenTokenStorageFails() {
      // 1. Arrange
      var command = new ResendVerificationCommand("user@email.com");
      var pendingUser = pendingUser();
      var exception = new IllegalStateException("Falha ao armazenar token de verificação.");

      given(userAccountRepositoryPort.findByEmail(command.email())).willReturn(Optional.of(pendingUser));
      doThrow(exception).when(emailVerificationTokenStore)
              .store(any(EmailVerificationToken.class), eq(VERIFICATION_CODE_TTL));

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> resendVerificationUseCase.execute(command));

      // 3. Assert
      throwableAssert
              .isInstanceOf(IllegalStateException.class)
              .hasMessage("Falha ao armazenar token de verificação.");

      InOrder inOrder = inOrder(checkAuthRateLimitPort, userAccountRepositoryPort);
      inOrder.verify(checkAuthRateLimitPort).checkResendVerification(command.email());
      inOrder.verify(userAccountRepositoryPort).findByEmail(command.email());

      verify(emailVerificationTokenStore).store(any(EmailVerificationToken.class), eq(VERIFICATION_CODE_TTL));
      verifyNoInteractions(eventPublisher);
      verifyNoMoreInteractions(checkAuthRateLimitPort, userAccountRepositoryPort, emailVerificationTokenStore);
    }
  }

  private UserAccount pendingUser() {
    return UserAccount.restore(
            UUID.fromString("019a19e6-fc96-7e7c-996e-86d7c3470001"),
            "User Name",
            "user@email.com",
            "encoded-password",
            UserStatus.PENDING_EMAIL_VERIFICATION,
            PlanType.FREE,
            false,
            Set.of()
    );
  }

  private UserAccount activeUser() {
    return UserAccount.restore(
            UUID.fromString("019a19e6-fc96-7e7c-996e-86d7c3470002"),
            "User Name",
            "user@email.com",
            "encoded-password",
            UserStatus.ACTIVE,
            PlanType.FREE,
            true,
            Set.of()
    );
  }
}
