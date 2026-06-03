package com.app.url_shortener.url.infrastructure.cursor;

import com.app.url_shortener.url.domain.exception.InvalidUrlCursorException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class DynamoDbCursorCodec {

  private static final int CURRENT_VERSION = 1;

  private final ObjectMapper objectMapper;

  public String encode(Map<String, AttributeValue> lastEvaluatedKey) {
    if (lastEvaluatedKey == null || lastEvaluatedKey.isEmpty()) {
      return null;
    }

    try {
      var payload = new DynamoDbCursorPayload(CURRENT_VERSION, toSerializableMap(lastEvaluatedKey));
      byte[] json = objectMapper.writeValueAsBytes(payload);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
    } catch (Exception exception) {
      throw new RuntimeException("Erro ao codificar cursor", exception);
    }
  }

  public Map<String, AttributeValue> decode(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }

    try {
      byte[] decoded = Base64.getUrlDecoder().decode(cursor);
      var payload = objectMapper.readValue(decoded, DynamoDbCursorPayload.class);
      validateVersion(payload);
      return toAttributeValueMap(payload.key());
    } catch (Exception exception) {
      throw new InvalidUrlCursorException();
    }
  }

  private void validateVersion(DynamoDbCursorPayload payload) {
    if (payload == null || payload.version() != CURRENT_VERSION) {
      throw new IllegalArgumentException("Unsupported cursor version.");
    }
  }

  private Map<String, Map<String, String>> toSerializableMap(Map<String, AttributeValue> lastEvaluatedKey) {
    var result = new LinkedHashMap<String, Map<String, String>>();

    lastEvaluatedKey.forEach((key, value) -> result.put(key, toSerializableValue(value)));

    return result;
  }

  private Map<String, String> toSerializableValue(AttributeValue value) {
    if (value.s() != null) {
      return Map.of("S", value.s());
    }

    if (value.n() != null) {
      return Map.of("N", value.n());
    }

    if (value.b() != null) {
      return Map.of("B", Base64.getUrlEncoder().withoutPadding().encodeToString(value.b().asByteArray()));
    }

    throw new IllegalArgumentException("Unsupported cursor attribute value type.");
  }

  private Map<String, AttributeValue> toAttributeValueMap(Map<String, Map<String, String>> value) {
    if (value == null || value.isEmpty()) {
      return Map.of();
    }

    var result = new LinkedHashMap<String, AttributeValue>();

    value.forEach((key, attribute) -> result.put(key, toAttributeValue(attribute)));

    return result;
  }

  private AttributeValue toAttributeValue(Map<String, String> value) {
    if (value.containsKey("S")) {
      return AttributeValue.builder().s(value.get("S")).build();
    }

    if (value.containsKey("N")) {
      return AttributeValue.builder().n(value.get("N")).build();
    }

    if (value.containsKey("B")) {
      return AttributeValue.builder()
          .b(SdkBytes.fromByteArray(Base64.getUrlDecoder().decode(value.get("B"))))
          .build();
    }

    throw new IllegalArgumentException("Unsupported cursor attribute value type.");
  }
}
