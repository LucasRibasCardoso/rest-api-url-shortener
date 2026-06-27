package com.app.url_shortener.url.application.result;

import java.util.List;

public record UrlRankingResult(List<UrlRankingItemResult> urls) {
  public UrlRankingResult {
    urls = List.copyOf(urls);
  }
}
