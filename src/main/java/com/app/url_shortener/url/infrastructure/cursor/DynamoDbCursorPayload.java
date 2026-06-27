package com.app.url_shortener.url.infrastructure.cursor;

import java.util.Map;

public record DynamoDbCursorPayload(int version, Map<String, Map<String, String>> key) {}
