package com.app.url_shortener.iam.application.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.app.url_shortener.iam.application.event.EmailVerificationRequestedEvent;
import com.app.url_shortener.iam.application.policy.EmailVerificationPolicy;
import com.app.url_shortener.iam.application.port.output.EmailDispatchRepositoryPort;
import com.app.url_shortener.iam.application.port.output.EmailVerificationTokenRepositoryPort;
import com.app.url_shortener.iam.application.port.output.VerificationCodeProtectorPort;
import com.app.url_shortener.iam.domain.enums.EmailDispatchPurpose;
import com.app.url_shortener.iam.domain.enums.EmailDispatchReason;
import com.app.url_shortener.iam.domain.enums.EmailDispatchStatus;
import com.app.url_shortener.iam.domain.exception.auth.DuplicateEmailDispatchEventException;
import com.app.url_shortener.iam.domain.exception.auth.DuplicateOpenEmailVerificationTokenException;
import com.app.url_shortener.iam.domain.exception.auth.EmailVerificationTokenNotFoundException;
import com.app.url_shortener.iam.domain.model.EmailDispatch;
import com.app.url_shortener.iam.domain.model.EmailVerificationToken;
import com.app.url_shortener.iam.domain.valueobject.VerificationCode;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Serviço de Dispatch de Verificação de Email")
class EmailDispatchVerificationServiceImplTest {

  private static final Duration CODE_TTL = Duration.ofMinutes(10);
  private static final Instant NOW = Instant.parse("2026-06-18T10:00:00Z");

  @Mock private TransactionTemplate transactionTemplate;

  @Mock private EmailVerificationPolicy emailVerificationPolicy;

  @Mock private EmailDispatchRepositoryPort emailDispatchRepositoryPort;

  @Mock private VerificationCodeProtectorPort verificationCodeProtectorPort;

  @Mock private EmailVerificationTokenRepositoryPort verificationTokenRepositoryPort;

  @Captor private ArgumentCaptor<EmailVerificationToken> tokenCaptor;

  @Captor private ArgumentCaptor<EmailDispatch> dispatchCaptor;

  @InjectMocks private EmailDispatchVerificationServiceImpl service;

  @Nested
  @DisplayName("Busca ou criação")
  class FindOrCreateTests {

    @Test
    @DisplayName("Deve recuperar dispatch existente pelo eventId")
    void shouldRecoverExistingDispatchByEventId() {
      // 1. Arrange
      var event = event(EmailDispatchReason.REGISTER);
      var token = token(UUID.fromString("019a22c5-0987-7af5-88d6-2df3aeb30201"));
      var dispatch = dispatch(event, token.getId());

      given(emailDispatchRepositoryPort.findByEventId(event.eventId()))
          .willReturn(Optional.of(dispatch));
      given(verificationTokenRepositoryPort.findById(token.getId())).willReturn(Optional.of(token));

      // 2. Act
      var result = service.findOrCreate(event);

      // 3. Assert
      assertThat(result.dispatch()).isSameAs(dispatch);
      assertThat(result.token()).isSameAs(token);

      verify(emailDispatchRepositoryPort).findByEventId(event.eventId());
      verify(verificationTokenRepositoryPort).findById(token.getId());
      verifyNoMoreInteractions(
          transactionTemplate,
          emailVerificationPolicy,
          emailDispatchRepositoryPort,
          verificationCodeProtectorPort,
          verificationTokenRepositoryPort);
    }

