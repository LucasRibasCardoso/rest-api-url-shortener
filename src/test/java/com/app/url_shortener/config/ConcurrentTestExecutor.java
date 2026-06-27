package com.app.url_shortener.config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public final class ConcurrentTestExecutor {

  private static final long READY_TIMEOUT_SECONDS = 5;
  private static final long RESULT_TIMEOUT_SECONDS = 15;

  private ConcurrentTestExecutor() {}

  public static <T> List<T> execute(int workers, Callable<T> operation) throws Exception {
    return execute(workers, ignored -> operation.call());
  }

  public static <T> List<T> execute(int workers, ConcurrentOperation<T> operation)
      throws Exception {
    if (workers <= 0) {
      throw new IllegalArgumentException("Workers must be greater than zero");
    }

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var workersReady = new CountDownLatch(workers);
      var startSignal = new CountDownLatch(1);

      var futures = new ArrayList<Future<T>>(workers);

      for (int worker = 1; worker <= workers; worker++) {
        int currentWorker = worker;

        futures.add(
            executor.submit(
                () -> {
                  workersReady.countDown();

                  if (!startSignal.await(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                        "Concurrent worker did not receive the start signal");
                  }

                  return operation.execute(currentWorker);
                }));
      }

      if (!workersReady.await(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Concurrent workers did not become ready");
      }

      startSignal.countDown();

      var results = new ArrayList<T>(workers);

      for (var future : futures) {
        try {
          results.add(future.get(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          throw exception;
        } catch (ExecutionException exception) {
          throw new IllegalStateException("Concurrent operation failed", exception.getCause());
        } catch (TimeoutException exception) {
          throw new IllegalStateException("Concurrent operation did not complete", exception);
        }
      }

      return results;
    }
  }

  @FunctionalInterface
  public interface ConcurrentOperation<T> {
    T execute(int worker) throws Exception;
  }
}
