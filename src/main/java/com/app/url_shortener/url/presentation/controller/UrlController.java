package com.app.url_shortener.url.presentation.controller;

import com.app.url_shortener.security.principal.UserPrincipal;
import com.app.url_shortener.url.application.command.DeleteUrlCommand;
import com.app.url_shortener.url.application.command.FindAllUrlsByUserIdCommand;
import com.app.url_shortener.url.application.command.ShortenUrlCommand;
import com.app.url_shortener.url.application.command.UrlDetailsCommand;
import com.app.url_shortener.url.application.result.PageUrlResult;
import com.app.url_shortener.url.application.result.ShortenUrlResult;
import com.app.url_shortener.url.application.result.UrlDetailsResult;
import com.app.url_shortener.url.application.usecase.DeleteUrlUseCase;
import com.app.url_shortener.url.application.usecase.FindAllUrlsByUserIdUseCase;
import com.app.url_shortener.url.application.usecase.FindUrlDetailsUseCase;
import com.app.url_shortener.url.application.usecase.ShortenUrlUseCase;
import com.app.url_shortener.url.presentation.dto.request.ShortenUrlRequestDto;
import com.app.url_shortener.url.presentation.dto.response.PageUrlResponseDto;
import com.app.url_shortener.url.presentation.dto.response.UrlDetailsResponseDto;
import com.app.url_shortener.url.presentation.dto.response.UrlResponseDto;
import com.app.url_shortener.url.presentation.mapper.UrlWebMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/urls")
public class UrlController {

  private static final String SHORT_CODE_PATH = "/{shortCode:[a-zA-Z0-9]{1,64}}";

  private final String baseUrl;
  private final UrlWebMapper urlWebMapper;
  private final DeleteUrlUseCase deleteUrlUseCase;
  private final ShortenUrlUseCase shortenUrlUseCase;
  private final FindUrlDetailsUseCase findUrlDetailsUseCase;
  private final FindAllUrlsByUserIdUseCase findAllUrlsByUserIdUseCase;

  public UrlController(
          @Value("${app.base-url}") String baseUrl,
          UrlWebMapper urlWebMapper,
          DeleteUrlUseCase deleteUrlUseCase,
          ShortenUrlUseCase shortenUrlUseCase,
          FindUrlDetailsUseCase findUrlDetailsUseCase,
          FindAllUrlsByUserIdUseCase findAllUrlsByUserIdUseCase) {
    this.baseUrl = baseUrl;
    this.urlWebMapper = urlWebMapper;
    this.deleteUrlUseCase = deleteUrlUseCase;
    this.shortenUrlUseCase = shortenUrlUseCase;
    this.findUrlDetailsUseCase = findUrlDetailsUseCase;
    this.findAllUrlsByUserIdUseCase = findAllUrlsByUserIdUseCase;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('url:create')")
  public ResponseEntity<UrlResponseDto> shortenUrl(
          @Valid @RequestBody ShortenUrlRequestDto request,
          @AuthenticationPrincipal UserPrincipal user) {
    ShortenUrlCommand command = urlWebMapper.toCommand(request, user.getId(), user.getPlan());
    ShortenUrlResult result = shortenUrlUseCase.execute(command);
    UrlResponseDto response = urlWebMapper.toResponse(result, baseUrl);
    return ResponseEntity.created(URI.create(response.shortUrl())).body(response);
  }

  @GetMapping(SHORT_CODE_PATH)
  @PreAuthorize("hasAuthority('url:read:own') or hasAuthority('url:read:any')")
  public ResponseEntity<UrlDetailsResponseDto> findUrlDetails(
          @PathVariable String shortCode,
          @AuthenticationPrincipal UserPrincipal user) {
    boolean canReadAny = user.getAuthorities().stream().anyMatch(a -> Objects.equals(a.getAuthority(), "url:read:any"));
    UrlDetailsCommand command = urlWebMapper.toCommand(user.getId(), shortCode, canReadAny);
    UrlDetailsResult result = findUrlDetailsUseCase.execute(command);
    UrlDetailsResponseDto response = urlWebMapper.toResponse(result);
    return ResponseEntity.ok(response);
  }

  @DeleteMapping(SHORT_CODE_PATH)
  @PreAuthorize("hasAuthority('url:delete:own') or hasAuthority('url:delete:any')")
  public ResponseEntity<Void> deleteUrl(
          @PathVariable String shortCode,
          @AuthenticationPrincipal UserPrincipal user) {
    boolean canDeleteAny = user.getAuthorities().stream().anyMatch(a -> Objects.equals(a.getAuthority(), "url:delete:any"));
    DeleteUrlCommand command = urlWebMapper.toCommandDelete(user.getId(), shortCode, canDeleteAny);
    deleteUrlUseCase.execute(command);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me")
  @PreAuthorize("hasAuthority('url:list:own')")
  public ResponseEntity<PageUrlResponseDto> findAllMyUrls(
          @AuthenticationPrincipal UserPrincipal user,
          @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
          @RequestParam(required = false) String cursor) {
    FindAllUrlsByUserIdCommand command = urlWebMapper.toCommand(user.getId(), limit, cursor);
    PageUrlResult result = findAllUrlsByUserIdUseCase.execute(command);
    PageUrlResponseDto response = urlWebMapper.toResponse(result, baseUrl);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/users/{userId}")
  @PreAuthorize("hasAuthority('url:list:any')")
  public ResponseEntity<PageUrlResponseDto> findAllUrlsByUserId(
          @PathVariable UUID userId,
          @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
          @RequestParam(required = false) String cursor) {
    FindAllUrlsByUserIdCommand command = urlWebMapper.toCommand(userId, limit, cursor);
    PageUrlResult result = findAllUrlsByUserIdUseCase.execute(command);
    PageUrlResponseDto response = urlWebMapper.toResponse(result, baseUrl);
    return ResponseEntity.ok(response);
  }
}
