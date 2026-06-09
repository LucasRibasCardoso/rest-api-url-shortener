package com.app.url_shortener.iam.domain.exception;

import com.app.url_shortener.shared.exception.ErrorCode;

public enum IamErrorCode implements ErrorCode {
  AUTH_DEFAULT_ROLE_NOT_FOUND("Permissão padrão não encontrada."),
  AUTH_INVALID_CREDENTIALS("Credenciais inválidas."),
  AUTH_ACCOUNT_PENDING_VERIFICATION("Conta pendente de verificação."),
  AUTH_ACCOUNT_LOCKED("Conta bloqueada."),
  AUTH_REFRESH_TOKEN_INVALID("Refresh token inválido."),
  AUTH_REFRESH_TOKEN_EXPIRED("Refresh token expirado."),
  AUTH_REFRESH_TOKEN_COMPROMISED("Refresh token comprometido."),
  AUTH_EMAIL_ALREADY_EXISTS("Email já cadastrado."),
  AUTH_INVALID_VERIFICATION_CODE("Código de verificação inválido."),
  AUTH_INVALID_OR_EXPIRED_VERIFICATION_CODE("Código de verificação inválido ou expirado."),
  AUTH_USER_NOT_FOUND("Usuário não encontrado."),
  USER_ACCOUNT_DISABLED("Conta desabilitada.");

  private final String message;

  IamErrorCode(String message) {
    this.message = message;
  }

  @Override
  public String getCode() {
    return name();
  }

  @Override
  public String getMessage() {
    return message;
  }
}
