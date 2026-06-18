package com.app.url_shortener.iam.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.app.url_shortener.iam.domain.enums.EmailDispatchPurpose;
import com.app.url_shortener.iam.domain.enums.EmailDispatchReason;
import com.app.url_shortener.iam.domain.enums.EmailDispatchStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Entidade EmailDispatch")
class EmailDispatchTest {

  private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID VERIFICATION_TOKEN_ID =
      UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final Instant CREATED_AT = Instant.parse("2026-06-16T10:00:00Z");

  @Nested
  @DisplayName("Criação e restauração")
  class CreationAndRestore {

    @Test
    @DisplayName("Deve criar dispatch pendente com os dados obrigatórios normalizados")
    void shouldCreatePendingDispatch() {
      // 1. Arrange

      // 2. Act
      EmailDispatch dispatch =
          EmailDispatch.create(
              EVENT_ID,
              USER_ID,
              VERIFICATION_TOKEN_ID,
              " user@example.com ",
              EmailDispatchPurpose.EMAIL_VERIFICATION,
              EmailDispatchReason.REGISTER,
              CREATED_AT);

      // 3. Assert
      assertThat(dispatch.getId()).isNotNull();
      assertThat(dispatch.getEventId()).isEqualTo(EVENT_ID);
      assertThat(dispatch.getUserId()).isEqualTo(USER_ID);
      assertThat(dispatch.getVerificationTokenId()).isEqualTo(VERIFICATION_TOKEN_ID);
      assertThat(dispatch.getEmail()).isEqualTo("user@example.com");
      assertThat(dispatch.getPurpose()).isEqualTo(EmailDispatchPurpose.EMAIL_VERIFICATION);
      assertThat(dispatch.getReason()).isEqualTo(EmailDispatchReason.REGISTER);
      assertThat(dispatch.getStatus()).isEqualTo(EmailDispatchStatus.PENDING);
      assertThat(dispatch.getSendAttempts()).isZero();
      assertThat(dispatch.getCreatedAt()).isEqualTo(CREATED_AT);
      assertThat(dispatch.getUpdatedAt()).isEqualTo(CREATED_AT);
      assertThat(dispatch.isPending()).isTrue();
      assertThat(dispatch.isAccepted()).isFalse();
    }

    @Test
    @DisplayName("Deve restaurar dispatch aceito com os dados persistidos")
    void shouldRestoreAcceptedDispatch() {
      // 1. Arrange
      UUID id = UUID.fromString("33333333-3333-3333-3333-333333333333");
      Instant acceptedAt = CREATED_AT.plus(Duration.ofSeconds(30));
      Instant updatedAt = CREATED_AT.plus(Duration.ofMinutes(1));

      // 2. Act
      EmailDispatch dispatch =
          restore(
              id,
              EmailDispatchStatus.ACCEPTED,
              1,
              CREATED_AT.plus(Duration.ofSeconds(5)),
              acceptedAt,
              null,
              "provider-message-id",
              null,
              null,
              updatedAt);

      // 3. Assert
      assertThat(dispatch.getId()).isEqualTo(id);
      assertThat(dispatch.getStatus()).isEqualTo(EmailDispatchStatus.ACCEPTED);
      assertThat(dispatch.getProviderMessageId()).isEqualTo("provider-message-id");
      assertThat(dispatch.getAcceptedAt()).isEqualTo(acceptedAt);
      assertThat(dispatch.getUpdatedAt()).isEqualTo(updatedAt);
      assertThat(dispatch.isAccepted()).isTrue();
    }
  }

  @Nested
  @DisplayName("Decisões de leitura")
  class ReadDecisions {

    @Test
    @DisplayName("Deve identificar status atual")
    void shouldIdentifyCurrentStatus() {
      // 1. Arrange
      EmailDispatch pendingDispatch = pendingDispatch();
      EmailDispatch sendingDispatch =
          restore(
              UUID.randomUUID(),
              EmailDispatchStatus.SENDING,
              1,
              CREATED_AT.plus(Duration.ofSeconds(10)),
              null,
              null,
              null,
              null,
              null,
              CREATED_AT.plus(Duration.ofSeconds(10)));
      EmailDispatch acceptedDispatch =
          restore(
              UUID.randomUUID(),
              EmailDispatchStatus.ACCEPTED,
              1,
              CREATED_AT.plus(Duration.ofSeconds(10)),
              CREATED_AT.plus(Duration.ofSeconds(20)),
              null,
              "provider-message-id",
              null,
              null,
              CREATED_AT.plus(Duration.ofSeconds(20)));
      EmailDispatch failedDispatch =
          restore(
              UUID.randomUUID(),
              EmailDispatchStatus.FAILED,
              1,
              CREATED_AT.plus(Duration.ofSeconds(10)),
              null,
              CREATED_AT.plus(Duration.ofSeconds(20)),
              null,
              "PROVIDER_ERROR",
              null,
              CREATED_AT.plus(Duration.ofSeconds(20)));

      // 2. Act
      boolean pending = pendingDispatch.isPending();
      boolean sending = sendingDispatch.isSending();
      boolean accepted = acceptedDispatch.isAccepted();
      boolean failed = failedDispatch.isFailed();

      // 3. Assert
      assertThat(pending).isTrue();
      assertThat(sending).isTrue();
      assertThat(accepted).isTrue();
      assertThat(failed).isTrue();
    }

