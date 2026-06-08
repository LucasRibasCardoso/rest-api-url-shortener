package com.app.url_shortener.url.application.port.output;

public interface IdBlockAllocatorPort {
  long allocateBlock(long blockSize);
}