    @Test
    @DisplayName("Deve criar token e dispatch quando não houver dispatch para o eventId")
    void shouldCreateTokenAndDispatchWhenEventWasNotProcessed() {
      // 1. Arrange
      var event = event(EmailDispatchReason.REGISTER);
      executeTransactions();

      given(emailDispatchRepositoryPort.findByEventId(event.eventId()))
          .willReturn(Optional.empty());
      given(emailVerificationPolicy.codeTtl()).willReturn(CODE_TTL);
      given(verificationCodeProtectorPort.hash(any(VerificationCode.class)))
          .willReturn("verification-code-hash");
      given(verificationCodeProtectorPort.encrypt(any(VerificationCode.class)))
          .willReturn("encrypted-code");
      given(verificationTokenRepositoryPort.save(any(EmailVerificationToken.class)))
          .willAnswer(invocation -> invocation.getArgument(0));
      given(emailDispatchRepositoryPort.save(any(EmailDispatch.class)))
          .willAnswer(invocation -> invocation.getArgument(0));

      // 2. Act
      var result = service.findOrCreate(event);

      // 3. Assert
      verify(verificationTokenRepositoryPort).save(tokenCaptor.capture());
      verify(emailDispatchRepositoryPort).save(dispatchCaptor.capture());

      var savedToken = tokenCaptor.getValue();
      var savedDispatch = dispatchCaptor.getValue();
      assertThat(savedToken.getUserId()).isEqualTo(event.userId());
      assertThat(savedToken.getEmail()).isEqualTo(event.email());
      assertThat(savedToken.getHashedCode()).isEqualTo("verification-code-hash");
      assertThat(savedToken.getEncryptedCode()).isEqualTo("encrypted-code");
      assertThat(savedToken.getExpiresAt())
          .isAfterOrEqualTo(Instant.now().plus(CODE_TTL).minusSeconds(5));

      assertThat(savedDispatch.getEventId()).isEqualTo(event.eventId());
      assertThat(savedDispatch.getUserId()).isEqualTo(event.userId());
      assertThat(savedDispatch.getVerificationTokenId()).isEqualTo(savedToken.getId());
      assertThat(savedDispatch.getEmail()).isEqualTo(event.email());
      assertThat(savedDispatch.getPurpose()).isEqualTo(EmailDispatchPurpose.EMAIL_VERIFICATION);
      assertThat(savedDispatch.getReason()).isEqualTo(EmailDispatchReason.REGISTER);
      assertThat(savedDispatch.getStatus()).isEqualTo(EmailDispatchStatus.PENDING);
      assertThat(result.token()).isSameAs(savedToken);
      assertThat(result.dispatch()).isSameAs(savedDispatch);

      verify(verificationTokenRepositoryPort)
          .revokeOpenByUserIdAndEmail(event.userId(), event.email(), savedToken.getCreatedAt());
    }

    @ParameterizedTest
    @EnumSource(EmailDispatchReason.class)
    @DisplayName("Deve criar dispatch do eventId atual ao recuperar token aberto concorrente")
    void shouldCreateCurrentEventDispatchWhenRecoveringWithOpenToken(EmailDispatchReason reason) {
      // 1. Arrange
      var event = event(reason);
      var token = token(UUID.fromString("019a22c5-0987-7af5-88d6-2df3aeb30202"));
      var duplicateOpenTokenException = new DuplicateOpenEmailVerificationTokenException();

      given(emailDispatchRepositoryPort.findByEventId(event.eventId()))
          .willReturn(Optional.empty());
      given(transactionTemplate.execute(any()))
          .willThrow(duplicateOpenTokenException)
          .willAnswer(invocation -> executeTransaction(invocation.getArgument(0)));
      given(verificationTokenRepositoryPort.findOpenByUserIdAndEmail(event.userId(), event.email()))
          .willReturn(Optional.of(token));
      given(emailDispatchRepositoryPort.save(any(EmailDispatch.class)))
          .willAnswer(invocation -> invocation.getArgument(0));

      // 2. Act
      var result = service.findOrCreate(event);

      // 3. Assert
      verify(emailDispatchRepositoryPort).save(dispatchCaptor.capture());
      var savedDispatch = dispatchCaptor.getValue();
      assertThat(savedDispatch.getEventId()).isEqualTo(event.eventId());
      assertThat(savedDispatch.getVerificationTokenId()).isEqualTo(token.getId());
      assertThat(savedDispatch.getReason()).isEqualTo(reason);
      assertThat(result.dispatch()).isSameAs(savedDispatch);
      assertThat(result.token()).isSameAs(token);

      verify(emailDispatchRepositoryPort, never()).findLatestByVerificationTokenId(any());
    }

    @Test
    @DisplayName("Deve falhar quando o token referenciado pelo dispatch não existir")
    void shouldFailWhenExistingDispatchTokenDoesNotExist() {
      // 1. Arrange
      var event = event(EmailDispatchReason.REGISTER);
      var tokenId = UUID.fromString("019a22c5-0987-7af5-88d6-2df3aeb30203");
      var dispatch = dispatch(event, tokenId);

      given(emailDispatchRepositoryPort.findByEventId(event.eventId()))
          .willReturn(Optional.of(dispatch));
      given(verificationTokenRepositoryPort.findById(tokenId)).willReturn(Optional.empty());

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> service.findOrCreate(event));

      // 3. Assert
      throwableAssert.isInstanceOf(EmailVerificationTokenNotFoundException.class);
    }

    @Test
    @DisplayName("Deve recuperar dispatch criado concorrentemente para o mesmo eventId")
    void shouldRecoverDispatchCreatedConcurrentlyForSameEventId() {
      // 1. Arrange
      var event = event(EmailDispatchReason.REGISTER);
      var token = token(UUID.fromString("019a22c5-0987-7af5-88d6-2df3aeb30204"));
      var concurrentDispatch = dispatch(event, token.getId());

      given(emailDispatchRepositoryPort.findByEventId(event.eventId()))
          .willReturn(Optional.empty(), Optional.of(concurrentDispatch));
      given(transactionTemplate.execute(any()))
          .willThrow(new DuplicateEmailDispatchEventException());
      given(verificationTokenRepositoryPort.findById(token.getId())).willReturn(Optional.of(token));

      // 2. Act
      var result = service.findOrCreate(event);

      // 3. Assert
      assertThat(result.dispatch()).isSameAs(concurrentDispatch);
      assertThat(result.token()).isSameAs(token);
    }

