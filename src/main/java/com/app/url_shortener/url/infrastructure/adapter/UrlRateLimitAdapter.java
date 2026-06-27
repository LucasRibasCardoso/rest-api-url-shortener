package com.app.url_shortener.url.infrastructure.adapter;

import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.shared.ratelimit.service.RateLimitService;
import com.app.url_shortener.url.application.port.output.CheckUrlRateLimitPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UrlRateLimitAdapter implements CheckUrlRateLimitPort {

  private final RateLimitService rateLimitService;

  @Override
  public void checkShorten(UUID userId, PlanType plan) {
    rateLimitService.checkShorten(userId, plan);
  }
}
