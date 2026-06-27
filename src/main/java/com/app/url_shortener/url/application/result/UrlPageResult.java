package com.app.url_shortener.url.application.result;

import java.util.List;

public record UrlPageResult(List<UrlListItemResult> urls, String nextCursor) {
  public UrlPageResult {
    urls = List.copyOf(urls);
  }
}
