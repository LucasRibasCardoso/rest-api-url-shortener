package com.app.url_shortener.url.application.usecase.impl;

import com.app.url_shortener.url.application.command.UrlDetailsCommand;
import com.app.url_shortener.url.application.port.output.UrlRepositoryPort;
import com.app.url_shortener.url.domain.exception.UrlNotFoundException;
import com.app.url_shortener.url.domain.model.Url;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Caso de Uso de Detalhes de URL")
class FindUrlDetailsUseCaseImplTest {

  @Mock
  private UrlRepositoryPort urlRepositoryPort;

  @InjectMocks
  private FindUrlDetailsUseCaseImpl findUrlDetailsUseCase;

  @Nested
  @DisplayName("Execução")
  class ExecuteTests {

    @Test
    @DisplayName("Deve retornar os detalhes da URL quando o solicitante for o dono")
    void shouldReturnUrlDetailsWhenRequesterIsOwner() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var shortCode = "aB3dE";
      var originalUrl = "https://google.com";
      var createdAt = Instant.parse("2026-05-10T14:30:00Z");
      var url = Url.restore(userId, shortCode, originalUrl, createdAt);
      var command = new UrlDetailsCommand(userId, shortCode, false);
      when(urlRepositoryPort.findByShortCode(shortCode)).thenReturn(Optional.of(url));

      // 2. Act
      var result = findUrlDetailsUseCase.execute(command);

      // 3. Assert
      assertThat(result.originalUrl()).isEqualTo(originalUrl);
      assertThat(result.shortCode()).isEqualTo(shortCode);
      assertThat(result.createdAt()).isEqualTo(createdAt);
      verify(urlRepositoryPort).findByShortCode(shortCode);
      verifyNoMoreInteractions(urlRepositoryPort);
    }

    @Test
    @DisplayName("Deve retornar os detalhes da URL quando o solicitante puder ler qualquer URL")
    void shouldReturnUrlDetailsWhenRequesterCanReadAny() {
      // 1. Arrange
      var ownerId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var requesterId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac002");
      var shortCode = "aB3dE";
      var originalUrl = "https://google.com";
      var createdAt = Instant.parse("2026-05-10T14:30:00Z");
      var url = Url.restore(ownerId, shortCode, originalUrl, createdAt);
      var command = new UrlDetailsCommand(requesterId, shortCode, true);
      when(urlRepositoryPort.findByShortCode(shortCode)).thenReturn(Optional.of(url));

      // 2. Act
      var result = findUrlDetailsUseCase.execute(command);

      // 3. Assert
      assertThat(result.originalUrl()).isEqualTo(originalUrl);
      assertThat(result.shortCode()).isEqualTo(shortCode);
      assertThat(result.createdAt()).isEqualTo(createdAt);
      verify(urlRepositoryPort).findByShortCode(shortCode);
      verifyNoMoreInteractions(urlRepositoryPort);
    }

    @Test
    @DisplayName("Deve lançar UrlNotFoundException quando a URL não existir")
    void shouldThrowUrlNotFoundExceptionWhenUrlDoesNotExist() {
      // 1. Arrange
      var requesterId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var shortCode = "invalid";
      var command = new UrlDetailsCommand(requesterId, shortCode, false);
      when(urlRepositoryPort.findByShortCode(shortCode)).thenReturn(Optional.empty());

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> findUrlDetailsUseCase.execute(command))
          .isInstanceOf(UrlNotFoundException.class);
      verify(urlRepositoryPort).findByShortCode(shortCode);
      verifyNoMoreInteractions(urlRepositoryPort);
    }

    @Test
    @DisplayName("Deve lançar UrlNotFoundException quando o solicitante não puder ler a URL")
    void shouldThrowUrlNotFoundExceptionWhenRequesterCannotReadUrl() {
      // 1. Arrange
      var ownerId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var requesterId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac002");
      var shortCode = "aB3dE";
      var url = Url.create(ownerId, shortCode, "https://google.com");
      var command = new UrlDetailsCommand(requesterId, shortCode, false);
      when(urlRepositoryPort.findByShortCode(shortCode)).thenReturn(Optional.of(url));

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> findUrlDetailsUseCase.execute(command))
          .isInstanceOf(UrlNotFoundException.class);
      verify(urlRepositoryPort).findByShortCode(shortCode);
      verifyNoMoreInteractions(urlRepositoryPort);
    }
  }
}
