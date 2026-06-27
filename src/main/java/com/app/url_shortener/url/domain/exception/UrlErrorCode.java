package com.app.url_shortener.url.domain.exception;

import com.app.url_shortener.shared.exception.ErrorCode;

public enum UrlErrorCode implements ErrorCode {
  URL_SHORT_CODE_REQUIRED("Código curto obrigatório."),
  URL_ORIGINAL_URL_REQUIRED("URL original obrigatória."),
  URL_SHORT_CODE_COLLISION("O código curto informado já existe."),
  URL_NOT_FOUND("URL não encontrada."),
  URL_CURSOR_INVALID("Cursor de paginação inválido."),
  URL_DELETE_FORBIDDEN("Sem permissão para excluir esta URL."),
  URL_UNSAFE_DESTINATION("URL de destino não permitida."),
  COUNTER_ID_INVALID_RESPONSE("Resposta inválida do DynamoDB ao alocar bloco de IDs."),
  COUNTER_ID_CONDITIONAL_CHECK_FAILED("Item do contador de IDs não encontrado no DynamoDB."),
  COUNTER_ID_THROUGHPUT_EXCEEDED("Capacidade do DynamoDB excedida ao alocar bloco de IDs."),
  COUNTER_ID_TABLE_NOT_FOUND("Tabela do contador de IDs não encontrada no DynamoDB."),
  COUNTER_ID_DYNAMODB_FAILURE("Falha do DynamoDB ao alocar bloco de IDs."),
  COUNTER_ID_CLIENT_FAILURE("Falha de comunicação com o DynamoDB ao alocar bloco de IDs.");

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
