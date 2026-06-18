package com.app.url_shortener.iam.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.url_shortener.iam.domain.exception.auth.DuplicateEmailDispatchEventException;
import com.app.url_shortener.iam.domain.exception.auth.DuplicateOpenEmailVerificationTokenException;
import com.app.url_shortener.iam.domain.exception.auth.EmailDispatchAlreadyProcessingException;
import com.app.url_shortener.iam.domain.exception.auth.EmailDispatchStateConflictException;
import com.app.url_shortener.iam.domain.exception.auth.EmailVerificationTokenNotFoundException;
import com.app.url_shortener.iam.domain.exception.auth.VerificationCodeProtectionException;
import com.app.url_shortener.shared.exception.conflict.ConflictException;
import com.app.url_shortener.shared.exception.internalservererror.InternalServerErrorException;
import com.app.url_shortener.shared.exception.notfound.NotFoundException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Testes de Unidade - IamErrorCode")
class IamErrorCodeTest {

  @Nested
  @DisplayName("Mensagens")
  class MessageTests {

    @Test
    @DisplayName("Deve expor mensagens padronizadas em português")
    void shouldExposeStandardPortugueseMessages() {
      // 1. Arrange
      Map<IamErrorCode, String> messages =
          Map.ofEntries(
              Map.entry(IamErrorCode.AUTH_DEFAULT_ROLE_NOT_FOUND, "Permissão padrão não encontrada."),
              Map.entry(IamErrorCode.AUTH_INVALID_CREDENTIALS, "Credenciais inválidas."),
              Map.entry(IamErrorCode.AUTH_ACCOUNT_PENDING_VERIFICATION, "Conta pendente de verificação."),
              Map.entry(IamErrorCode.AUTH_ACCOUNT_LOCKED, "Conta bloqueada."),
              Map.entry(IamErrorCode.AUTH_REFRESH_TOKEN_INVALID, "Refresh token inválido."),
              Map.entry(IamErrorCode.AUTH_REFRESH_TOKEN_EXPIRED, "Refresh token expirado."),
              Map.entry(IamErrorCode.AUTH_REFRESH_TOKEN_COMPROMISED, "Refresh token comprometido."),
              Map.entry(IamErrorCode.AUTH_EMAIL_ALREADY_EXISTS, "Email já cadastrado."),
              Map.entry(IamErrorCode.AUTH_INVALID_VERIFICATION_CODE, "Código de verificação inválido."),
              Map.entry(
                  IamErrorCode.AUTH_INVALID_OR_EXPIRED_VERIFICATION_CODE,
                  "Código de verificação inválido ou expirado."),
              Map.entry(
                  IamErrorCode.AUTH_INVALID_EMAIL_VERIFICATION_EVENT,
                  "Evento de verificação de e-mail inválido."),
              Map.entry(IamErrorCode.AUTH_USER_NOT_FOUND, "Usuário não encontrado."),
              Map.entry(IamErrorCode.USER_ACCOUNT_DISABLED, "Conta desabilitada."),
              Map.entry(
                  IamErrorCode.AUTH_DUPLICATE_EMAIL_DISPATCH_EVENT,
                  "Evento de disparo de e-mail já processado."),
              Map.entry(
                  IamErrorCode.AUTH_EMAIL_DISPATCH_ALREADY_PROCESSING,
                  "E-mail de verificação já em processamento."),
              Map.entry(
                  IamErrorCode.AUTH_EMAIL_DISPATCH_STATE_CONFLICT,
                  "Estado do disparo de e-mail em conflito para processamento."),
              Map.entry(
                  IamErrorCode.AUTH_EMAIL_VERIFICATION_SEND_FAILED,
                  "Falha ao enviar e-mail de verificação."),
              Map.entry(
                  IamErrorCode.AUTH_VERIFICATION_CODE_HASH_FAILED,
                  "Falha ao gerar hash do código de verificação."),
              Map.entry(
                  IamErrorCode.AUTH_VERIFICATION_CODE_ENCRYPT_FAILED,
                  "Falha ao criptografar código de verificação."),
              Map.entry(
                  IamErrorCode.AUTH_VERIFICATION_CODE_DECRYPT_FAILED,
                  "Falha ao descriptografar código de verificação."),
              Map.entry(
                  IamErrorCode.AUTH_ENCRYPTED_VERIFICATION_CODE_INVALID,
                  "Código de verificação criptografado inválido."),
              Map.entry(IamErrorCode.AUTH_VERIFICATION_CODE_INVALID, "Código de verificação inválido."),
              Map.entry(
                  IamErrorCode.AUTH_EMAIL_VERIFICATION_TOKEN_NOT_FOUND,
                  "Token de verificação de e-mail não encontrado."),
              Map.entry(
                  IamErrorCode.AUTH_DUPLICATE_OPEN_EMAIL_VERIFICATION_TOKEN,
                  "Já existe um token de verificação de e-mail aberto para este usuário."));

      // 2. Act
      var errorCodes = IamErrorCode.values();

      // 3. Assert
      for (IamErrorCode errorCode : errorCodes) {
        assertThat(errorCode.getMessage()).isEqualTo(messages.get(errorCode));
      }
    }
  }

