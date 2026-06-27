package com.app.url_shortener.iam.infrastructure.notification.strategy;

import com.app.url_shortener.iam.application.policy.EmailVerificationPolicy;
import com.app.url_shortener.iam.application.port.output.EmailVerificationSenderPort;
import com.app.url_shortener.iam.application.port.output.model.EmailVerificationSendResult;
import com.app.url_shortener.iam.domain.exception.auth.EmailVerificationSendException;
import com.app.url_shortener.iam.infrastructure.config.AwsSesEmailVerificationProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.iam.email-verification.sender", havingValue = "ses")
public class AwsSesEmailVerificationSenderStrategy implements EmailVerificationSenderPort {

  private static final String CHARSET = StandardCharsets.UTF_8.name();

  private final SesClient sesClient;
  private final AwsSesEmailVerificationProperties properties;
  private final EmailVerificationPolicy emailVerificationPolicy;

  @Override
  public EmailVerificationSendResult sendEmailVerificationCode(String email, String code) {
    try {
      SendEmailResponse response = sesClient.sendEmail(buildRequest(email, code));
      String providerMessageId = response.messageId();

      if (providerMessageId == null || providerMessageId.isBlank()) {
        throw new EmailVerificationSendException();
      }

      return new EmailVerificationSendResult(providerMessageId);

    } catch (EmailVerificationSendException exception) {
      throw exception;
    } catch (SesException exception) {
      log.warn(
          "AWS SES rejeitou o envio do e-mail de verificação. awsErrorCode={}, statusCode={}, requestId={}",
          awsErrorCode(exception),
          exception.statusCode(),
          exception.requestId());
      throw new EmailVerificationSendException(exception);
    } catch (SdkClientException exception) {
      log.warn(
          "Falha de comunicação com AWS SES durante envio de e-mail de verificação. failureType={}",
          exception.getClass().getSimpleName());
      throw new EmailVerificationSendException(exception);
    }
  }

  private SendEmailRequest buildRequest(String email, String code) {
    var destination = Destination.builder().toAddresses(List.of(email)).build();
    var message =
        Message.builder()
            .subject(content(properties.subject()))
            .body(
                Body.builder().text(content(textBody(code))).html(content(htmlBody(code))).build())
            .build();

    return SendEmailRequest.builder()
        .source(properties.fromEmail())
        .destination(destination)
        .message(message)
        .build();
  }

  private Content content(String value) {
    return Content.builder().charset(CHARSET).data(value).build();
  }

  private String textBody(String code) {
    return "Seu código de verificação é: "
        + code
        + System.lineSeparator()
        + "O código é válido por "
        + validityText()
        + "."
        + System.lineSeparator()
        + "Se você não solicitou este código, ignore este e-mail.";
  }

  private String htmlBody(String code) {
    return "<p>Seu código de verificação é: <strong>"
        + HtmlUtils.htmlEscape(code)
        + "</strong></p>"
        + "<p>O código é válido por "
        + validityText()
        + ".</p>"
        + "<p>Se você não solicitou este código, ignore este e-mail.</p>";
  }

  private String validityText() {
    Duration codeTtl = emailVerificationPolicy.codeTtl();
    long seconds = codeTtl.toSeconds();

    if (seconds >= 60 && seconds % 60 == 0) {
      long minutes = codeTtl.toMinutes();
      return minutes + (minutes == 1 ? " minuto" : " minutos");
    }

    return seconds + (seconds == 1 ? " segundo" : " segundos");
  }

  private String awsErrorCode(SesException exception) {
    return exception.awsErrorDetails() == null
        ? exception.getClass().getSimpleName()
        : exception.awsErrorDetails().errorCode();
  }
}
