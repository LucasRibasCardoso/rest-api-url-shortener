package com.app.url_shortener.url.application.port.output;

import com.app.url_shortener.url.application.port.output.model.RedirectCacheEntry;
import java.util.Optional;

public interface RedirectCachePort {

  Optional<RedirectCacheEntry> findByShortCode(String shortCode);

  boolean saveActiveIfAbsent(String shortCode, String longUrl);

  void saveActive(String shortCode, String longUrl);

  void saveDeleted(String shortCode);

  boolean saveNotFoundIfAbsent(String shortCode);

  void evict(String shortCode);
}