  @Nested
  @DisplayName("Exceções de verificação de email")
  class EmailVerificationExceptionTests {

    @Test
    @DisplayName("Deve mapear exceções de concorrência para conflito")
    void shouldMapConcurrencyExceptionsToConflict() {
      // 1. Arrange
      var duplicateDispatch = new DuplicateEmailDispatchEventException();
      var duplicateToken = new DuplicateOpenEmailVerificationTokenException();
      var alreadyProcessing = new EmailDispatchAlreadyProcessingException();
      var stateConflict = new EmailDispatchStateConflictException();

      // 2. Act

      // 3. Assert
      assertThat(duplicateDispatch).isInstanceOf(ConflictException.class);
      assertThat(duplicateDispatch.getErrorCode())
          .isEqualTo(IamErrorCode.AUTH_DUPLICATE_EMAIL_DISPATCH_EVENT);
      assertThat(duplicateToken.getErrorCode())
          .isEqualTo(IamErrorCode.AUTH_DUPLICATE_OPEN_EMAIL_VERIFICATION_TOKEN);
      assertThat(alreadyProcessing.getErrorCode())
          .isEqualTo(IamErrorCode.AUTH_EMAIL_DISPATCH_ALREADY_PROCESSING);
      assertThat(stateConflict.getErrorCode())
          .isEqualTo(IamErrorCode.AUTH_EMAIL_DISPATCH_STATE_CONFLICT);
    }

    @Test
    @DisplayName("Deve mapear token ausente para não encontrado")
    void shouldMapMissingTokenToNotFound() {
      // 1. Arrange
      var exception = new EmailVerificationTokenNotFoundException();

      // 2. Act

      // 3. Assert
      assertThat(exception).isInstanceOf(NotFoundException.class);
      assertThat(exception.getErrorCode()).isEqualTo(IamErrorCode.AUTH_EMAIL_VERIFICATION_TOKEN_NOT_FOUND);
    }

    @Test
    @DisplayName("Deve mapear falha de proteção do código para erro interno")
    void shouldMapCodeProtectionFailureToInternalServerError() {
      // 1. Arrange
      var exception =
          new VerificationCodeProtectionException(IamErrorCode.AUTH_VERIFICATION_CODE_DECRYPT_FAILED);

      // 2. Act

      // 3. Assert
      assertThat(exception).isInstanceOf(InternalServerErrorException.class);
      assertThat(exception.getErrorCode()).isEqualTo(IamErrorCode.AUTH_VERIFICATION_CODE_DECRYPT_FAILED);
    }
  }
}
