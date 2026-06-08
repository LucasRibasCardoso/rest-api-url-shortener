package com.app.url_shortener.iam.application.port.output;

import com.app.url_shortener.iam.application.port.output.model.IssuedAccessToken;
import com.app.url_shortener.iam.application.result.AuthenticatedUserResult;

public interface AccessTokenIssuerPort {

  IssuedAccessToken issue(AuthenticatedUserResult user);
}
