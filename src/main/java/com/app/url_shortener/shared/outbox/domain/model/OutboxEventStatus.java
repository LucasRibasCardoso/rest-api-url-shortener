package com.app.url_shortener.shared.outbox.domain.model;

public enum OutboxEventStatus {
  PENDING,
  PUBLISHED,
  FAILED
}
