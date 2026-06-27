package com.app.url_shortener.url.infrastructure.adapter;

import com.app.url_shortener.url.application.port.output.IdBlockAllocatorPort;
import com.app.url_shortener.url.application.port.output.IdGeneratorPort;
import com.app.url_shortener.url.infrastructure.config.IdGeneratorProperties;
import org.springframework.stereotype.Service;

@Service
public class IdGeneratorAdapter implements IdGeneratorPort {

  private final IdBlockAllocatorPort idBlockAllocatorPort;
  private final long blockSize;
  private IdBlock currentIdBlock;

  public IdGeneratorAdapter(
      IdBlockAllocatorPort idBlockAllocatorPort, IdGeneratorProperties properties) {
    this.idBlockAllocatorPort = idBlockAllocatorPort;
    this.blockSize = properties.blockSize();
    this.currentIdBlock = IdBlock.requiringAllocation(blockSize);
  }

  /*
   * Metodo synchronized para garantir consumo seguro do bloco de IDs em ambientes multi-thread.
   *
   * 1. verificar se o bloco acabou;
   * 2. alocar novo bloco se necessário;
   * 3. calcular o próximo ID;
   * 4. avançar o offset.
   *
   * Isso evita o bug de concorrência onde uma thread poderia usar o offset
   * de um bloco antigo com o baseId de um bloco novo.
   */
  @Override
  public synchronized long generateId() {
    if (currentIdBlock.isExhausted()) {
      currentIdBlock = allocateNewBlock();
    }

    long generatedId = currentIdBlock.nextId();
    currentIdBlock = currentIdBlock.advanceToNextId();

    return generatedId;
  }

  // Reserva uma faixa exclusiva de IDs no contador global.
  private IdBlock allocateNewBlock() {
    long baseId = idBlockAllocatorPort.allocateBlock(blockSize);

    if (baseId <= 0) {
      throw new IllegalStateException("Allocated ID block must start with a positive value");
    }

    return IdBlock.from(baseId, blockSize);
  }

  /*
   * Representa uma faixa de IDs reservada para consumo local.
   *
   * baseId:
   *   primeiro ID do bloco.
   *
   * nextOffset:
   *   próxima posição disponível dentro do bloco.
   *
   * blockSize:
   *   tamanho total do bloco reservado.
   *
   * Exemplo:
   *   baseId = 100
   *   blockSize = 3
   *
   *   nextOffset = 0 -> próximo ID = 100
   *   nextOffset = 1 -> próximo ID = 101
   *   nextOffset = 2 -> próximo ID = 102
   *   nextOffset = 3 -> bloco esgotado
   */
  private record IdBlock(long baseId, long nextOffset, long blockSize) {

    /*
     * Cria um bloco inicial esgotado para forçar a alocação de um bloco real
     * no primeiro uso do gerador.
     */
    private static IdBlock requiringAllocation(long blockSize) {
      return new IdBlock(0, blockSize, blockSize);
    }

    /*
     * Cria um bloco real retornado pelo contador global.
     * O offset começa em 0 porque nenhum ID desse bloco foi consumido ainda.
     */
    private static IdBlock from(long baseId, long blockSize) {
      return new IdBlock(baseId, 0, blockSize);
    }

    /*
     * O bloco está esgotado quando o próximo offset disponível é igual ou maior
     * que o tamanho total do bloco.
     */
    private boolean isExhausted() {
      return nextOffset >= blockSize;
    }

    /*
     * Calcula o próximo ID absoluto usando o início do bloco + offset atual.
     *
     * Math.addExact lança ArithmeticException em caso de overflow,
     * evitando gerar IDs incorretos silenciosamente.
     */
    private long nextId() {
      return Math.addExact(baseId, nextOffset);
    }

    /*
     * Retorna uma nova instância apontando para o próximo offset.
     *
     */
    private IdBlock advanceToNextId() {
      return new IdBlock(baseId, nextOffset + 1, blockSize);
    }
  }
}
