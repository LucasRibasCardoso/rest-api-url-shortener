package com.app.url_shortener.shared.exception;

public enum CommonErrorCode implements ErrorCode {
  REQUEST_VALIDATION_FAILED("Um ou mais campos estão inválidos."),
  DATA_INTEGRITY_CONFLICT("Violação de integridade dos dados."),
  DEPENDENCY_FAILURE("Falha temporária em serviço de infraestrutura."),
  INTERNAL_SERVER_ERROR("Erro interno inesperado."),
  IDEMPOTENCY_HEADER_MISSING("O cabeçalho Idempotency-Key é obrigatório para esta operação."),
  IDEMPOTENCY_IN_PROCESSING("A requisição já está em processamento. Aguarde."),
  AUTH_ACCESS_DENIED("Acesso negado para esse recurso"),
  AUTH_UNAUTHORIZED("Autenticação necessária ou token inválido."),
  TOO_MANY_REQUESTS("Muitas requisições. Por favor, tente novamente mais tarde."),
  RATE_LIMIT_INFRASTRUCTURE_ERROR("Ocorreu um erro na infraestrutura do rate limit."),
  OUTBOX_EVENT_SERIALIZER_ERROR_EXCEPTION("Ocorreu um erro ao serializar o evento de outbox."),
  OUTBOX_EVENT_PUBLISH_ERROR_EXCEPTION("Ocorreu um erro ao publicar o evento de outbox.")
  ;

  private final String message;

  CommonErrorCode(String message) {
    this.message = message;
  }

  @Override
  public String getMessage() {
    return message;
  }

  @Override
  public String getCode() {
    return name();
  }
}
