package com.app.url_shortener.iam.infrastructure.adapter;

import com.app.url_shortener.iam.application.port.output.AccessTokenIssuerPort;
import com.app.url_shortener.iam.application.port.output.model.IssuedAccessToken;
import com.app.url_shortener.iam.application.result.AuthenticatedUserResult;
import com.app.url_shortener.security.jwt.JwtAccessTokenSubject;
import com.app.url_shortener.security.jwt.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccessTokenIssuerAdapter implements AccessTokenIssuerPort {

  private final JwtTokenService jwtTokenService;

  @Override
  public IssuedAccessToken issue(AuthenticatedUserResult user) {
    JwtAccessTokenSubject tokenProperties = new JwtAccessTokenSubject(user.id(), user.plan(), user.authorities());
    String accessToken = jwtTokenService.generateAccessToken(tokenProperties);
    long expiresInSeconds = jwtTokenService.getExpiresInSeconds();
    return new IssuedAccessToken(accessToken, expiresInSeconds);
  }
}
