package com.app.url_shortener.shared.idempotency.valueobjects;


public record RequestFingerprint(
        String principalScope,
        String method,
        String route,
        String bodyHash
) {

    public static RequestFingerprint of(String principalScope, String method, String route, String bodyHash) {
        return new RequestFingerprint(principalScope, method, route, bodyHash);
    }
}
