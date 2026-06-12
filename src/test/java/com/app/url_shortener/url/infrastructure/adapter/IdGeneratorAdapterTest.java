package com.app.url_shortener.url.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.app.url_shortener.url.application.port.output.IdBlockAllocatorPort;
import com.app.url_shortener.url.infrastructure.config.IdGeneratorProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Adaptador de Geração de IDs")
class IdGeneratorAdapterTest {

  private static final long BLOCK_SIZE = 10L;

  @Mock
  private IdBlockAllocatorPort idBlockAllocatorPort;

  @Nested
  @DisplayName("Geração sequencial")
  class SequentialGenerationTests {

    @Test
    @DisplayName("Deve alocar o primeiro bloco no primeiro generateId")
    void shouldAllocateFirstBlockOnFirstGenerateId() {
      // 1. Arrange
      when(idBlockAllocatorPort.allocateBlock(BLOCK_SIZE)).thenReturn(1L);
      var adapter = new IdGeneratorAdapter(idBlockAllocatorPort, properties(BLOCK_SIZE));

      // 2. Act
      var id = adapter.generateId();

      // 3. Assert
      assertThat(id).isEqualTo(1L);
      verify(idBlockAllocatorPort).allocateBlock(BLOCK_SIZE);
      verifyNoMoreInteractions(idBlockAllocatorPort);
    }

    @Test
    @DisplayName("Deve gerar IDs sequenciais dentro do mesmo bloco")
    void shouldGenerateSequentialIdsInsideSameBlock() {
      // 1. Arrange
      when(idBlockAllocatorPort.allocateBlock(BLOCK_SIZE)).thenReturn(1L);
      var adapter = new IdGeneratorAdapter(idBlockAllocatorPort, properties(BLOCK_SIZE));

      // 2. Act
      var firstId = adapter.generateId();
      var secondId = adapter.generateId();
      var thirdId = adapter.generateId();

      // 3. Assert
      assertThat(List.of(firstId, secondId, thirdId))
          .containsExactly(1L, 2L, 3L);
      verify(idBlockAllocatorPort).allocateBlock(BLOCK_SIZE);
      verifyNoMoreInteractions(idBlockAllocatorPort);
    }

    @Test
    @DisplayName("Deve alocar novo bloco somente quando o bloco atual esgotar")
    void shouldAllocateNewBlockOnlyWhenCurrentBlockIsExhausted() {
      // 1. Arrange
      when(idBlockAllocatorPort.allocateBlock(BLOCK_SIZE)).thenReturn(1L, 11L);
      var adapter = new IdGeneratorAdapter(idBlockAllocatorPort, properties(BLOCK_SIZE));

      // 2. Act
      var generatedIds = LongStream.rangeClosed(1, 11)
          .map(ignored -> adapter.generateId())
          .boxed()
          .toList();

      // 3. Assert
      assertThat(generatedIds)
          .containsExactlyElementsOf(expectedRange(1L, 11L));
      verify(idBlockAllocatorPort, times(2)).allocateBlock(BLOCK_SIZE);
      verifyNoMoreInteractions(idBlockAllocatorPort);
    }
  }

  @Nested
  @DisplayName("Geração concorrente")
  class ConcurrentGenerationTests {

    @Test
    @DisplayName("Deve gerar IDs únicos sob concorrência")
    void shouldGenerateUniqueIdsUnderConcurrency() throws Exception {
      // 1. Arrange
      var blockSize = 5L;
      var totalIds = 1_000;
      var threadPoolSize = 20;
      var expectedBlocks = expectedBlocks(totalIds, blockSize);
      var nextBaseId = new AtomicLong(1L);
      when(idBlockAllocatorPort.allocateBlock(blockSize))
          .thenAnswer(invocation -> nextBaseId.getAndAdd(blockSize));
      var adapter = new IdGeneratorAdapter(idBlockAllocatorPort, properties(blockSize));

      // 2. Act
      var generatedIds = generateConcurrently(adapter, totalIds, threadPoolSize);

      // 3. Assert
      assertThat(generatedIds).hasSize(totalIds);
      assertThat(generatedIds.stream().distinct().count()).isEqualTo(totalIds);
      assertThat(generatedIds)
          .containsExactlyInAnyOrderElementsOf(expectedRange(1L, totalIds));
      verify(idBlockAllocatorPort, times(expectedBlocks)).allocateBlock(blockSize);
      verifyNoMoreInteractions(idBlockAllocatorPort);
    }

