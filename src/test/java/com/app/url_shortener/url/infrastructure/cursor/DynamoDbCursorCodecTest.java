package com.app.url_shortener.url.infrastructure.cursor;

import com.app.url_shortener.url.domain.exception.InvalidUrlCursorException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Codec de Cursor DynamoDB")
class DynamoDbCursorCodecTest {

  private final DynamoDbCursorCodec codec = new DynamoDbCursorCodec(new ObjectMapper());

  @Nested
  @DisplayName("Codificação")
  class EncodeTests {

    @Test
    @DisplayName("Deve codificar chave do DynamoDB como cursor opaco em Base64 URL-safe")
    void shouldEncodeDynamoDbKeyAsUrlSafeBase64Cursor() {
      // 1. Arrange
      var lastKey = Map.of(
          "shortCode", AttributeValue.builder().s("aB3dE").build(),
          "sequence", AttributeValue.builder().n("100").build());

      // 2. Act
      var result = codec.encode(lastKey);

      // 3. Assert
      assertThat(result).isNotBlank();
      assertThat(result).doesNotContain("=");
      var decodedJson = new String(Base64.getUrlDecoder().decode(result), StandardCharsets.UTF_8);
      assertThat(decodedJson)
          .contains("\"version\":1")
          .contains("\"shortCode\":{\"S\":\"aB3dE\"")
          .contains("\"sequence\":{\"N\":\"100\"");
    }

    @Test
    @DisplayName("Deve retornar nulo quando chave avaliada for nula")
    void shouldReturnNullWhenLastEvaluatedKeyIsNull() {
      // 1. Arrange

      // 2. Act
      var result = codec.encode(null);

      // 3. Assert
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("Deve retornar nulo quando chave avaliada for vazia")
    void shouldReturnNullWhenLastEvaluatedKeyIsEmpty() {
      // 1. Arrange

      // 2. Act
      var result = codec.encode(Map.of());

      // 3. Assert
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("Deve lançar RuntimeException quando tipo de atributo não for suportado")
    void shouldThrowRuntimeExceptionWhenAttributeValueTypeIsUnsupported() {
      // 1. Arrange
      var lastKey = Map.of("active", AttributeValue.builder().bool(true).build());

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> codec.encode(lastKey))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Erro ao codificar cursor");
    }
  }

  @Nested
  @DisplayName("Decodificação")
  class DecodeTests {

    @Test
    @DisplayName("Deve decodificar cursor para chave do DynamoDB")
    void shouldDecodeCursorToDynamoDbKey() {
      // 1. Arrange
      var json = """
          {"version":1,"key":{"shortCode":{"S":"aB3dE"},"sequence":{"N":"100"}}}
          """;
      var cursor = Base64.getUrlEncoder().withoutPadding()
          .encodeToString(json.getBytes(StandardCharsets.UTF_8));

      // 2. Act
      var result = codec.decode(cursor);

      // 3. Assert
      assertThat(result)
          .containsEntry("shortCode", AttributeValue.builder().s("aB3dE").build())
          .containsEntry("sequence", AttributeValue.builder().n("100").build());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    @DisplayName("Deve retornar nulo quando cursor for nulo ou em branco")
    void shouldReturnNullWhenCursorIsNullOrBlank(String cursor) {
      // 1. Arrange

      // 2. Act
      var result = codec.decode(cursor);

      // 3. Assert
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("Deve lançar RuntimeException quando cursor for inválido")
    void shouldThrowRuntimeExceptionWhenCursorIsInvalid() {
      // 1. Arrange
      var cursor = "invalid";

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> codec.decode(cursor))
          .isInstanceOf(InvalidUrlCursorException.class);
    }

    @Test
    @DisplayName("Deve lançar RuntimeException quando versão do cursor não for suportada")
    void shouldThrowRuntimeExceptionWhenCursorVersionIsUnsupported() {
      // 1. Arrange
      var json = """
          {"version":2,"key":{"shortCode":{"S":"aB3dE"}}}
          """;
      var cursor = Base64.getUrlEncoder().withoutPadding()
          .encodeToString(json.getBytes(StandardCharsets.UTF_8));

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> codec.decode(cursor))
          .isInstanceOf(InvalidUrlCursorException.class);
    }
  }

  @Nested
  @DisplayName("Round-trip")
  class RoundTripTests {

    @Test
    @DisplayName("Deve preservar valores de chave ao codificar e decodificar cursor")
    void shouldPreserveKeyValuesWhenEncodingAndDecodingCursor() {
      // 1. Arrange
      var binary = SdkBytes.fromUtf8String("binary-value");
      var lastKey = Map.of(
          "userId", AttributeValue.builder().s("019a16f1-ae7f-7c9d-9e18-44773f1ac001").build(),
          "sequence", AttributeValue.builder().n("100").build(),
          "binary", AttributeValue.builder().b(binary).build());

      // 2. Act
      var cursor = codec.encode(lastKey);
      var result = codec.decode(cursor);

      // 3. Assert
      assertThat(result).containsAllEntriesOf(lastKey);
    }
  }
}
