package com.app.url_shortener.iam.application.service.impl;

import com.app.url_shortener.iam.application.port.output.RefreshTokenRepositoryPort;
import com.app.url_shortener.iam.application.service.CompromisedRefreshTokenRevocationService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompromisedRefreshTokenRevocationServiceImpl implements CompromisedRefreshTokenRevocationService {

  private final RefreshTokenRepositoryPort refreshTokenRepositoryPort;

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void revokeAllTokensDueToCompromise(UUID userId) {
    refreshTokenRepositoryPort.revokeAllTokensForUser(userId);
  }
}
