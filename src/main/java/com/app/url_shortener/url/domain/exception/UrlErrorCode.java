package com.app.url_shortener.url.domain.exception;

import com.app.url_shortener.shared.exception.ErrorCode;

public enum UrlErrorCode implements ErrorCode {
  URL_SHORT_CODE_REQUIRED("Codigo curto e obrigatorio."),
  URL_ORIGINAL_URL_REQUIRED("URL original e obrigatoria."),
  URL_SHORT_CODE_COLLISION("O codigo curto informado ja existe."),
  URL_NOT_FOUND("URL nao encontrada."),
  COUNTER_ID_INVALID_RESPONSE("Resposta invalida do DynamoDB ao alocar bloco de IDs."),
  COUNTER_ID_CONDITIONAL_CHECK_FAILED("Item do contador de IDs nao encontrado no DynamoDB."),
  COUNTER_ID_THROUGHPUT_EXCEEDED("Capacidade do DynamoDB excedida ao alocar bloco de IDs."),
  COUNTER_ID_TABLE_NOT_FOUND("Tabela do contador de IDs nao encontrada no DynamoDB."),
  COUNTER_ID_DYNAMODB_FAILURE("Falha do DynamoDB ao alocar bloco de IDs."),
  COUNTER_ID_CLIENT_FAILURE("Falha de comunicacao com DynamoDB ao alocar bloco de IDs.");

  private final String message;

  UrlErrorCode(String message) {
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
