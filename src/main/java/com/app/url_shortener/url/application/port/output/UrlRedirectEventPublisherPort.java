package com.app.url_shortener.url.application.port.output;

import com.app.url_shortener.url.application.event.UrlRedirectedEvent;

public interface UrlRedirectEventPublisherPort {
  void publishAsync(UrlRedirectedEvent event);
}
