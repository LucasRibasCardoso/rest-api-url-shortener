package com.app.url_shortener.url.application.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.url.application.command.ShortenUrlCommand;
import com.app.url_shortener.url.application.port.output.CheckUrlRateLimitPort;
import com.app.url_shortener.url.application.port.output.IdGeneratorPort;
import com.app.url_shortener.url.application.port.output.RedirectCachePort;
import com.app.url_shortener.url.application.port.output.UrlEncoderPort;
import com.app.url_shortener.url.application.port.output.UrlRepositoryPort;
import com.app.url_shortener.url.application.validation.UrlSafetyValidator;
import com.app.url_shortener.url.domain.exception.RedirectCacheException;
import com.app.url_shortener.url.domain.exception.UnsafeUrlException;
import com.app.url_shortener.url.domain.model.Url;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Caso de Uso de Encurtamento de URL")
class ShortenUrlUseCaseImplTest {

  @Mock private IdGeneratorPort idGeneratorPort;

  @Mock private UrlEncoderPort urlEncoderPort;

  @Mock private UrlRepositoryPort urlRepositoryPort;

  @Mock private RedirectCachePort redirectCachePort;

  @Mock private CheckUrlRateLimitPort checkUrlRateLimitPort;

  @Mock private UrlSafetyValidator urlSafetyValidator;

  @InjectMocks private ShortenUrlUseCaseImpl shortenUrlUseCase;

  @Nested
  @DisplayName("Execução")
  class ExecuteTests {

    @Test
    @DisplayName(
        "Deve gerar código curto, persistir, salvar cache ativo e retornar a URL encurtada")
    void shouldGenerateShortCodeSaveActiveCacheAndReturnShortenedUrl() {
      // 1. Arrange
      var generatedId = 100L;
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var originalUrl = "https://google.com";
      var planType = PlanType.FREE;
      var command = new ShortenUrlCommand(userId, originalUrl, planType);
      var shortCode = "aB3dE";
      when(idGeneratorPort.generateId()).thenReturn(generatedId);
      when(urlEncoderPort.encode(generatedId)).thenReturn(shortCode);

      // 2. Act
      var result = shortenUrlUseCase.execute(command);

      // 3. Assert
      assertThat(result.shortCode()).isEqualTo(shortCode);
      assertThat(result.originalUrl()).isEqualTo(originalUrl);
      assertThat(result.createdAt()).isNotNull();

      var urlCaptor = ArgumentCaptor.forClass(Url.class);
      verify(idGeneratorPort).generateId();
      verify(urlEncoderPort).encode(generatedId);
      verify(urlRepositoryPort).save(urlCaptor.capture());
      verify(redirectCachePort).saveActive(shortCode, originalUrl);
      verify(urlSafetyValidator).validate(originalUrl);

      var capturedUrl = urlCaptor.getValue();
      assertThat(capturedUrl.getUserId()).isEqualTo(userId);
      assertThat(capturedUrl.getShortCode()).isEqualTo(shortCode);
      assertThat(capturedUrl.getOriginalUrl()).isEqualTo(originalUrl);
      assertThat(capturedUrl.getCreatedAt()).isNotNull();

      InOrder inOrder =
          inOrder(
              urlSafetyValidator,
              checkUrlRateLimitPort,
              idGeneratorPort,
              urlEncoderPort,
              urlRepositoryPort,
              redirectCachePort);
      inOrder.verify(urlSafetyValidator).validate(originalUrl);
      inOrder.verify(checkUrlRateLimitPort).checkShorten(userId, planType);
      inOrder.verify(idGeneratorPort).generateId();
      inOrder.verify(urlEncoderPort).encode(generatedId);
      inOrder.verify(urlRepositoryPort).save(capturedUrl);
      inOrder.verify(redirectCachePort).saveActive(shortCode, originalUrl);

      verifyNoMoreInteractions(
          urlSafetyValidator,
          checkUrlRateLimitPort,
          idGeneratorPort,
          urlEncoderPort,
          urlRepositoryPort,
          redirectCachePort);
    }

    @Test
    @DisplayName("Deve retornar URL encurtada quando escrita no cache de redirecionamento falhar")
    void shouldReturnShortenedUrlWhenRedirectCacheWriteFails() {
      // 1. Arrange
      var generatedId = 100L;
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var originalUrl = "https://google.com";
      var planType = PlanType.FREE;
      var command = new ShortenUrlCommand(userId, originalUrl, planType);
      var shortCode = "aB3dE";
      var exception = new RedirectCacheException(new RuntimeException("Redis unavailable"));
      when(idGeneratorPort.generateId()).thenReturn(generatedId);
      when(urlEncoderPort.encode(generatedId)).thenReturn(shortCode);
      doThrow(exception).when(redirectCachePort).saveActive(shortCode, originalUrl);

      // 2. Act
      var result = shortenUrlUseCase.execute(command);

      // 3. Assert
      assertThat(result.shortCode()).isEqualTo(shortCode);
      assertThat(result.originalUrl()).isEqualTo(originalUrl);
      assertThat(result.createdAt()).isNotNull();

      var urlCaptor = ArgumentCaptor.forClass(Url.class);
      verify(urlSafetyValidator).validate(originalUrl);
      verify(checkUrlRateLimitPort).checkShorten(userId, planType);
      verify(idGeneratorPort).generateId();
      verify(urlEncoderPort).encode(generatedId);
      verify(urlRepositoryPort).save(urlCaptor.capture());
      verify(redirectCachePort).saveActive(shortCode, originalUrl);

      var capturedUrl = urlCaptor.getValue();
      assertThat(capturedUrl.getUserId()).isEqualTo(userId);
      assertThat(capturedUrl.getShortCode()).isEqualTo(shortCode);
      assertThat(capturedUrl.getOriginalUrl()).isEqualTo(originalUrl);

      verifyNoMoreInteractions(
          urlSafetyValidator,
          checkUrlRateLimitPort,
          idGeneratorPort,
          urlEncoderPort,
          urlRepositoryPort,
          redirectCachePort);
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
      verifyNoInteractions(idGeneratorPort, urlEncoderPort, urlRepositoryPort, redirectCachePort);
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
          idGeneratorPort,
          urlEncoderPort,
          urlRepositoryPort,
          redirectCachePort);
      verifyNoMoreInteractions(urlSafetyValidator);
    }
  }
}
