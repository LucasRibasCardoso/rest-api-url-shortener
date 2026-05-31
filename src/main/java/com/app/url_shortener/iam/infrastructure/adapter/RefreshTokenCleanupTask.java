package com.app.url_shortener.iam.infrastructure.adapter;

import com.app.url_shortener.iam.infrastructure.persistence.repository.RefreshTokenJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupTask {

  private final RefreshTokenJpaRepository refreshTokenJpaRepository;

  /**
   * Roda automaticamente todos os dias às 03:00 da manhã.
   * O formato Cron é: "Segundo Minuto Hora Dia Mes DiaDaSemana"
   * O formato Cron é: "Segundo Minuto Hora Dia Mes DiaDaSemana"
   */
  @Scheduled(cron = "0 0 3 * * *")
  @Transactional
  public void cleanupExpiredTokens() {
    log.info("Iniciando rotina de limpeza de Refresh Tokens expirados/revogados...");

    Instant cutoffDate = Instant.now().minus(7, ChronoUnit.DAYS);
    int deletedCount = refreshTokenJpaRepository.deleteExpiredTokensBefore(cutoffDate);

    log.info("Rotina de limpeza concluída. {} tokens antigos foram deletados permanentemente do banco de dados.", deletedCount);
  }
}