    @Test
    @DisplayName("Deve preservar os limites dos blocos sob concorrência")
    void shouldPreserveBlockBoundariesUnderConcurrency() throws Exception {
      // 1. Arrange
      var blockSize = 2L;
      var totalIds = 100;
      var threadPoolSize = 20;
      var expectedBlocks = expectedBlocks(totalIds, blockSize);
      var nextBaseId = new AtomicLong(1L);
      when(idBlockAllocatorPort.allocateBlock(blockSize))
          .thenAnswer(invocation -> nextBaseId.getAndAdd(blockSize));
      var adapter = new IdGeneratorAdapter(idBlockAllocatorPort, properties(blockSize));

      // 2. Act
      var generatedIds = generateConcurrently(adapter, totalIds, threadPoolSize);

      // 3. Assert
      assertThat(generatedIds).hasSize(totalIds);
      assertThat(generatedIds.stream().distinct().count()).isEqualTo(totalIds);
      assertThat(generatedIds)
          .containsExactlyInAnyOrderElementsOf(expectedRange(1L, totalIds));
      assertThat(generatedIds).allSatisfy(id -> assertThat(id).isBetween(1L, (long) totalIds));
      verify(idBlockAllocatorPort, times(expectedBlocks)).allocateBlock(blockSize);
      verifyNoMoreInteractions(idBlockAllocatorPort);
    }
  }

  @Nested
  @DisplayName("Falhas")
  class FailureTests {

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    @DisplayName("Deve falhar se contador retornar baseId inválido")
    void shouldFailWhenCounterReturnsInvalidBaseId(long invalidBaseId) {
      // 1. Arrange
      when(idBlockAllocatorPort.allocateBlock(BLOCK_SIZE)).thenReturn(invalidBaseId);
      var adapter = new IdGeneratorAdapter(idBlockAllocatorPort, properties(BLOCK_SIZE));

      // 2. Act / 3. Assert
      assertThatThrownBy(adapter::generateId)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Allocated ID block must start with a positive value");

      verify(idBlockAllocatorPort).allocateBlock(BLOCK_SIZE);
      verifyNoMoreInteractions(idBlockAllocatorPort);
    }
  }

  private static List<Long> expectedRange(long startInclusive, long endInclusive) {
    return LongStream.rangeClosed(startInclusive, endInclusive)
        .boxed()
        .toList();
  }

  private static IdGeneratorProperties properties(long blockSize) {
    return new IdGeneratorProperties(blockSize, "url-id");
  }

  private static int expectedBlocks(int totalIds, long blockSize) {
    return Math.toIntExact((totalIds + blockSize - 1) / blockSize);
  }

  private static List<Long> generateConcurrently(
      IdGeneratorAdapter adapter,
      int totalIds,
      int threadPoolSize) throws Exception {

    var executor = Executors.newFixedThreadPool(threadPoolSize);
    var startLatch = new CountDownLatch(1);

    try {
      var futures = new ArrayList<java.util.concurrent.Future<Long>>();

      for (int i = 0; i < totalIds; i++) {
        futures.add(executor.submit(() -> {
          startLatch.await();
          return adapter.generateId();
        }));
      }

      startLatch.countDown();

      var generatedIds = new ArrayList<Long>();
      for (var future : futures) {
        generatedIds.add(future.get(5, TimeUnit.SECONDS));
      }

      return generatedIds;
    } finally {
      executor.shutdownNow();
    }
  }
}
