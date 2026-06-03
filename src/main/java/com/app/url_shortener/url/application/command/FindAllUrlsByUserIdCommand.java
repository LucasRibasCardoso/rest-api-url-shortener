package com.app.url_shortener.url.application.command;

import java.util.UUID;

public record FindAllUrlsByUserIdCommand(UUID userId, int limit, String cursor, UrlStatusFilter status) {

  public FindAllUrlsByUserIdCommand {
    cursor = cursor != null ? cursor.trim() : null;
    status = status != null ? status : UrlStatusFilter.ACTIVE;
  }
}
