package com.app.url_shortener.iam.application.event;

public final class IamOutboxEventTypes {

  public static final String AGGREGATE_USER = "USER";
  public static final String EMAIL_VERIFICATION_REQUESTED = "EMAIL_VERIFICATION_REQUESTED";

  private IamOutboxEventTypes() {}
}
