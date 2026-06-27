package com.app.url_shortener.iam.infrastructure.notification.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.app.url_shortener.iam.application.policy.EmailVerificationPolicy;
import com.app.url_shortener.iam.domain.exception.IamErrorCode;
import com.app.url_shortener.iam.domain.exception.auth.EmailVerificationSendException;
import com.app.url_shortener.iam.infrastructure.config.AwsSesEmailVerificationProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;
import software.amazon.awssdk.services.ses.model.SesException;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Strategy AWS SES para verificação de e-mail")
class AwsSesEmailVerificationSenderStrategyTest {

  private static final String FROM_EMAIL = "no-reply@example.com";
  private static final String TO_EMAIL = "user@example.com";
  private static final String SUBJECT = "Confirme seu e-mail";
  private static final String CODE = "123456";

  @Mock private SesClient sesClient;

  private AwsSesEmailVerificationSenderStrategy strategy;

  @BeforeEach
  void setUp() {
    var properties =
        new AwsSesEmailVerificationProperties(null, FROM_EMAIL, SUBJECT, Duration.ofSeconds(10));
    var policy =
        new EmailVerificationPolicy(
            Duration.ofMinutes(10), Duration.ofMinutes(1), Duration.ofSeconds(30));
    strategy = new AwsSesEmailVerificationSenderStrategy(sesClient, properties, policy);
  }

  @Nested
  @DisplayName("Envio")
  class SendTests {

    @Test
    @DisplayName("Deve enviar conteúdo textual e HTML e retornar o MessageId")
    void shouldSendTextAndHtmlContentAndReturnMessageId() {
      // 1. Arrange
      given(sesClient.sendEmail(any(SendEmailRequest.class)))
          .willReturn(SendEmailResponse.builder().messageId("provider-message-id").build());
      var requestCaptor = ArgumentCaptor.forClass(SendEmailRequest.class);

      // 2. Act
      var result = strategy.sendEmailVerificationCode(TO_EMAIL, CODE);

      // 3. Assert
      assertThat(result.providerMessageId()).isEqualTo("provider-message-id");
      verify(sesClient).sendEmail(requestCaptor.capture());

      var request = requestCaptor.getValue();
      var message = request.message();
      assertThat(request.source()).isEqualTo(FROM_EMAIL);
      assertThat(request.destination().toAddresses()).containsExactly(TO_EMAIL);
      assertThat(message.subject().data()).isEqualTo(SUBJECT);
      assertThat(message.subject().charset()).isEqualTo("UTF-8");
      assertThat(message.body().text().data()).contains(CODE, "10 minutos");
      assertThat(message.body().text().charset()).isEqualTo("UTF-8");
      assertThat(message.body().html().data())
          .contains("<strong>" + CODE + "</strong>", "10 minutos");
      assertThat(message.body().html().charset()).isEqualTo("UTF-8");
    }

    @Test
    @DisplayName("Deve rejeitar resposta sem MessageId")
    void shouldRejectResponseWithoutMessageId() {
      // 1. Arrange
      given(sesClient.sendEmail(any(SendEmailRequest.class)))
          .willReturn(SendEmailResponse.builder().messageId(" ").build());

      // 2. Act
      var throwableAssert =
          assertThatThrownBy(() -> strategy.sendEmailVerificationCode(TO_EMAIL, CODE));

      // 3. Assert
      throwableAssert
          .isInstanceOf(EmailVerificationSendException.class)
          .hasMessage(IamErrorCode.AUTH_EMAIL_VERIFICATION_SEND_FAILED.getMessage());
    }

    @Test
    @DisplayName("Deve encapsular rejeição retornada pelo AWS SES")
    void shouldWrapAwsSesRejection() {
      // 1. Arrange
      var cause =
          SesException.builder()
              .message("message rejected")
              .statusCode(400)
              .requestId("request-id")
              .awsErrorDetails(AwsErrorDetails.builder().errorCode("MessageRejected").build())
              .build();
      given(sesClient.sendEmail(any(SendEmailRequest.class))).willThrow(cause);

      // 2. Act
      var throwableAssert =
          assertThatThrownBy(() -> strategy.sendEmailVerificationCode(TO_EMAIL, CODE));

      // 3. Assert
      throwableAssert
          .isInstanceOf(EmailVerificationSendException.class)
          .hasCause(cause)
          .hasMessage(IamErrorCode.AUTH_EMAIL_VERIFICATION_SEND_FAILED.getMessage());
    }

    @Test
    @DisplayName("Deve encapsular falha de comunicação com AWS SES")
    void shouldWrapAwsSdkClientFailure() {
      // 1. Arrange
      var cause = SdkClientException.builder().message("network failure").build();
      given(sesClient.sendEmail(any(SendEmailRequest.class))).willThrow(cause);

      // 2. Act
      var throwableAssert =
          assertThatThrownBy(() -> strategy.sendEmailVerificationCode(TO_EMAIL, CODE));

      // 3. Assert
      throwableAssert
          .isInstanceOf(EmailVerificationSendException.class)
          .hasCause(cause)
          .hasMessage(IamErrorCode.AUTH_EMAIL_VERIFICATION_SEND_FAILED.getMessage());
    }
  }
}
