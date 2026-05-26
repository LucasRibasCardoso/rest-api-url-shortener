package com.app.url_shortener.url.application.port.output;

import com.app.url_shortener.iam.domain.enums.PlanType;
import java.util.UUID;

public interface CheckUrlRateLimitPort {
  void checkShorten(UUID userId, PlanType plan);
}
