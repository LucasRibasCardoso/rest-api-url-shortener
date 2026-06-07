package com.app.url_shortener.url.application.usecase.impl;

import com.app.url_shortener.url.application.command.FindAllUrlsByUserIdCommand;
import com.app.url_shortener.url.application.command.UrlStatusFilter;
import com.app.url_shortener.url.application.port.output.UrlRepositoryPort;
import com.app.url_shortener.url.application.result.PageUrlResult;
import com.app.url_shortener.url.application.result.UrlListItemResult;
import com.app.url_shortener.url.domain.model.UrlStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Caso de Uso de Listagem de URLs por Usuário")
class FindAllUrlsByUserIdUseCaseImplTest {

  @Mock
  private UrlRepositoryPort urlRepositoryPort;

  @InjectMocks
  private FindAllUrlsByUserIdUseCaseImpl findAllUrlsByUserIdUseCase;

  @Nested
  @DisplayName("Execução")
  class ExecuteTests {

    @Test
    @DisplayName("Deve buscar URLs do usuário com paginação e retornar o resultado do repositório")
    void shouldFindUrlsByUserIdWithPaginationAndReturnRepositoryResult() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var limit = 10;
      var cursor = "next-page-cursor";
      var status = UrlStatusFilter.ALL;
      var command = new FindAllUrlsByUserIdCommand(userId, limit, cursor, status);
      var pageUrlResult = new PageUrlResult(List.of(
          new UrlListItemResult(
              "https://google.com",
              "aB3dE",
              Instant.parse("2026-05-10T14:30:00Z"),
              UrlStatus.ACTIVE,
              42,
              Instant.parse("2026-05-11T10:00:00Z")),
          new UrlListItemResult(
              "https://spring.io",
              "fG4hI",
              Instant.parse("2026-05-10T15:45:00Z"),
              UrlStatus.DELETED,
              10,
              Instant.parse("2026-05-11T11:00:00Z"))
      ), "following-page-cursor");
      when(urlRepositoryPort.findAllByUserId(userId, limit, cursor, status)).thenReturn(pageUrlResult);

      // 2. Act
      var result = findAllUrlsByUserIdUseCase.execute(command);

      // 3. Assert
      assertThat(result).isSameAs(pageUrlResult);
      verify(urlRepositoryPort).findAllByUserId(userId, limit, cursor, status);
      verifyNoMoreInteractions(urlRepositoryPort);
    }

    @Test
    @DisplayName("Deve buscar URLs do usuário sem cursor")
    void shouldFindUrlsByUserIdWithoutCursor() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var limit = 20;
      String cursor = null;
      var status = UrlStatusFilter.ACTIVE;
      var command = new FindAllUrlsByUserIdCommand(userId, limit, cursor, status);
      var pageUrlResult = new PageUrlResult(List.of(), null);
      when(urlRepositoryPort.findAllByUserId(userId, limit, cursor, status)).thenReturn(pageUrlResult);

      // 2. Act
      var result = findAllUrlsByUserIdUseCase.execute(command);

      // 3. Assert
      assertThat(result).isSameAs(pageUrlResult);
      verify(urlRepositoryPort).findAllByUserId(userId, limit, cursor, status);
      verifyNoMoreInteractions(urlRepositoryPort);
    }
  }
}
