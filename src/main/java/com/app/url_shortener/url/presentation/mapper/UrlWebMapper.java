package com.app.url_shortener.url.presentation.mapper;

import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.url.application.command.*;
import com.app.url_shortener.url.application.result.*;
import com.app.url_shortener.url.presentation.dto.request.ShortenUrlRequestDto;
import com.app.url_shortener.url.presentation.dto.response.*;
import com.app.url_shortener.url.presentation.dto.response.UrlPageResponseDto;
import java.util.UUID;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UrlWebMapper {

  @Mapping(target = "userId", source = "userId")
  @Mapping(target = "originalUrl", source = "request.originalUrl")
  ShortenUrlCommand toShortenUrlCommand(
      ShortenUrlRequestDto request, UUID userId, PlanType planType);

  ResolveUrlCommand toResolveUrlCommand(String shortCode);

  UrlRankingCommand toUrlRankingCommand(UUID userId, int rankingSize);

  DeleteUrlCommand toDeleteUrlCommand(UUID requesterId, String shortCode, boolean canDeleteAny);

  UrlDetailsCommand toUrlDetailsCommand(UUID requesterId, String shortCode, boolean canReadAny);

  FindAllUrlsByUserIdCommand toFindAllUrlsByUserIdCommand(
      UUID userId, int limit, String cursor, UrlStatusFilter status);

  @Mapping(target = "shortUrl", source = "shortCode", qualifiedByName = "toFullShortUrl")
  @Mapping(target = "status", constant = "ACTIVE")
  UrlResponseDto toUrlResponse(ShortenUrlResult result, @Context String baseUrl);

  UrlDetailsResponseDto toUrlDetailsResponse(UrlDetailsResult result);

  @Mapping(target = "shortUrl", source = "shortCode", qualifiedByName = "toFullShortUrl")
  UrlResponseDto toUrlResponse(UrlListItemResult result, @Context String baseUrl);

  @Mapping(target = "shortUrl", source = "shortCode", qualifiedByName = "toFullShortUrl")
  UrlRankingItemResponseDto toUrlRankingItemResponse(
      UrlRankingItemResult result, @Context String baseUrl);

  @Mapping(target = "urls", source = "urls")
  UrlRankingResponseDto toUrlRankingResponse(UrlRankingResult result, @Context String baseUrl);

  UrlPageResponseDto toUrlPagelResponse(UrlPageResult result, @Context String baseUrl);

  @Named("toFullShortUrl")
  default String toFullShortUrl(String shortCode, @Context String baseUrl) {
    if (shortCode == null || baseUrl == null) {
      return null;
    }

    return baseUrl.endsWith("/") ? baseUrl + "r/" + shortCode : baseUrl + "/r/" + shortCode;
  }
}
