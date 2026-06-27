package com.app.url_shortener.iam.infrastructure.notification.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.url_shortener.config.AbstractIntegrationTest;
import com.app.url_shortener.config.LocalStackContainerSupport;
import com.app.url_shortener.iam.application.port.output.EmailVerificationSenderPort;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@TestPropertySource(properties = "app.iam.email-verification.sender=ses")
@DisplayName("Testes de Integração - Strategy AWS SES para verificação de e-mail")
class AwsSesEmailVerificationSenderStrategyIT extends AbstractIntegrationTest {

  private static final String FROM_EMAIL = "no-reply@url-shortener.local";
  private static final String TO_EMAIL = "user@example.com";
  private static final String CODE = "123456";

  @Autowired private EmailVerificationSenderPort emailVerificationSenderPort;

  @Autowired private ObjectMapper objectMapper;

  @Test
  @DisplayName("Deve enviar e-mail e permitir inspeção da mensagem no LocalStack")
  void shouldSendEmailAndExposeCapturedMessageInLocalStack() throws Exception {
    // 1. Arrange

    // 2. Act
    var result = emailVerificationSenderPort.sendEmailVerificationCode(TO_EMAIL, CODE);
    JsonNode capturedMessage = findCapturedMessage(result.providerMessageId());

    // 3. Assert
    assertThat(capturedMessage).isNotNull();
    assertThat(capturedMessage.get("Source").asString()).isEqualTo(FROM_EMAIL);
    assertThat(capturedMessage.get("Destination").get("ToAddresses").get(0).asString())
        .isEqualTo(TO_EMAIL);
    assertThat(capturedMessage.get("Subject").asString()).isEqualTo("Confirme seu e-mail");
    assertThat(capturedMessage.get("Body").get("text_part").asString())
        .contains(CODE, "10 minutos");
    assertThat(capturedMessage.get("Body").get("html_part").asString())
        .contains("<strong>" + CODE + "</strong>", "10 minutos");
  }

  private JsonNode findCapturedMessage(String providerMessageId) throws Exception {
    String encodedFromEmail = URLEncoder.encode(FROM_EMAIL, StandardCharsets.UTF_8);
    URI endpoint = LocalStackContainerSupport.sesEndpoint();
    var request =
        HttpRequest.newBuilder(endpoint.resolve("/_aws/ses?email=" + encodedFromEmail))
            .GET()
            .build();

    try (var httpClient = HttpClient.newHttpClient()) {
      var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(200);

      JsonNode messages = objectMapper.readTree(response.body()).get("messages");
      for (JsonNode message : messages) {
        if (providerMessageId.equals(message.get("Id").asString())) {
          return message;
        }
      }
      return null;
    }
  }
}
