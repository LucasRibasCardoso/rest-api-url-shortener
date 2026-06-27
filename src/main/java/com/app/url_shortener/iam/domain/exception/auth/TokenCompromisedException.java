package com.app.url_shortener.iam.domain.exception.auth;

import com.app.url_shortener.iam.domain.exception.IamErrorCode;
import com.app.url_shortener.shared.exception.unauthorized.UnauthorizedException;

public class TokenCompromisedException extends UnauthorizedException {

  public TokenCompromisedException() {
    super(IamErrorCode.AUTH_REFRESH_TOKEN_COMPROMISED);
  }
}