    @Test
    @DisplayName("Deve indicar envio recente quando tentativa em andamento ainda não expirou")
    void shouldIdentifyRecentSending() {
      // 1. Arrange
      Instant sendingStartedAt = CREATED_AT.plus(Duration.ofSeconds(10));
      EmailDispatch dispatch =
          restore(
              UUID.randomUUID(),
              EmailDispatchStatus.SENDING,
              1,
              sendingStartedAt,
              null,
              null,
              null,
              null,
              null,
              sendingStartedAt);
      Instant now = sendingStartedAt.plus(Duration.ofSeconds(30));

      // 2. Act
      boolean sendingRecently = dispatch.isSendingRecently(now, Duration.ofMinutes(1));

      // 3. Assert
      assertThat(sendingRecently).isTrue();
    }

    @Test
    @DisplayName("Deve indicar envio não recente quando timeout expirou")
    void shouldIdentifyExpiredSendingTimeout() {
      // 1. Arrange
      Instant sendingStartedAt = CREATED_AT.plus(Duration.ofSeconds(10));
      EmailDispatch dispatch =
          restore(
              UUID.randomUUID(),
              EmailDispatchStatus.SENDING,
              1,
              sendingStartedAt,
              null,
              null,
              null,
              null,
              null,
              sendingStartedAt);
      Instant now = sendingStartedAt.plus(Duration.ofMinutes(2));

      // 2. Act
      boolean sendingRecently = dispatch.isSendingRecently(now, Duration.ofMinutes(1));

      // 3. Assert
      assertThat(sendingRecently).isFalse();
    }

  }

  @Nested
  @DisplayName("Validação")
  class Validation {

    @Test
    @DisplayName("Deve rejeitar tentativas negativas ao restaurar")
    void shouldRejectNegativeAttempts() {
      // 1. Arrange
      UUID id = UUID.randomUUID();

      // 2. Act / 3. Assert
      assertThatThrownBy(
              () ->
                  restore(
                      id,
                      EmailDispatchStatus.PENDING,
                      -1,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      CREATED_AT))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("sendAttempts must not be negative");
    }

    @Test
    @DisplayName("Deve rejeitar updatedAt anterior ao createdAt")
    void shouldRejectUpdatedAtBeforeCreatedAt() {
      // 1. Arrange
      UUID id = UUID.randomUUID();

      // 2. Act / 3. Assert
      assertThatThrownBy(
              () ->
                  restore(
                      id,
                      EmailDispatchStatus.PENDING,
                      0,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      CREATED_AT.minus(Duration.ofSeconds(1))))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("updatedAt must not be before createdAt");
    }

    @Test
    @DisplayName("Deve rejeitar timestamps opcionais anteriores ao createdAt")
    void shouldRejectOptionalTimestampsBeforeCreatedAt() {
      // 1. Arrange
      Instant beforeCreatedAt = CREATED_AT.minus(Duration.ofSeconds(1));

      // 2. Act / 3. Assert
      assertThatThrownBy(
              () ->
                  restore(
                      UUID.randomUUID(),
                      EmailDispatchStatus.SENDING,
                      1,
                      beforeCreatedAt,
                      null,
                      null,
                      null,
                      null,
                      null,
                      CREATED_AT))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("sendingStartedAt must not be before createdAt");
    }

    @Test
    @DisplayName("Deve rejeitar status ACCEPTED sem metadados de aceite")
    void shouldRejectAcceptedStatusWithoutAcceptanceMetadata() {
      // 1. Arrange
      UUID id = UUID.randomUUID();

      // 2. Act / 3. Assert
      assertThatThrownBy(
              () ->
                  restore(
                      id,
                      EmailDispatchStatus.ACCEPTED,
                      1,
                      CREATED_AT.plus(Duration.ofSeconds(1)),
                      null,
                      null,
                      null,
                      null,
                      null,
                      CREATED_AT.plus(Duration.ofSeconds(1))))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Accepted email dispatch requires acceptance metadata");
    }

