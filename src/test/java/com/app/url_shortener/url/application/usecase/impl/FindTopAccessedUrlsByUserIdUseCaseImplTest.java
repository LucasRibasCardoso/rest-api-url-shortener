package com.app.url_shortener.url.application.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.app.url_shortener.url.application.command.UrlRankingCommand;
import com.app.url_shortener.url.application.port.output.UrlRepositoryPort;
import com.app.url_shortener.url.application.result.UrlRankingItemResult;
import com.app.url_shortener.url.application.result.UrlRankingResult;
import com.app.url_shortener.url.domain.model.UrlStatus;
import java.time.Instant;
import java.util.List;
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
@DisplayName("Testes de Unidade - Caso de Uso de Ranking de URLs")
class FindTopAccessedUrlsByUserIdUseCaseImplTest {

  @Mock private UrlRepositoryPort urlRepositoryPort;

  @InjectMocks private FindTopAccessedUrlsByUserIdUseCaseImpl findTopAccessedUrlsByUserIdUseCase;

  @Nested
  @DisplayName("Execução")
  class ExecuteTests {

    @Test
    @DisplayName("Deve buscar ranking de URLs ativas do usuário e retornar resultado")
    void shouldFindTopAccessedActiveUrlsByUserIdAndReturnResult() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var rankingSize = 10;
      var command = new UrlRankingCommand(userId, rankingSize);
      var urls =
          List.of(
              new UrlRankingItemResult(
                  "https://google.com",
                  "aB3dE",
                  Instant.parse("2026-05-10T14:30:00Z"),
                  UrlStatus.ACTIVE,
                  42,
                  Instant.parse("2026-06-08T10:00:00Z")));
      var rankingResult = new UrlRankingResult(urls);

      when(urlRepositoryPort.findTopAccessedActiveByUserId(userId, rankingSize))
          .thenReturn(rankingResult);

      // 2. Act
      var result = findTopAccessedUrlsByUserIdUseCase.execute(command);

      // 3. Assert
      assertThat(result).isSameAs(rankingResult);
      verify(urlRepositoryPort).findTopAccessedActiveByUserId(userId, rankingSize);
      verifyNoMoreInteractions(urlRepositoryPort);
    }
  }
}