    @Test
    @DisplayName(
        "Deve relançar colisão de eventId quando o dispatch concorrente não for encontrado")
    void shouldRethrowDuplicateEventWhenConcurrentDispatchCannotBeFound() {
      // 1. Arrange
      var event = event(EmailDispatchReason.REGISTER);
      var duplicateException = new DuplicateEmailDispatchEventException();

      given(emailDispatchRepositoryPort.findByEventId(event.eventId()))
          .willReturn(Optional.empty());
      given(transactionTemplate.execute(any())).willThrow(duplicateException);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> service.findOrCreate(event));

      // 3. Assert
      throwableAssert.isSameAs(duplicateException);
    }

    @Test
    @DisplayName(
        "Deve relançar colisão de token aberto quando o token concorrente não for encontrado")
    void shouldRethrowDuplicateOpenTokenWhenConcurrentTokenCannotBeFound() {
      // 1. Arrange
      var event = event(EmailDispatchReason.RESEND);
      var duplicateException = new DuplicateOpenEmailVerificationTokenException();

      given(emailDispatchRepositoryPort.findByEventId(event.eventId()))
          .willReturn(Optional.empty());
      given(transactionTemplate.execute(any())).willThrow(duplicateException);
      given(verificationTokenRepositoryPort.findOpenByUserIdAndEmail(event.userId(), event.email()))
          .willReturn(Optional.empty());

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> service.findOrCreate(event));

      // 3. Assert
      throwableAssert.isSameAs(duplicateException);
    }

    @Test
    @DisplayName("Deve recuperar dispatch quando houver colisão após recuperar token aberto")
    void shouldRecoverDispatchAfterCollisionWhileUsingConcurrentOpenToken() {
      // 1. Arrange
      var event = event(EmailDispatchReason.RESEND);
      var token = token(UUID.fromString("019a22c5-0987-7af5-88d6-2df3aeb30205"));
      var concurrentDispatch = dispatch(event, token.getId());

      given(emailDispatchRepositoryPort.findByEventId(event.eventId()))
          .willReturn(Optional.empty(), Optional.of(concurrentDispatch));
      given(transactionTemplate.execute(any()))
          .willThrow(new DuplicateOpenEmailVerificationTokenException())
          .willAnswer(invocation -> executeTransaction(invocation.getArgument(0)));
      given(verificationTokenRepositoryPort.findOpenByUserIdAndEmail(event.userId(), event.email()))
          .willReturn(Optional.of(token));
      given(emailDispatchRepositoryPort.save(any(EmailDispatch.class)))
          .willThrow(new DuplicateEmailDispatchEventException());
      given(verificationTokenRepositoryPort.findById(token.getId())).willReturn(Optional.of(token));

      // 2. Act
      var result = service.findOrCreate(event);

      // 3. Assert
      assertThat(result.dispatch()).isSameAs(concurrentDispatch);
      assertThat(result.token()).isSameAs(token);
    }
  }

  private void executeTransactions() {
    given(transactionTemplate.execute(any()))
        .willAnswer(invocation -> executeTransaction(invocation.getArgument(0)));
  }

  private Object executeTransaction(TransactionCallback<?> callback) {
    return callback.doInTransaction(new SimpleTransactionStatus());
  }

  private EmailVerificationRequestedEvent event(EmailDispatchReason reason) {
    return new EmailVerificationRequestedEvent(
        UUID.fromString("019a22c5-0987-7af5-88d6-2df3aeb30101"),
        UUID.fromString("019a22c5-0987-7af5-88d6-2df3aeb30102"),
        "user@email.com",
        reason,
        NOW);
  }

  private EmailVerificationToken token(UUID tokenId) {
    return EmailVerificationToken.restore(
        tokenId,
        UUID.fromString("019a22c5-0987-7af5-88d6-2df3aeb30102"),
        "user@email.com",
        "verification-code-hash",
        "encrypted-code",
        NOW.plus(CODE_TTL),
        null,
        null,
        0,
        null,
        NOW,
        NOW);
  }

  private EmailDispatch dispatch(EmailVerificationRequestedEvent event, UUID tokenId) {
    return EmailDispatch.restore(
        UUID.fromString("019a22c5-0987-7af5-88d6-2df3aeb30301"),
        event.eventId(),
        event.userId(),
        tokenId,
        event.email(),
        EmailDispatchPurpose.EMAIL_VERIFICATION,
        event.reason(),
        EmailDispatchStatus.PENDING,
        null,
        0,
        null,
        null,
        null,
        null,
        null,
        NOW,
        NOW);
  }
}
