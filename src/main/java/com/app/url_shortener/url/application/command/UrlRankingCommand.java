package com.app.url_shortener.url.application.command;

import java.util.UUID;

public record UrlRankingCommand(UUID userId, int rankingSize) {}
