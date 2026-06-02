package com.app.url_shortener.url.application.usecase.impl;

import com.app.url_shortener.url.application.command.ResolveUrlCommand;
import com.app.url_shortener.url.application.port.output.RedirectCachePort;
import com.app.url_shortener.url.application.port.output.UrlRepositoryPort;
import com.app.url_shortener.url.application.result.RedirectCacheStatus;
import com.app.url_shortener.url.application.result.UrlRedirectCacheEntry;
import com.app.url_shortener.url.domain.exception.RedirectCacheException;
import com.app.url_shortener.url.domain.exception.UrlNotFoundException;
import com.app.url_shortener.url.domain.model.Url;
import com.app.url_shortener.url.domain.model.UrlStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Caso de Uso de Resolução de URL")
class ResolveUrlUseCaseImplTest {

  @Mock
  private RedirectCachePort redirectCachePort;

  @Mock
  private UrlRepositoryPort urlRepositoryPort;

  @InjectMocks
  private ResolveUrlUseCaseImpl resolveUrlUseCase;

  @Nested
  @DisplayName("Execução")
  class ExecuteTests {

    @Test
    @DisplayName("Deve retornar a URL original quando o cache possuir entrada ativa")
    void shouldReturnOriginalUrlWhenCacheHasActiveEntry() {
      // 1. Arrange
      var shortCode = "aB3dE";
      var command = new ResolveUrlCommand(shortCode);
      var originalUrl = "https://google.com";
      var cacheEntry = new UrlRedirectCacheEntry(RedirectCacheStatus.ACTIVE, originalUrl);
      when(redirectCachePort.findByShortCode(shortCode)).thenReturn(Optional.of(cacheEntry));

      // 2. Act
      var result = resolveUrlUseCase.execute(command);

      // 3. Assert
      assertThat(result.originalUrl()).isEqualTo(originalUrl);
      verify(redirectCachePort).findByShortCode(shortCode);
      verifyNoMoreInteractions(redirectCachePort);
      verifyNoInteractions(urlRepositoryPort);
    }

    @Test
    @DisplayName("Deve retornar a URL original do DynamoDB e salvar cache ativo em cache miss")
    void shouldReturnOriginalUrlFromDynamoDbAndSaveActiveCacheOnCacheMiss() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var shortCode = "aB3dE";
      var command = new ResolveUrlCommand(shortCode);
      var originalUrl = "https://google.com";
      var url = Url.create(userId, shortCode, originalUrl);
      when(redirectCachePort.findByShortCode(shortCode)).thenReturn(Optional.empty());
      when(urlRepositoryPort.findByShortCode(shortCode)).thenReturn(Optional.of(url));
      when(redirectCachePort.saveActiveIfAbsent(shortCode, originalUrl)).thenReturn(true);

      // 2. Act
      var result = resolveUrlUseCase.execute(command);

      // 3. Assert
      assertThat(result.originalUrl()).isEqualTo(originalUrl);
      verify(redirectCachePort).findByShortCode(shortCode);
      verify(urlRepositoryPort).findByShortCode(shortCode);
      verify(redirectCachePort).saveActiveIfAbsent(shortCode, originalUrl);
      verifyNoMoreInteractions(redirectCachePort, urlRepositoryPort);
    }

