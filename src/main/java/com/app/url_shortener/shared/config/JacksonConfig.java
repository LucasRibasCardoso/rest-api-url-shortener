package com.app.url_shortener.shared.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;

@Configuration
public class JacksonConfig {

  @Bean
  public JsonMapperBuilderCustomizer problemMapperCustomizer() {
    return builder -> builder.addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);
  }
}
