package com.app.url_shortener.url.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Testes de Unidade - UrlErrorCode")
class UrlErrorCodeTest {

  @Nested
  @DisplayName("Mensagens")
  class MessageTests {

    @Test
    @DisplayName("Deve expor mensagens padronizadas em português")
    void shouldExposeStandardPortugueseMessages() {
      // 1. Arrange
      Map<UrlErrorCode, String> messages = Map.ofEntries(
          Map.entry(UrlErrorCode.URL_SHORT_CODE_REQUIRED, "Código curto obrigatório."),
          Map.entry(UrlErrorCode.URL_ORIGINAL_URL_REQUIRED, "URL original obrigatória."),
          Map.entry(UrlErrorCode.URL_SHORT_CODE_COLLISION, "O código curto informado já existe."),
          Map.entry(UrlErrorCode.URL_NOT_FOUND, "URL não encontrada."),
          Map.entry(UrlErrorCode.URL_CURSOR_INVALID, "Cursor de paginação inválido."),
          Map.entry(UrlErrorCode.URL_DELETE_FORBIDDEN, "Sem permissão para excluir esta URL."),
          Map.entry(UrlErrorCode.URL_UNSAFE_DESTINATION, "URL de destino não permitida."),
          Map.entry(UrlErrorCode.COUNTER_ID_INVALID_RESPONSE, "Resposta inválida do DynamoDB ao alocar bloco de IDs."),
          Map.entry(UrlErrorCode.COUNTER_ID_CONDITIONAL_CHECK_FAILED, "Item do contador de IDs não encontrado no DynamoDB."),
          Map.entry(UrlErrorCode.COUNTER_ID_THROUGHPUT_EXCEEDED, "Capacidade do DynamoDB excedida ao alocar bloco de IDs."),
          Map.entry(UrlErrorCode.COUNTER_ID_TABLE_NOT_FOUND, "Tabela do contador de IDs não encontrada no DynamoDB."),
          Map.entry(UrlErrorCode.COUNTER_ID_DYNAMODB_FAILURE, "Falha do DynamoDB ao alocar bloco de IDs."),
          Map.entry(UrlErrorCode.COUNTER_ID_CLIENT_FAILURE, "Falha de comunicação com o DynamoDB ao alocar bloco de IDs.")
      );

      // 2. Act
      var errorCodes = UrlErrorCode.values();

      // 3. Assert
      for (UrlErrorCode errorCode : errorCodes) {
        assertThat(errorCode.getMessage()).isEqualTo(messages.get(errorCode));
      }
    }
  }
}
