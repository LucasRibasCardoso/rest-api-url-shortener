package com.app.url_shortener.shared.ratelimit;

import com.app.url_shortener.iam.domain.enums.PlanType;
import java.util.UUID;

public interface RateLimitService {

  void checkLogin(String clientIp, String email);

  void checkShorten(UUID userId, PlanType plan);

  void checkResendVerification(String email);

  void checkVerifyEmail(String email);
}
