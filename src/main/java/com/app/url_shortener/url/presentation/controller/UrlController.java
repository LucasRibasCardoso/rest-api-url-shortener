package com.app.url_shortener.url.presentation.controller;

import com.app.url_shortener.security.principal.UserPrincipal;
import com.app.url_shortener.url.application.command.*;
import com.app.url_shortener.url.application.result.*;
import com.app.url_shortener.url.application.usecase.*;
import com.app.url_shortener.url.presentation.dto.request.ShortenUrlRequestDto;
import com.app.url_shortener.url.presentation.dto.response.*;
import com.app.url_shortener.url.presentation.mapper.UrlWebMapper;
import com.app.url_shortener.url.presentation.validator.ValidRankingSize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
  private final FindTopAccessedUrlsByUserIdUseCase findTopAccessedUrlsByUserIdUseCase;

  public UrlController(
      @Value("${app.base-url}") String baseUrl,
      UrlWebMapper urlWebMapper,
      DeleteUrlUseCase deleteUrlUseCase,
      ShortenUrlUseCase shortenUrlUseCase,
      FindUrlDetailsUseCase findUrlDetailsUseCase,
      FindAllUrlsByUserIdUseCase findAllUrlsByUserIdUseCase,
      FindTopAccessedUrlsByUserIdUseCase findTopAccessedUrlsByUserIdUseCase) {
    this.baseUrl = baseUrl;
    this.urlWebMapper = urlWebMapper;
    this.deleteUrlUseCase = deleteUrlUseCase;
    this.shortenUrlUseCase = shortenUrlUseCase;
    this.findUrlDetailsUseCase = findUrlDetailsUseCase;
    this.findAllUrlsByUserIdUseCase = findAllUrlsByUserIdUseCase;
    this.findTopAccessedUrlsByUserIdUseCase = findTopAccessedUrlsByUserIdUseCase;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('url:create')")
  public ResponseEntity<UrlResponseDto> shortenUrl(
      @Valid @RequestBody ShortenUrlRequestDto request,
      @AuthenticationPrincipal UserPrincipal user) {
    ShortenUrlCommand command = urlWebMapper.toShortenUrlCommand(request, user.getId(), user.getPlan());
    ShortenUrlResult result = shortenUrlUseCase.execute(command);
    UrlResponseDto response = urlWebMapper.toUrlResponse(result, baseUrl);
    return ResponseEntity.created(URI.create(response.shortUrl())).body(response);
  }

  @GetMapping(SHORT_CODE_PATH)
  @PreAuthorize("hasAuthority('url:read:own') or hasAuthority('url:read:any')")
  public ResponseEntity<UrlDetailsResponseDto> findUrlDetails(
      @PathVariable String shortCode, @AuthenticationPrincipal UserPrincipal user) {
    boolean canReadAny = hasAuthority(user, "url:read:any");
    UrlDetailsCommand command = urlWebMapper.toUrlDetailsCommand(user.getId(), shortCode, canReadAny);
    UrlDetailsResult result = findUrlDetailsUseCase.execute(command);
    UrlDetailsResponseDto response = urlWebMapper.toUrlDetailsResponse(result);
    return ResponseEntity.ok(response);
  }

  @DeleteMapping(SHORT_CODE_PATH)
  @PreAuthorize("hasAuthority('url:delete:own') or hasAuthority('url:delete:any')")
  public ResponseEntity<Void> deleteUrl(
      @PathVariable String shortCode, @AuthenticationPrincipal UserPrincipal user) {
    boolean canDeleteAny = hasAuthority(user, "url:delete:any");
    DeleteUrlCommand command = urlWebMapper.toDeleteUrlCommand(user.getId(), shortCode, canDeleteAny);
    deleteUrlUseCase.execute(command);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/users/{userId}")
  @PreAuthorize("hasAuthority('url:list:any')")
  public ResponseEntity<PageUrlResponseDto> findAllUrlsByUserId(
      @PathVariable UUID userId,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
      @RequestParam(defaultValue = "ACTIVE") UrlStatusFilter status,
      @RequestParam(required = false) String cursor) {
    FindAllUrlsByUserIdCommand command =
        urlWebMapper.toFindAllUrlsByUserIdCommand(userId, limit, cursor, status);
    PageUrlResult result = findAllUrlsByUserIdUseCase.execute(command);
    PageUrlResponseDto response = urlWebMapper.toPageUrlResponse(result, baseUrl);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/me")
  @PreAuthorize("hasAuthority('url:list:own')")
  public ResponseEntity<PageUrlResponseDto> findAllMyUrls(
      @AuthenticationPrincipal UserPrincipal user,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
      @RequestParam(defaultValue = "ACTIVE") UrlStatusFilter status,
      @RequestParam(required = false) String cursor) {
    FindAllUrlsByUserIdCommand command =
        urlWebMapper.toFindAllUrlsByUserIdCommand(user.getId(), limit, cursor, status);
    PageUrlResult result = findAllUrlsByUserIdUseCase.execute(command);
    PageUrlResponseDto response = urlWebMapper.toPageUrlResponse(result, baseUrl);
    return ResponseEntity.ok(response);
  }

  @GetMapping("me/ranking")
  @PreAuthorize("hasAuthority('url:ranking:own')")
  public ResponseEntity<UrlRankingResponseDto> findMyTopUrls(
      @RequestParam(defaultValue = "3") @ValidRankingSize int rankingSize,
      @AuthenticationPrincipal UserPrincipal user) {
    UrlRankingCommand command = urlWebMapper.toUrlRankingCommand(user.getId(), rankingSize);
    UrlRankingResult result = findTopAccessedUrlsByUserIdUseCase.execute(command);
    UrlRankingResponseDto response = urlWebMapper.toUrlRankingResponse(result, baseUrl);
    return ResponseEntity.ok(response);
  }

  private boolean hasAuthority(UserPrincipal user, String authority) {
    return user.getAuthorities().stream()
        .anyMatch(a -> Objects.equals(a.getAuthority(), authority));
  }
}
