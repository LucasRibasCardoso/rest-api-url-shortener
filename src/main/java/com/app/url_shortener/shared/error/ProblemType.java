package com.app.url_shortener.shared.error;

public final class ProblemType {

  private ProblemType() {
  }

  public static final String VALIDATION = "/errors/validation";
  public static final String CONFLICT = "/errors/conflict";
  public static final String NOT_FOUND = "/errors/not-found";
  public static final String UNAUTHORIZED = "/errors/unauthorized";
  public static final String FORBIDDEN = "/errors/forbidden";
  public static final String TOO_MANY_REQUESTS = "/errors/too-many-requests";
  public static final String BUSINESS = "/errors/business";
  public static final String INFRASTRUCTURE = "/errors/infrastructure";
}