package com.app.url_shortener.url.application.usecase.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.app.url_shortener.url.application.command.DeleteUrlCommand;
import com.app.url_shortener.url.application.port.output.RedirectCachePort;
import com.app.url_shortener.url.application.port.output.UrlRepositoryPort;
import com.app.url_shortener.url.domain.exception.RedirectCacheException;
import com.app.url_shortener.url.domain.exception.UrlDeleteForbiddenException;
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

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Caso de Uso de Exclusão de URL")
class DeleteUrlUseCaseImplTest {

  @Mock private UrlRepositoryPort urlRepositoryPort;

  @Mock private RedirectCachePort redirectCachePort;

  @InjectMocks private DeleteUrlUseCaseImpl deleteUrlUseCase;

  @Nested
  @DisplayName("Execução")
  class ExecuteTests {

    @Test
    @DisplayName("Deve excluir a URL quando o solicitante for o dono")
    void shouldDeleteUrlWhenRequesterIsOwner() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var shortCode = "aB3dE";
      var url = Url.create(userId, shortCode, "https://google.com");
      var command = new DeleteUrlCommand(userId, shortCode, false);
      when(urlRepositoryPort.findByShortCode(shortCode)).thenReturn(Optional.of(url));

      // 2. Act
      deleteUrlUseCase.execute(command);

      // 3. Assert
      verify(urlRepositoryPort).findByShortCode(shortCode);
      verify(urlRepositoryPort).softDeleteByShortCode(url, userId);
      verify(redirectCachePort).saveDeleted(shortCode);
      verifyNoMoreInteractions(urlRepositoryPort, redirectCachePort);
    }

    @Test
    @DisplayName("Deve excluir a URL quando o solicitante puder excluir qualquer URL")
    void shouldDeleteUrlWhenRequesterCanDeleteAny() {
      // 1. Arrange
      var ownerId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var requesterId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac002");
      var shortCode = "aB3dE";
      var url = Url.create(ownerId, shortCode, "https://google.com");
      var command = new DeleteUrlCommand(requesterId, shortCode, true);
      when(urlRepositoryPort.findByShortCode(shortCode)).thenReturn(Optional.of(url));

      // 2. Act
      deleteUrlUseCase.execute(command);

      // 3. Assert
      verify(urlRepositoryPort).findByShortCode(shortCode);
      verify(urlRepositoryPort).softDeleteByShortCode(url, requesterId);
      verify(redirectCachePort).saveDeleted(shortCode);
      verifyNoMoreInteractions(urlRepositoryPort, redirectCachePort);
    }

    @Test
    @DisplayName("Deve reforçar cache DELETED quando a URL já estiver deletada")
    void shouldSaveDeletedInCacheWhenUrlIsAlreadyDeleted() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var shortCode = "aB3dE";
      var createdAt = Instant.parse("2026-05-10T14:30:00Z");
      var deletedAt = Instant.parse("2026-05-11T10:00:00Z");
      var url =
          Url.restore(
              userId,
              shortCode,
              "https://google.com",
              createdAt,
              UrlStatus.DELETED,
              deletedAt,
              userId,
              deletedAt,
              0,
              null);
      var command = new DeleteUrlCommand(userId, shortCode, false);
      when(urlRepositoryPort.findByShortCode(shortCode)).thenReturn(Optional.of(url));

      // 2. Act
      deleteUrlUseCase.execute(command);

      // 3. Assert
      verify(urlRepositoryPort).findByShortCode(shortCode);
      verify(redirectCachePort).saveDeleted(shortCode);
      verifyNoMoreInteractions(urlRepositoryPort, redirectCachePort);
    }

    @Test
    @DisplayName("Deve lançar UrlNotFoundException quando a URL não existir")
    void shouldThrowUrlNotFoundExceptionWhenUrlDoesNotExist() {
      // 1. Arrange
      var requesterId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var shortCode = "invalid";
      var command = new DeleteUrlCommand(requesterId, shortCode, false);
      when(urlRepositoryPort.findByShortCode(shortCode)).thenReturn(Optional.empty());

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> deleteUrlUseCase.execute(command))
          .isInstanceOf(UrlNotFoundException.class);
      verify(urlRepositoryPort).findByShortCode(shortCode);
      verifyNoMoreInteractions(urlRepositoryPort, redirectCachePort);
    }

    @Test
    @DisplayName(
        "Deve lançar UrlDeleteForbiddenException quando o solicitante não puder excluir a URL")
    void shouldThrowUrlDeleteForbiddenExceptionWhenRequesterCannotDeleteUrl() {
      // 1. Arrange
      var ownerId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var requesterId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac002");
      var shortCode = "aB3dE";
      var url = Url.create(ownerId, shortCode, "https://google.com");
      var command = new DeleteUrlCommand(requesterId, shortCode, false);
      when(urlRepositoryPort.findByShortCode(shortCode)).thenReturn(Optional.of(url));

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> deleteUrlUseCase.execute(command))
          .isInstanceOf(UrlDeleteForbiddenException.class);
      verify(urlRepositoryPort).findByShortCode(shortCode);
      verifyNoMoreInteractions(urlRepositoryPort, redirectCachePort);
    }

    @Test
    @DisplayName("Deve propagar falha ao salvar DELETED no cache")
    void shouldPropagateExceptionWhenSavingDeletedInCacheFails() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var shortCode = "aB3dE";
      var url = Url.create(userId, shortCode, "https://google.com");
      var command = new DeleteUrlCommand(userId, shortCode, false);
      var exception = new RedirectCacheException(new RuntimeException("Redis unavailable"));
      when(urlRepositoryPort.findByShortCode(shortCode)).thenReturn(Optional.of(url));
      doThrow(exception).when(redirectCachePort).saveDeleted(shortCode);

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> deleteUrlUseCase.execute(command)).isSameAs(exception);
      verify(urlRepositoryPort).findByShortCode(shortCode);
      verify(urlRepositoryPort).softDeleteByShortCode(url, userId);
      verify(redirectCachePort).saveDeleted(shortCode);
      verifyNoMoreInteractions(urlRepositoryPort, redirectCachePort);
    }
  }
}
