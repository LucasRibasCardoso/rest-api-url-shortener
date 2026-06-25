  # Plano — Cobertura do pipeline Outbox → SQS → Consumer → SES

  ## Resumo

  Expandir a cobertura com infraestrutura real PostgreSQL/LocalStack, sem alterar produção. Os testes devem comprovar
  publicação em lote, falhas/retries da outbox, redelivery/DLQ, idempotência, recuperação de dispatch e falhas reais do SES.

  Schedulers serão acionados explicitamente; delays configurados para 1h evitarão execuções concorrentes. Para DLQ rápida,
  será usada uma configuração exclusiva de teste com ImmediateRetryAsyncErrorHandler, suportado pelo Spring Cloud AWS 4.0.2
  (https://docs.awspring.io/spring-cloud-aws/docs/4.0.2/reference/html/index.html).

  ## Implementação

  - Manter AuthRegistrationIntegrationTest como cobertura do Fluxo 1: usuário pendente e outbox persistidos atomicamente.
  - Criar OutboxSqsPublishingIntegrationTest, com listener desabilitado e batch-size=2:
      - registrar três usuários;
      - executar o scheduler uma vez;
      - comprovar dois eventos PUBLISHED, um PENDING e exatamente dois envelopes reais na SQS;
      - validar IDs, tipo, schema, aggregate e payload sem OTP.

  - Criar OutboxSqsPublishingFailureIntegrationTest, apontando a outbox para fila inexistente:
      - primeira execução registra tentativa, erro e nextAttemptAt;
      - segunda execução, com max-attempts=2 e retry curto, marca FAILED;
      - garantir ausência de token, dispatch e e-mail.

  - Evoluir EmailVerificationFlowIntegrationTest:
      - enviar o redelivery do evento aceito pela SQS real, não chamar diretamente o consumer;
      - configurar consumo sequencial e aguardar fila drenada;
      - validar que continuam existindo um token, um dispatch, uma tentativa e um e-mail;
      - adicionar recuperação de dispatch SENDING stale: preparar o estado, aguardar o timeout curto e publicar pela SQS,
        esperando ACCEPTED com tentativa incrementada.

  - Criar EmailVerificationConsumerFailureIntegrationTest:
      - configuração de teste com retry imediato;
      - remover temporariamente a identidade SES antes do envio para produzir rejeição real;
      - validar cinco tentativas, dispatch FAILED, usuário pendente, token aberto, ausência de e-mail e mensagem na DLQ;
      - enviar envelope com schema inválido e validar redrive para DLQ sem criar estado IAM.

  - Adicionar ao fluxo normal a recuperação transitória do SES:
      - remover a identidade SES, registrar e publicar pelo scheduler;
      - aguardar dispatch FAILED;
      - restaurar a identidade e republicar o mesmo envelope pela SQS;
      - validar ACCEPTED, duas tentativas, metadados de falha limpos e apenas um e-mail capturado.

  - Ajustar apenas a infraestrutura de testes:
      - garantir a identidade SES novamente em cada BeforeEach, evitando contaminação caso um teste falhe;
      - adicionar configuração test-only do ImmediateRetryAsyncErrorHandler;
      - consultar filas de forma não destrutiva via SqsAsyncClient; consumir a DLQ apenas após Awaitility confirmar a
        mensagem.

  - Criar EmailDispatchJpaRepositoryTest como JPA slice para as queries condicionais:
      - reservar PENDING, FAILED e SENDING stale;
      - rejeitar SENDING recente e ACCEPTED;
      - permitir markAsAccepted/markAsFailed somente a partir de SENDING.

  - Completar EmailDispatchVerificationServiceImplTest para recuperações concorrentes determinísticas:
      - dispatch duplicado recuperável e irrecuperável;
      - token referenciado ausente;
      - token aberto duplicado ausente;
      - colisão ao criar o segundo dispatch.

  ## Necessidade por cenário

   Cenário                                                       Camada
  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   Scheduler, limite do lote e envelope real na SQS              Integração obrigatória
  ────────────────────────────────────────────────────────────  ─────────────────────────────────────────────────────────────
   Fila inexistente, retry e esgotamento da outbox               Integração obrigatória
  ────────────────────────────────────────────────────────────  ─────────────────────────────────────────────────────────────
   Consumer, SES e redelivery após ACCEPTED                      Integração obrigatória
  ────────────────────────────────────────────────────────────  ─────────────────────────────────────────────────────────────
   Falha permanente do SES e DLQ                                 Integração obrigatória
  ────────────────────────────────────────────────────────────  ─────────────────────────────────────────────────────────────
   Recuperação de dispatch FAILED e SENDING stale                Integração obrigatória
  ────────────────────────────────────────────────────────────  ─────────────────────────────────────────────────────────────
   Envelope incompatível chegando à DLQ                          Integração obrigatória; um caso representativo basta
  ────────────────────────────────────────────────────────────  ─────────────────────────────────────────────────────────────
   Usuário inexistente/ativo/e-mail divergente e token           Manter unitário
   expirado
  ────────────────────────────────────────────────────────────  ─────────────────────────────────────────────────────────────
   Falha de descriptografia e conflito após envio                Manter unitário; integração seria artificial
  ────────────────────────────────────────────────────────────  ─────────────────────────────────────────────────────────────
   Colisões de constraints e transições SQL condicionais         Unidade + JPA slice
  ────────────────────────────────────────────────────────────  ─────────────────────────────────────────────────────────────
   Concorrência real entre threads                               Não criar E2E probabilístico
  ────────────────────────────────────────────────────────────  ─────────────────────────────────────────────────────────────
   SQS totalmente indisponível                                   Não derrubar o LocalStack compartilhado; fila inexistente
                                                                 cobre a fronteira
  ────────────────────────────────────────────────────────────  ─────────────────────────────────────────────────────────────
   Queda após SES aceitar e antes do markAsAccepted              Documentar limitação; não afirmar exactly-once

  ## Validação

  Executar primeiro cada nova classe isoladamente e depois:

  ./mvnw -Dgroups=integration test
  ./mvnw -Dtest=EmailDispatchJpaRepositoryTest,EmailDispatchVerificationServiceImplTest test
  ./mvnw clean verify

  Critérios: nenhuma alteração de produção, nenhum mock de infraestrutura nos testes completos, nenhuma chamada AWS real,
  nenhuma espera com Thread.sleep, filas e SES inspecionados via clientes reais e todos os cenários independentes da ordem.

  ## Premissas

  - Escopo restrito a testes e suporte test-only; eventuais bugs de produção serão reportados separadamente.
  - Idempotência significa deduplicação por eventId após ACCEPTED, não exactly-once entre SES e banco.
  - O LocalStack exige uma identidade SES configurada e conclui sua verificação imediatamente, permitindo remover/restaurar a
    identidade para simular rejeição de maneira protocolar, conforme a documentação oficial
    (https://docs.localstack.cloud/user-guide/aws/ses/).