    @Test
    @DisplayName("Deve salvar NOT_FOUND no cache quando o código curto não existir no DynamoDB")
    void shouldSaveNotFoundCacheWhenShortCodeDoesNotExistInDynamoDb() {
      // 1. Arrange
      var shortCode = "invalid";
      var command = new ResolveUrlCommand(shortCode);
      when(redirectCachePort.findByShortCode(shortCode)).thenReturn(Optional.empty());
      when(urlRepositoryPort.findByShortCode(shortCode)).thenReturn(Optional.empty());

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> resolveUrlUseCase.execute(command))
          .isInstanceOf(UrlNotFoundException.class);
      verify(redirectCachePort).findByShortCode(shortCode);
      verify(urlRepositoryPort).findByShortCode(shortCode);
      verify(redirectCachePort).saveNotFoundIfAbsent(shortCode);
      verifyNoMoreInteractions(redirectCachePort, urlRepositoryPort);
    }

    @Test
    @DisplayName("Deve lançar UrlNotFoundException quando o cache possuir entrada deletada")
    void shouldThrowUrlNotFoundExceptionWhenCacheHasDeletedEntry() {
      // 1. Arrange
      var shortCode = "aB3dE";
      var command = new ResolveUrlCommand(shortCode);
      var cacheEntry = new UrlRedirectCacheEntry(RedirectCacheStatus.DELETED, null);
      when(redirectCachePort.findByShortCode(shortCode)).thenReturn(Optional.of(cacheEntry));

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> resolveUrlUseCase.execute(command))
          .isInstanceOf(UrlNotFoundException.class);
      verify(redirectCachePort).findByShortCode(shortCode);
      verifyNoMoreInteractions(redirectCachePort);
      verifyNoInteractions(urlRepositoryPort);
    }

    @Test
    @DisplayName("Deve lançar UrlNotFoundException quando o cache possuir entrada NOT_FOUND")
    void shouldThrowUrlNotFoundExceptionWhenCacheHasNotFoundEntry() {
      // 1. Arrange
      var shortCode = "aB3dE";
      var command = new ResolveUrlCommand(shortCode);
      var cacheEntry = new UrlRedirectCacheEntry(RedirectCacheStatus.NOT_FOUND, null);
      when(redirectCachePort.findByShortCode(shortCode)).thenReturn(Optional.of(cacheEntry));

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> resolveUrlUseCase.execute(command))
          .isInstanceOf(UrlNotFoundException.class);
      verify(redirectCachePort).findByShortCode(shortCode);
      verifyNoMoreInteractions(redirectCachePort);
      verifyNoInteractions(urlRepositoryPort);
    }

    @Test
    @DisplayName("Deve salvar DELETED no cache quando DynamoDB retornar URL não redirecionável")
    void shouldSaveDeletedCacheWhenDynamoDbReturnsNonRedirectableUrl() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var deletedBy = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac002");
      var now = Instant.parse("2026-01-01T00:00:00Z");
      var shortCode = "aB3dE";
      var originalUrl = "https://google.com";
      var command = new ResolveUrlCommand(shortCode);
      var url =
          Url.restore(
              userId,
              shortCode,
              originalUrl,
              now.minusSeconds(60),
              UrlStatus.DELETED,
              now,
              deletedBy,
              now);
      when(redirectCachePort.findByShortCode(shortCode)).thenReturn(Optional.empty());
      when(urlRepositoryPort.findByShortCode(shortCode)).thenReturn(Optional.of(url));

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> resolveUrlUseCase.execute(command))
          .isInstanceOf(UrlNotFoundException.class);
      verify(redirectCachePort).findByShortCode(shortCode);
      verify(urlRepositoryPort).findByShortCode(shortCode);
      verify(redirectCachePort).saveDeleted(shortCode);
      verifyNoMoreInteractions(redirectCachePort, urlRepositoryPort);
    }

    @Test
    @DisplayName("Deve reler cache e retornar ACTIVE quando saveActiveIfAbsent falhar")
    void shouldReloadCacheAndReturnActiveWhenSaveActiveIfAbsentFails() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var shortCode = "aB3dE";
      var command = new ResolveUrlCommand(shortCode);
      var dynamoUrl = Url.create(userId, shortCode, "https://google.com");
      var cachedEntry = new UrlRedirectCacheEntry(RedirectCacheStatus.ACTIVE, "https://example.com");
      when(redirectCachePort.findByShortCode(shortCode))
          .thenReturn(Optional.empty())
          .thenReturn(Optional.of(cachedEntry));
      when(urlRepositoryPort.findByShortCode(shortCode)).thenReturn(Optional.of(dynamoUrl));
      when(redirectCachePort.saveActiveIfAbsent(shortCode, dynamoUrl.getOriginalUrl()))
          .thenReturn(false);

      // 2. Act
      var result = resolveUrlUseCase.execute(command);

      // 3. Assert
      assertThat(result.originalUrl()).isEqualTo(cachedEntry.longUrl());
      verify(redirectCachePort, org.mockito.Mockito.times(2)).findByShortCode(shortCode);
      verify(urlRepositoryPort).findByShortCode(shortCode);
      verify(redirectCachePort).saveActiveIfAbsent(shortCode, dynamoUrl.getOriginalUrl());
      verifyNoMoreInteractions(redirectCachePort, urlRepositoryPort);
    }

    @Test
    @DisplayName("Deve lançar UrlNotFoundException quando releitura do cache encontrar DELETED")
    void shouldThrowUrlNotFoundExceptionWhenReloadedCacheHasDeletedEntry() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var shortCode = "aB3dE";
      var command = new ResolveUrlCommand(shortCode);
      var url = Url.create(userId, shortCode, "https://google.com");
      var deletedEntry = new UrlRedirectCacheEntry(RedirectCacheStatus.DELETED, null);
      when(redirectCachePort.findByShortCode(shortCode))
          .thenReturn(Optional.empty())
          .thenReturn(Optional.of(deletedEntry));
      when(urlRepositoryPort.findByShortCode(shortCode)).thenReturn(Optional.of(url));
      when(redirectCachePort.saveActiveIfAbsent(shortCode, url.getOriginalUrl())).thenReturn(false);

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> resolveUrlUseCase.execute(command))
          .isInstanceOf(UrlNotFoundException.class);
      verify(redirectCachePort, org.mockito.Mockito.times(2)).findByShortCode(shortCode);
      verify(urlRepositoryPort).findByShortCode(shortCode);
      verify(redirectCachePort).saveActiveIfAbsent(shortCode, url.getOriginalUrl());
      verifyNoMoreInteractions(redirectCachePort, urlRepositoryPort);
    }

    @Test
    @DisplayName("Deve retornar URL do DynamoDB quando releitura do cache continuar ausente")
    void shouldReturnDynamoDbUrlWhenReloadedCacheIsStillMissing() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var shortCode = "aB3dE";
      var command = new ResolveUrlCommand(shortCode);
      var originalUrl = "https://google.com";
      var url = Url.create(userId, shortCode, originalUrl);
      when(redirectCachePort.findByShortCode(shortCode))
          .thenReturn(Optional.empty())
          .thenReturn(Optional.empty());
      when(urlRepositoryPort.findByShortCode(shortCode)).thenReturn(Optional.of(url));
      when(redirectCachePort.saveActiveIfAbsent(shortCode, originalUrl)).thenReturn(false);

      // 2. Act
      var result = resolveUrlUseCase.execute(command);

      // 3. Assert
      assertThat(result.originalUrl()).isEqualTo(originalUrl);
      verify(redirectCachePort, org.mockito.Mockito.times(2)).findByShortCode(shortCode);
      verify(urlRepositoryPort).findByShortCode(shortCode);
      verify(redirectCachePort).saveActiveIfAbsent(shortCode, originalUrl);
      verifyNoMoreInteractions(redirectCachePort, urlRepositoryPort);
    }

    @Test
    @DisplayName("Deve retornar URL do DynamoDB quando releitura do cache falhar")
    void shouldReturnDynamoDbUrlWhenReloadedCacheFails() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var shortCode = "aB3dE";
      var command = new ResolveUrlCommand(shortCode);
      var originalUrl = "https://google.com";
      var url = Url.create(userId, shortCode, originalUrl);
      when(redirectCachePort.findByShortCode(shortCode))
          .thenReturn(Optional.empty())
          .thenThrow(new RedirectCacheException(new RuntimeException("Redis unavailable")));
      when(urlRepositoryPort.findByShortCode(shortCode)).thenReturn(Optional.of(url));
      when(redirectCachePort.saveActiveIfAbsent(shortCode, originalUrl)).thenReturn(false);

      // 2. Act
      var result = resolveUrlUseCase.execute(command);

      // 3. Assert
      assertThat(result.originalUrl()).isEqualTo(originalUrl);
      verify(redirectCachePort, org.mockito.Mockito.times(2)).findByShortCode(shortCode);
      verify(urlRepositoryPort).findByShortCode(shortCode);
      verify(redirectCachePort).saveActiveIfAbsent(shortCode, originalUrl);
      verifyNoMoreInteractions(redirectCachePort, urlRepositoryPort);
    }

    @Test
    @DisplayName("Deve buscar no DynamoDB quando leitura do cache falhar")
    void shouldFallbackToDynamoDbWhenCacheReadFails() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var shortCode = "aB3dE";
      var command = new ResolveUrlCommand(shortCode);
      var originalUrl = "https://google.com";
      var url = Url.create(userId, shortCode, originalUrl);
      when(redirectCachePort.findByShortCode(shortCode))
          .thenThrow(new RedirectCacheException(new RuntimeException("Redis unavailable")));
      when(urlRepositoryPort.findByShortCode(shortCode)).thenReturn(Optional.of(url));
      when(redirectCachePort.saveActiveIfAbsent(shortCode, originalUrl)).thenReturn(true);

      // 2. Act
      var result = resolveUrlUseCase.execute(command);

      // 3. Assert
      assertThat(result.originalUrl()).isEqualTo(originalUrl);
      verify(redirectCachePort).findByShortCode(shortCode);
      verify(urlRepositoryPort).findByShortCode(shortCode);
      verify(redirectCachePort).saveActiveIfAbsent(shortCode, originalUrl);
      verifyNoMoreInteractions(redirectCachePort, urlRepositoryPort);
    }

    @Test
    @DisplayName("Deve redirecionar usando DynamoDB quando escrita ACTIVE no cache falhar")
    void shouldRedirectUsingDynamoDbWhenActiveCacheWriteFails() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var shortCode = "aB3dE";
      var command = new ResolveUrlCommand(shortCode);
      var originalUrl = "https://google.com";
      var url = Url.create(userId, shortCode, originalUrl);
      when(redirectCachePort.findByShortCode(shortCode)).thenReturn(Optional.empty());
      when(urlRepositoryPort.findByShortCode(shortCode)).thenReturn(Optional.of(url));
      when(redirectCachePort.saveActiveIfAbsent(shortCode, originalUrl))
          .thenThrow(new RedirectCacheException(new RuntimeException("Redis unavailable")));

      // 2. Act
      var result = resolveUrlUseCase.execute(command);

      // 3. Assert
      assertThat(result.originalUrl()).isEqualTo(originalUrl);
      verify(redirectCachePort).findByShortCode(shortCode);
      verify(urlRepositoryPort).findByShortCode(shortCode);
      verify(redirectCachePort).saveActiveIfAbsent(shortCode, originalUrl);
      verifyNoMoreInteractions(redirectCachePort, urlRepositoryPort);
    }

    @Test
    @DisplayName("Deve lançar UrlNotFoundException quando escrita NOT_FOUND no cache falhar")
    void shouldThrowUrlNotFoundExceptionWhenNotFoundCacheWriteFails() {
      // 1. Arrange
      var shortCode = "invalid";
      var command = new ResolveUrlCommand(shortCode);
      when(redirectCachePort.findByShortCode(shortCode)).thenReturn(Optional.empty());
      when(urlRepositoryPort.findByShortCode(shortCode)).thenReturn(Optional.empty());
      when(redirectCachePort.saveNotFoundIfAbsent(shortCode))
          .thenThrow(new RedirectCacheException(new RuntimeException("Redis unavailable")));

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> resolveUrlUseCase.execute(command))
          .isInstanceOf(UrlNotFoundException.class);
      verify(redirectCachePort).findByShortCode(shortCode);
      verify(urlRepositoryPort).findByShortCode(shortCode);
      verify(redirectCachePort).saveNotFoundIfAbsent(shortCode);
      verifyNoMoreInteractions(redirectCachePort, urlRepositoryPort);
    }

    @Test
    @DisplayName("Deve lançar UrlNotFoundException quando escrita DELETED no cache falhar")
    void shouldThrowUrlNotFoundExceptionWhenDeletedCacheWriteFails() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var deletedBy = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac002");
      var now = Instant.parse("2026-01-01T00:00:00Z");
      var shortCode = "aB3dE";
      var originalUrl = "https://google.com";
      var command = new ResolveUrlCommand(shortCode);
      var url =
          Url.restore(
              userId,
              shortCode,
              originalUrl,
              now.minusSeconds(60),
              UrlStatus.DELETED,
              now,
              deletedBy,
              now);
      when(redirectCachePort.findByShortCode(shortCode)).thenReturn(Optional.empty());
      when(urlRepositoryPort.findByShortCode(shortCode)).thenReturn(Optional.of(url));
      doThrow(new RedirectCacheException(new RuntimeException("Redis unavailable")))
          .when(redirectCachePort)
          .saveDeleted(shortCode);

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> resolveUrlUseCase.execute(command))
          .isInstanceOf(UrlNotFoundException.class);
      verify(redirectCachePort).findByShortCode(shortCode);
      verify(urlRepositoryPort).findByShortCode(shortCode);
      verify(redirectCachePort).saveDeleted(shortCode);
      verifyNoMoreInteractions(redirectCachePort, urlRepositoryPort);
    }
  }
}
