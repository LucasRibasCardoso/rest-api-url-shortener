package com.app.url_shortener.url.application.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.app.url_shortener.url.domain.model.UrlStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Testes de Unidade - Results de coleções de URL")
class UrlCollectionResultTest {

  @Nested
  @DisplayName("UrlPageResult")
  class UrlPageResultTests {

    @Test
    @DisplayName("Deve criar cópia imutável das URLs paginadas")
    void shouldCreateImmutableCopyOfPageUrls() {
      // 1. Arrange
      var urls = new ArrayList<>(List.of(listItemResult("aB3dE")));

      // 2. Act
      var result = new UrlPageResult(urls, "next-cursor");
      urls.add(listItemResult("fG4hI"));

      // 3. Assert
      assertThat(result.urls()).extracting(UrlListItemResult::shortCode).containsExactly("aB3dE");
      assertThatThrownBy(() -> result.urls().add(listItemResult("fG4hI")))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("UrlRankingResult")
  class UrlRankingResultTests {

    @Test
    @DisplayName("Deve criar cópia imutável das URLs do ranking")
    void shouldCreateImmutableCopyOfRankingUrls() {
      // 1. Arrange
      var urls = new ArrayList<>(List.of(rankingItemResult("aB3dE")));

      // 2. Act
      var result = new UrlRankingResult(urls);
      urls.add(rankingItemResult("fG4hI"));

      // 3. Assert
      assertThat(result.urls()).extracting(UrlRankingItemResult::shortCode).containsExactly("aB3dE");
      assertThatThrownBy(() -> result.urls().add(rankingItemResult("fG4hI")))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  private UrlListItemResult listItemResult(String shortCode) {
    return new UrlListItemResult(
        "https://example.com/" + shortCode,
        shortCode,
        Instant.parse("2026-06-08T12:00:00Z"),
        UrlStatus.ACTIVE);
  }

  private UrlRankingItemResult rankingItemResult(String shortCode) {
    return new UrlRankingItemResult(
        "https://example.com/" + shortCode,
        shortCode,
        Instant.parse("2026-06-08T12:00:00Z"),
        UrlStatus.ACTIVE,
        10,
        Instant.parse("2026-06-08T13:00:00Z"));
  }
}
