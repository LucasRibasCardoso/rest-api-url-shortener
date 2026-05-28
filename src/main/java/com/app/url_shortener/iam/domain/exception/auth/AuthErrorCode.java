package com.app.url_shortener.iam.domain.exception.auth;

import com.app.url_shortener.shared.exception.ErrorCode;

public enum AuthErrorCode implements ErrorCode {

  AUTH_DEFAULT_ROLE_NOT_FOUND("Permissão padrão não encontrada."),
  AUTH_USER_NAME_REQUIRED("Nome do usuário é obrigatório."),
  AUTH_EMAIL_REQUIRED("Email é obrigatório."),
  AUTH_PASSWORD_HASH_REQUIRED("Hash da senha é obrigatório."),
  AUTH_ROLE_NAME_REQUIRED("Nome da role é obrigatório."),
  AUTH_PERMISSION_NAME_REQUIRED("Nome da permissão é obrigatório."),
  AUTH_INVALID_CREDENTIALS("Credenciais inválidas."),
  AUTH_ACCOUNT_PENDING_VERIFICATION("Conta pendente de verificação."),
  AUTH_ACCOUNT_LOCKED("Conta bloqueada."),
  AUTH_ACCOUNT_NOT_FOUND("Conta não existe ou foi removida."),
  AUTH_REFRESH_TOKEN_INVALID("Refresh token inválido."),
  AUTH_REFRESH_TOKEN_EXPIRED("Refresh token expirado."),
  AUTH_REFRESH_TOKEN_COMPROMISED("Refresh token comprometido."),
  AUTH_EMAIL_ALREADY_EXISTS("Email já cadastrado."),
  AUTH_INVALID_VERIFICATION_CODE("Código de verificação inválido."),
  AUTH_INVALID_OR_EXPIRED_VERIFICATION_CODE("Código de verificação inválido ou expirado."),
  AUTH_EMAIL_ALREADY_VERIFIED("Email já verificado."),
  AUTH_USER_NOT_FOUND("Usuário não encontrado."),
  AUTH_PASSWORD_RESET_TOKEN_INVALID("Token de redefinição de senha inválido."),
  AUTH_PASSWORD_RESET_TOKEN_EXPIRED("Token de redefinição de senha expirado.");

  private final String message;

  AuthErrorCode(String message) {
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
