package com.app.url_shortener.url.application.command;

public enum UrlStatusFilter {
  ACTIVE,
  DELETED,
  ALL;

  public boolean isAll() {
    return this.equals(ALL);
  }

  public boolean isActive() {
    return this.equals(ACTIVE);
  }

  public boolean isDeleted() {
    return this.equals(DELETED);
  }
}
