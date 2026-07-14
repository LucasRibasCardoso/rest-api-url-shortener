package com.app.url_shortener.shared.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@ConditionalOnProperty(prefix = "app.openapi", name = "enabled", havingValue = "true")
public class DocumentationController {

  private static final Resource SCALAR_DOCUMENTATION = new ClassPathResource("openapi/scalar.html");

  @GetMapping(value = "/docs", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<Resource> showDocumentation() {
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(SCALAR_DOCUMENTATION);
  }
}
