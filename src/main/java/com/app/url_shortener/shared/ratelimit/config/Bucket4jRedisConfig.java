package com.app.url_shortener.shared.ratelimit.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import java.time.Duration;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@Configuration
public class Bucket4jRedisConfig {

  private static final int MAX_CAS_RETRIES = 5;
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(2);
  private static final Duration BUCKET_EXPIRATION_MARGIN = Duration.ofSeconds(10);

  /**
   * Cria o bean que fornece a conexão Lettuce utilizada pelo Bucket4j. Detecta se o cliente nativo
   * do Lettuce é standalone ou cluster e retorna um wrapper com a conexão apropriada. Lança
   * IllegalStateException quando o tipo de cliente não é suportado.
   *
   * @param connectionFactory factory do Spring Data Redis para obter o cliente nativo
   * @return wrapper contendo a conexão standalone ou cluster para uso do Bucket4j
   */
  @Bean
  public Bucket4jRedisConnection bucket4jRedisConnection(
      LettuceConnectionFactory connectionFactory) {

    AbstractRedisClient nativeClient = connectionFactory.getRequiredNativeClient();
    RedisCodec<String, byte[]> codec = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);

    if (nativeClient instanceof RedisClient redisClient) {
      return Bucket4jRedisConnection.standalone(redisClient.connect(codec));
    }

    if (nativeClient instanceof RedisClusterClient redisClusterClient) {
      return Bucket4jRedisConnection.cluster(redisClusterClient.connect(codec));
    }

    throw new IllegalStateException(
        "Unsupported Lettuce native client for Bucket4j rate limit: "
            + nativeClient.getClass().getName());
  }

  /**
   * Cria o bean do ProxyManager do Bucket4j, configurado para usar a conexão Redis fornecida.
   * Configura estratégia de expiração baseada no refill, número máximo de tentativas CAS e timeout
   * de requisição para operação robusta sob concorrência.
   *
   * @param redisConnection wrapper com a conexão Lettuce (standalone ou cluster)
   * @return LettuceBasedProxyManager configurado para ser usado pelo Bucket4j
   */
  @Bean
  public LettuceBasedProxyManager<String> bucket4jProxyManager(
      Bucket4jRedisConnection redisConnection) {

    if (redisConnection.standaloneConnection() != null) {
      return Bucket4jLettuce.casBasedBuilder(redisConnection.standaloneConnection())
          .expirationAfterWrite(expirationAfterWriteStrategy())
          .maxRetries(MAX_CAS_RETRIES)
          .requestTimeout(REQUEST_TIMEOUT)
          .build();
    }

    if (redisConnection.clusterConnection() != null) {
      return Bucket4jLettuce.casBasedBuilder(redisConnection.clusterConnection())
          .expirationAfterWrite(expirationAfterWriteStrategy())
          .maxRetries(MAX_CAS_RETRIES)
          .requestTimeout(REQUEST_TIMEOUT)
          .build();
    }

    throw new IllegalStateException("Bucket4j Redis connection was not initialized");
  }

  /**
   * Cria um bean que fecha a conexão Redis quando a aplicação é encerrada. Verifica se a conexão é
   * standalone ou cluster e a fecha corretamente, liberando os recursos de forma segura durante o
   * shutdown do Spring.
   *
   * @param redisConnection a conexão Redis configurada para o Bucket4j
   * @return um DisposableBean que fecha a conexão ao encerrar a aplicação
   */
  @Bean
  public DisposableBean bucket4jRedisConnectionCloser(Bucket4jRedisConnection redisConnection) {
    return () -> {
      StatefulConnection<String, byte[]> connection =
          redisConnection.standaloneConnection() != null
              ? redisConnection.standaloneConnection()
              : redisConnection.clusterConnection();

      if (connection != null && connection.isOpen()) {
        try {
          connection.close();
        } catch (Exception e) {
        }
      }
    };
  }

  /**
   * Cria a estratégia de expiração usada pelo Bucket4j. Baseia a expiração no tempo necessário para
   * encher o bucket até o máximo.
   *
   * @return estratégia de expiração para o Bucket4j
   */
  private ExpirationAfterWriteStrategy expirationAfterWriteStrategy() {
    return ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
        BUCKET_EXPIRATION_MARGIN);
  }

  /**
   * Wrapper que encapsula a conexão Lettuce utilizada pelo Bucket4j. Garante que apenas uma das
   * conexões (standalone ou cluster) esteja presente. Use os métodos de fábrica para instanciar.
   *
   * @param standaloneConnection conexão Lettuce standalone; deve ser {@code null} quando {@code
   *     clusterConnection} estiver presente
   * @param clusterConnection conexão Lettuce em modo cluster; deve ser {@code null} quando {@code
   *     standaloneConnection} estiver presente
   */
  public record Bucket4jRedisConnection(
      StatefulRedisConnection<String, byte[]> standaloneConnection,
      StatefulRedisClusterConnection<String, byte[]> clusterConnection) {

    public Bucket4jRedisConnection {
      boolean hasStandalone = standaloneConnection != null;
      boolean hasCluster = clusterConnection != null;

      if (hasStandalone == hasCluster) {
        throw new IllegalArgumentException("Only one Bucket4j Redis connection can be used");
      }
    }

    public static Bucket4jRedisConnection standalone(
        StatefulRedisConnection<String, byte[]> connection) {
      return new Bucket4jRedisConnection(connection, null);
    }

    public static Bucket4jRedisConnection cluster(
        StatefulRedisClusterConnection<String, byte[]> connection) {
      return new Bucket4jRedisConnection(null, connection);
    }
  }
}
