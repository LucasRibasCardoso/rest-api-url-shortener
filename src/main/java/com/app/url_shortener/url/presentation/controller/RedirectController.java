package com.app.url_shortener.url.presentation.controller;

import com.app.url_shortener.url.application.command.ResolveUrlCommand;
import com.app.url_shortener.url.application.result.ResolveUrlResult;
import com.app.url_shortener.url.application.usecase.ResolveUrlUseCase;
import com.app.url_shortener.url.presentation.mapper.UrlWebMapper;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/r")
@RestController
@RequiredArgsConstructor
public class RedirectController {

  private static final String SHORT_CODE_PATH = "/{shortCode:[a-zA-Z0-9]{1,64}}";

  private final UrlWebMapper urlWebMapper;
  private final ResolveUrlUseCase resolveUrlUseCase;

  @GetMapping(SHORT_CODE_PATH)
  public ResponseEntity<?> redirectToOriginalUrl(@PathVariable String shortCode) {
    ResolveUrlCommand command = urlWebMapper.toResolveUrlCommand(shortCode);
    ResolveUrlResult result = resolveUrlUseCase.execute(command);
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(result.originalUrl()))
        .build();
  }
}
