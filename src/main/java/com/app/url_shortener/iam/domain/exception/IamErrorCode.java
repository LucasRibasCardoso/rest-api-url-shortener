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
  AUTH_INVALID_EMAIL_VERIFICATION_EVENT("Evento de verificação de e-mail inválido."),
  AUTH_USER_NOT_FOUND("Usuário não encontrado."),
  USER_ACCOUNT_DISABLED("Conta desabilitada."),
  AUTH_DUPLICATE_EMAIL_DISPATCH_EVENT("Evento de disparo de e-mail já processado."),
  AUTH_EMAIL_DISPATCH_ALREADY_PROCESSING("E-mail de verificação já em processamento."),
  AUTH_EMAIL_DISPATCH_STATE_CONFLICT("Estado do disparo de e-mail em conflito para processamento."),
  AUTH_EMAIL_VERIFICATION_SEND_FAILED("Falha ao enviar e-mail de verificação."),
  AUTH_VERIFICATION_CODE_HASH_FAILED("Falha ao gerar hash do código de verificação."),
  AUTH_VERIFICATION_CODE_ENCRYPT_FAILED("Falha ao criptografar código de verificação."),
  AUTH_VERIFICATION_CODE_DECRYPT_FAILED("Falha ao descriptografar código de verificação."),
  AUTH_ENCRYPTED_VERIFICATION_CODE_INVALID("Código de verificação criptografado inválido."),
  AUTH_VERIFICATION_CODE_INVALID("Código de verificação inválido."),
  AUTH_EMAIL_VERIFICATION_TOKEN_NOT_FOUND("Token de verificação de e-mail não encontrado."),
  AUTH_DUPLICATE_OPEN_EMAIL_VERIFICATION_TOKEN("Já existe um token de verificação de e-mail aberto para este usuário.");

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
