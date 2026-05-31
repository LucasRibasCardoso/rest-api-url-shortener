package com.app.url_shortener.url.application.usecase.impl;

import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.url.application.command.ShortenUrlCommand;
import com.app.url_shortener.url.application.port.output.CheckUrlRateLimitPort;
import com.app.url_shortener.url.application.port.output.IdGeneratorPort;
import com.app.url_shortener.url.application.port.output.UrlEncoderPort;
import com.app.url_shortener.url.application.port.output.UrlRepositoryPort;
import com.app.url_shortener.url.domain.model.Url;
import com.app.url_shortener.url.application.validation.UrlSafetyValidator;
import com.app.url_shortener.url.domain.exception.UnsafeUrlException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Caso de Uso de Encurtamento de URL")
class ShortenUrlUseCaseImplTest {

  @Mock
  private IdGeneratorPort idGeneratorService;

  @Mock
  private UrlEncoderPort urlEncoderPort;

  @Mock
  private UrlRepositoryPort urlRepositoryPort;

  @Mock
  private CheckUrlRateLimitPort checkUrlRateLimitPort;

  @Mock
  private UrlSafetyValidator urlSafetyValidator;

  @InjectMocks
  private ShortenUrlUseCaseImpl shortenUrlUseCase;

  @Nested
  @DisplayName("Execução")
  class ExecuteTests {

    @Test
    @DisplayName("Deve gerar código curto, persistir e retornar a URL encurtada")
    void shouldGenerateShortCodeSaveAndReturnShortenedUrl() {
      // 1. Arrange
      var generatedId = 100L;
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var originalUrl = "https://google.com";
      var planType = PlanType.FREE;
      var command = new ShortenUrlCommand(userId, originalUrl, planType);
      var shortCode = "aB3dE";
      when(idGeneratorService.generateId()).thenReturn(generatedId);
      when(urlEncoderPort.encode(generatedId)).thenReturn(shortCode);

      // 2. Act
      var result = shortenUrlUseCase.execute(command);

      // 3. Assert
      assertThat(result.shortCode()).isEqualTo(shortCode);
      assertThat(result.originalUrl()).isEqualTo(originalUrl);
      assertThat(result.createdAt()).isNotNull();

      var urlCaptor = ArgumentCaptor.forClass(Url.class);
      verify(idGeneratorService).generateId();
      verify(urlEncoderPort).encode(generatedId);
      verify(urlRepositoryPort).save(urlCaptor.capture());
      verify(urlSafetyValidator).validate(originalUrl);

      var capturedUrl = urlCaptor.getValue();
      assertThat(capturedUrl.getUserId()).isEqualTo(userId);
      assertThat(capturedUrl.getShortCode()).isEqualTo(shortCode);
      assertThat(capturedUrl.getOriginalUrl()).isEqualTo(originalUrl);
      assertThat(capturedUrl.getCreatedAt()).isNotNull();

      InOrder inOrder = inOrder(
          urlSafetyValidator,
          checkUrlRateLimitPort,
          idGeneratorService,
          urlEncoderPort,
          urlRepositoryPort
      );
      inOrder.verify(urlSafetyValidator).validate(originalUrl);
      inOrder.verify(checkUrlRateLimitPort).checkShorten(userId, planType);
      inOrder.verify(idGeneratorService).generateId();
      inOrder.verify(urlEncoderPort).encode(generatedId);
      inOrder.verify(urlRepositoryPort).save(capturedUrl);

      verifyNoMoreInteractions(
          urlSafetyValidator,
          checkUrlRateLimitPort,
          idGeneratorService,
          urlEncoderPort,
          urlRepositoryPort
      );
    }

    @Test
    @DisplayName("Deve propagar rate limit e não persistir URL quando encurtamento for negado")
    void shouldPropagateRateLimitAndNotPersistUrlWhenShortenIsDenied() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var originalUrl = "https://google.com";
      var planType = PlanType.PREMIUM;
      var command = new ShortenUrlCommand(userId, originalUrl, planType);
      var exception = new RuntimeException("Rate limit excedido.");

      doThrow(exception).when(checkUrlRateLimitPort).checkShorten(userId, planType);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> shortenUrlUseCase.execute(command));

      // 3. Assert
      throwableAssert.isSameAs(exception);

      verify(urlSafetyValidator).validate(originalUrl);
      verify(checkUrlRateLimitPort).checkShorten(userId, planType);
      verifyNoInteractions(idGeneratorService, urlEncoderPort, urlRepositoryPort);
      verifyNoMoreInteractions(urlSafetyValidator, checkUrlRateLimitPort);
    }

    @Test
    @DisplayName("Deve propagar URL insegura e não executar rate limit nem persistência")
    void shouldPropagateUnsafeUrlAndNotExecuteRateLimitOrPersistence() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var originalUrl = "http://localhost";
      var planType = PlanType.FREE;
      var command = new ShortenUrlCommand(userId, originalUrl, planType);
      var exception = new UnsafeUrlException();

      doThrow(exception).when(urlSafetyValidator).validate(originalUrl);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> shortenUrlUseCase.execute(command));

      // 3. Assert
      throwableAssert.isSameAs(exception);

      verify(urlSafetyValidator).validate(originalUrl);
      verifyNoInteractions(
          checkUrlRateLimitPort,
          idGeneratorService,
          urlEncoderPort,
          urlRepositoryPort
      );
      verifyNoMoreInteractions(urlSafetyValidator);
    }
  }
}