    @Test
    @DisplayName("Deve permitir status FAILED sem mensagem de erro")
    void shouldAllowFailedStatusWithoutErrorMessage() {
      // 1. Arrange
      Instant failedAt = CREATED_AT.plus(Duration.ofSeconds(20));

      // 2. Act
      EmailDispatch dispatch =
          restore(
              UUID.randomUUID(),
              EmailDispatchStatus.FAILED,
              1,
              CREATED_AT.plus(Duration.ofSeconds(1)),
              null,
              failedAt,
              null,
              "PROVIDER_ERROR",
              null,
              failedAt);

      // 3. Assert
      assertThat(dispatch.isFailed()).isTrue();
      assertThat(dispatch.getFailedAt()).isEqualTo(failedAt);
      assertThat(dispatch.getLastErrorCode()).isEqualTo("PROVIDER_ERROR");
      assertThat(dispatch.getLastErrorMessage()).isNull();
    }

    @Test
    @DisplayName("Deve rejeitar status FAILED sem metadados obrigatórios de falha")
    void shouldRejectFailedStatusWithoutRequiredFailureMetadata() {
      // 1. Arrange
      UUID id = UUID.randomUUID();

      // 2. Act / 3. Assert
      assertThatThrownBy(
              () ->
                  restore(
                      id,
                      EmailDispatchStatus.FAILED,
                      1,
                      CREATED_AT.plus(Duration.ofSeconds(1)),
                      null,
                      null,
                      null,
                      "PROVIDER_ERROR",
                      null,
                      CREATED_AT.plus(Duration.ofSeconds(1))))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Failed email dispatch requires failure metadata");
    }

    @Test
    @DisplayName("Deve rejeitar token de verificação nulo")
    void shouldRejectNullVerificationTokenId() {
      // 1. Arrange

      // 2. Act / 3. Assert
      assertThatThrownBy(
              () ->
                  EmailDispatch.create(
                      EVENT_ID,
                      USER_ID,
                      null,
                      "user@example.com",
                      EmailDispatchPurpose.EMAIL_VERIFICATION,
                      EmailDispatchReason.REGISTER,
                      CREATED_AT))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("verificationTokenId is required");
    }

    @Test
    @DisplayName("Não deve expor dados sensíveis ou de provedor no toString")
    void shouldNotExposeSensitiveOrProviderDataInToString() {
      // 1. Arrange
      EmailDispatch acceptedDispatch =
          restore(
              UUID.randomUUID(),
              EmailDispatchStatus.ACCEPTED,
              1,
              CREATED_AT.plus(Duration.ofSeconds(1)),
              CREATED_AT.plus(Duration.ofSeconds(2)),
              null,
              "provider-message-id",
              null,
              null,
              CREATED_AT.plus(Duration.ofSeconds(2)));
      EmailDispatch failedDispatch =
          restore(
              UUID.randomUUID(),
              EmailDispatchStatus.FAILED,
              1,
              CREATED_AT.plus(Duration.ofSeconds(1)),
              null,
              CREATED_AT.plus(Duration.ofSeconds(2)),
              null,
              "PROVIDER_ERROR",
              "Provider rejected message",
              CREATED_AT.plus(Duration.ofSeconds(2)));

      // 2. Act
      String acceptedResult = acceptedDispatch.toString();
      String failedResult = failedDispatch.toString();

      // 3. Assert
      assertThat(acceptedResult)
          .doesNotContain("provider-message-id");
      assertThat(failedResult)
          .doesNotContain("Provider rejected message");
    }
  }

  private static EmailDispatch pendingDispatch() {
    return restore(
        UUID.randomUUID(),
        EmailDispatchStatus.PENDING,
        0,
        null,
        null,
        null,
        null,
        null,
        null,
        CREATED_AT);
  }

  private static EmailDispatch restore(
      UUID id,
      EmailDispatchStatus status,
      int sendAttempts,
      Instant sendingStartedAt,
      Instant acceptedAt,
      Instant failedAt,
      String providerMessageId,
      String lastErrorCode,
      String lastErrorMessage,
      Instant updatedAt) {
    return EmailDispatch.restore(
        id,
        EVENT_ID,
        USER_ID,
        VERIFICATION_TOKEN_ID,
        "user@example.com",
        EmailDispatchPurpose.EMAIL_VERIFICATION,
        EmailDispatchReason.REGISTER,
        status,
        providerMessageId,
        sendAttempts,
        sendingStartedAt,
        acceptedAt,
        failedAt,
        lastErrorCode,
        lastErrorMessage,
        CREATED_AT,
        updatedAt);
  }
}
