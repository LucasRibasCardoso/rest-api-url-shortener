package com.app.url_shortener.url.presentation.mapper;

import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.url.application.command.*;
import com.app.url_shortener.url.application.result.*;
import com.app.url_shortener.url.presentation.dto.request.ShortenUrlRequestDto;
import com.app.url_shortener.url.presentation.dto.response.PageUrlResponseDto;
import com.app.url_shortener.url.presentation.dto.response.UrlDetailsResponseDto;
import com.app.url_shortener.url.presentation.dto.response.UrlRankingItemResponseDto;
import com.app.url_shortener.url.presentation.dto.response.UrlRankingResponseDto;
import com.app.url_shortener.url.presentation.dto.response.UrlResponseDto;
import java.util.UUID;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UrlWebMapper {

  @Mapping(target = "userId", source = "userId")
  @Mapping(target = "originalUrl", source = "request.originalUrl")
  ShortenUrlCommand toCommand(ShortenUrlRequestDto request, UUID userId, PlanType planType);

  UrlDetailsCommand toCommand(UUID requesterId, String shortCode, boolean canReadAny);

  DeleteUrlCommand toCommandDelete(UUID requesterId, String shortCode, boolean canDeleteAny);

  FindAllUrlsByUserIdCommand toCommand(UUID userId, int limit, String cursor, UrlStatusFilter status);

  ResolveUrlCommand toCommand(String shortCode);

  UrlRankingCommand toCommandRanking(UUID userId, int rankingSize);

  @Mapping(target = "shortUrl", source = "shortCode", qualifiedByName = "toFullShortUrl")
  @Mapping(target = "status", constant = "ACTIVE")
  UrlResponseDto toResponse(ShortenUrlResult result, @Context String baseUrl);

  UrlDetailsResponseDto toResponse(UrlDetailsResult result);

  @Mapping(target = "shortUrl", source = "shortCode", qualifiedByName = "toFullShortUrl")
  UrlResponseDto toResponse(UrlListItemResult result, @Context String baseUrl);

  @Mapping(target = "shortUrl", source = "shortCode", qualifiedByName = "toFullShortUrl")
  UrlRankingItemResponseDto toRankingItemResponse(UrlRankingItemResult result, @Context String baseUrl);

  @Mapping(target = "urls", source = "urls")
  UrlRankingResponseDto toRankingResponse(UrlRankingResult result, @Context String baseUrl);

  PageUrlResponseDto toResponse(PageUrlResult result, @Context String baseUrl);

  @Named("toFullShortUrl")
  default String toFullShortUrl(String shortCode, @Context String baseUrl) {
    if (shortCode == null || baseUrl == null) {
      return null;
    }

    return baseUrl.endsWith("/") ? baseUrl + "r/" + shortCode : baseUrl + "/r/" + shortCode;
  }
}
