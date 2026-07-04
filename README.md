# URL Shortener SaaS API

[![Pipeline CI/CD](https://github.com/LucasRibasCardoso/rest-api-url-shortener/actions/workflows/ci.yml/badge.svg)](https://github.com/LucasRibasCardoso/rest-api-url-shortener/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-6DB33F?logo=springboot&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-Wrapper-C71A36?logo=apachemaven&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16.3-4169E1?logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7.2-DC382D?logo=redis&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-ready-2496ED?logo=docker&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green)

## Objetivo do Projeto

API RESTful para encurtamento, gerenciamento e resolução de URLs, com autenticação stateless, controle de permissões, persistência distribuída e cache. O objetivo principal é oferecer uma base backend escalável para um produto SaaS de URL shortener, permitindo que usuários autenticados criem links curtos, consultem seus próprios links, removam URLs e redirecionem visitantes por meio de short codes públicos.

## Requisitos Não Funcionais

Os requisitos abaixo orientam as principais decisões de arquitetura do projeto e representam metas de robustez, escala e disponibilidade para uma operação SaaS em produção.

| Requisito | Meta técnica |
|---|---|
| Escala de escrita | Suportar **100 milhões de URLs geradas por dia**. |
| Proporção leitura/escrita | Para cada `1` operação de gravação, suportar aproximadamente `10` operações de leitura. |
| Volume de leitura | Projetar o sistema para um perfil majoritariamente read-heavy, com cache e acesso por chave. |
| Tamanho do short code | Manter a URL encurtada no menor tamanho viável. |
| Alfabeto permitido | Usar apenas números `0-9` e letras `a-z` / `A-Z`, compatível com Base62. |
| Tamanho médio armazenado | Considerar comprimento médio de **100 bytes** por URL original. |
| Retenção | Armazenar URLs pelo período mínimo de **10 anos**. |
| Disponibilidade | Operar em alta disponibilidade, com expectativa de funcionamento **24/7**. |

Em termos de capacidade média, 100 milhões de URLs/dia equivalem a aproximadamente **1.158 escritas por segundo**. Com a proporção de 10 leituras por gravação, o sistema deve acomodar cerca de **1 bilhão de leituras por dia**, ou aproximadamente **11.574 leituras por segundo** em média. Para 10 anos de retenção, apenas o conteúdo bruto das URLs originais representa cerca de **36,5 TB** antes de metadados, índices, replicação, backups e overhead do provedor.

Essas metas justificam decisões como persistência de URLs em DynamoDB por chave (`shortCode`), Redis para cache e contadores, geração de códigos com alfabeto Base62, idempotência em operações críticas e autenticação stateless para facilitar escalabilidade horizontal.

## Arquitetura Geral

O projeto é organizado no pacote raiz `com.app.url_shortener` e separado em quatro áreas principais:

- `iam`: identidade e acesso, incluindo registro, login, verificação de e-mail, refresh tokens, roles e permissions.
- `url`: criação, consulta, listagem, exclusão e resolução de URLs encurtadas.
- `security`: configuração Spring Security, JWT, conversores de autenticação, handlers e principal autenticado.
- `shared`: configurações transversais, filtros, idempotência, exceções, ProblemDetail e utilitários de persistência.

Os módulos `iam` e `url` seguem Clean Architecture com forte influência de DDD e Ports and Adapters:

```text
Controller -> Web Mapper -> Command -> Use Case -> Output Port -> Adapter -> Banco/Cache/Serviço externo <- Result <- Domain Model <-
```

Na prática:

- A camada `presentation` expõe controllers REST, DTOs, validações HTTP e mappers de entrada/saída.
- A camada `application` orquestra casos de uso, comandos, resultados e portas de saída.
- A camada `domain` concentra modelos, value objects, enums, eventos e exceções de negócio.
- A camada `infrastructure` implementa adapters para JPA/PostgreSQL, Redis, DynamoDB, Hashids, tokens e notificações.

Também existem decisões de arquitetura orientadas a eventos assíncronos. O registro e o reenvio de verificação gravam eventos de outbox no PostgreSQL; um scheduler publica esses eventos no SQS e um consumidor processa o envio do e-mail de verificação. Redirecionamentos públicos também publicam eventos no SQS para atualização assíncrona de contadores de acesso no DynamoDB.

## Stack Utilizada

### Linguagem & Framework

- Java 21
- Spring Boot 4.0.5
- Spring Web MVC
- Spring Validation
- Spring Actuator
- Spring Security
- Spring OAuth2 Resource Server
- Spring Cache
- Maven Wrapper

### Banco de Dados & Cache

- PostgreSQL 16.3 Alpine
- Flyway para migrations relacionais
- Spring Data JPA / Hibernate
- Redis 7.2 Alpine para cache de redirecionamento, idempotência, rate limiting e alocação de blocos de IDs
- DynamoDB via AWS SDK 2.42.29 e DynamoDB Enhanced Client
- LocalStack 3.0.0 para DynamoDB, SQS e SES em ambiente local

### Mensageria/Cloud

- LocalStack com DynamoDB, SQS e SES para desenvolvimento local
- AWS DynamoDB como armazenamento de URLs
- AWS SQS para eventos assíncronos de verificação de e-mail e redirecionamento
- AWS SES para envio de e-mails de verificação

### Testes & Qualidade

- JUnit 5
- Mockito
- AssertJ
- Spring Boot Test
- Spring Security Test
- Testcontainers 1.19.7
- RestAssured 6.0.0
- Testes unitários, slice tests Web/JPA/Redis e base para testes de integração
- GitHub Actions com pipeline de testes por grupos e build/push de imagem Docker no GHCR
- MapStruct 1.6.3 para mapeamento
- Lombok
- Hashids 1.0.3
- springdoc-openapi 3.0.2

## Como Rodar Localmente

### Pré-requisitos

- JDK 21
- Docker e Docker Compose
- Maven Wrapper disponível no repositório (`./mvnw`)
- Portas locais livres: `5432` para PostgreSQL, `6379` para Redis, `4566` para LocalStack e `8080` para a API

### 1. Criar configuração local

Crie a configuração local a partir do exemplo versionado:

```bash
cp .env.example .env
```

O `.env` é carregado apenas para desenvolvimento local e não deve ser versionado. Ajuste seus
valores locais antes de iniciar a infraestrutura e a aplicação.

### 2. Subir infraestrutura local

```bash
docker compose up -d
```

Esse comando sobe:

- PostgreSQL com banco `url_shortener`
- Redis
- LocalStack com DynamoDB, SQS, SES e Secrets Manager

Antes de subir a infraestrutura, crie o arquivo local de secrets a partir do exemplo:

```bash
cp secrets/application-dev.example.json secrets/application-dev.json
```

Os scripts em `localstack-init` criam automaticamente as tabelas DynamoDB, as filas SQS, a
identidade SES definida por `SES_FROM_EMAIL` e o secret
`/url-shortener/dev/application` no Secrets Manager local. Os e-mails enviados localmente ficam
disponíveis em `http://localhost:4566/_aws/ses` para inspeção.

O arquivo `secrets/application-dev.json` não é versionado. Ele deve conter as chaves sensíveis
carregadas pela aplicação via Secrets Manager:

```json
{
  "postgres.password": "...",
  "app.hashids.salt": "...",
  "app.security.jwt.secret": "...",
  "app.rate-limit.email-hash-secret": "...",
  "app.iam.email-verification.code-protection.encryption-password": "...",
  "app.iam.email-verification.code-protection.encryption-salt": "...",
  "app.iam.email-verification.code-protection.hmac-secret": "..."
}
```

Em desenvolvimento, `POSTGRES_PASSWORD` no `.env` continua sendo necessário para o Docker Compose
inicializar o PostgreSQL. O valor de `postgres.password` em `secrets/application-dev.json` é usado
pela aplicação via Secrets Manager; mantenha os dois com o mesmo valor.

Para conferir se o secret foi criado:

```bash
awslocal secretsmanager describe-secret --secret-id /url-shortener/dev/application
```

### 3. Validar ou compilar o projeto

```bash
./mvnw clean compile
```

### 4. Executar a aplicação

```bash
./mvnw spring-boot:run
```

Com `SPRING_PROFILES_ACTIVE=dev` no `.env`, a aplicação ficará disponível em:

```text
http://localhost:8080
```

### Variáveis de ambiente

O arquivo `.env.example` documenta todas as variáveis usadas pela aplicação, pelo Docker Compose e
pelos scripts de inicialização do LocalStack. Em produção, forneça essas variáveis externamente e
não use o `.env` local.

### Envio de e-mails com SES

No profile `dev`, a aplicação usa o AWS SES do LocalStack. O script
`localstack-init/create-ses-identities.sh` verifica previamente a identidade configurada em
`SES_FROM_EMAIL`; a aplicação somente consome essa identidade e não provisiona recursos SES em
runtime. As mensagens locais podem ser inspecionadas em `http://localhost:4566/_aws/ses`.

Em homologação e produção, não configure endpoint customizado. A região e as credenciais são
resolvidas pelo Spring Cloud AWS, preferencialmente por IAM Role. O domínio do endereço definido em
`SES_FROM_EMAIL` deve estar verificado na mesma região e a aplicação precisa da permissão
`ses:SendEmail`. Para enviar a destinatários não verificados, a conta da região precisa estar fora
do sandbox do SES.

O status `ACCEPTED` e o `providerMessageId` persistido representam o aceite síncrono da mensagem
pelo SES, não sua entrega ao destinatário. O fluxo possui semântica de pelo menos uma vez; se o SES
aceitar a mensagem e a resposta não chegar à aplicação, uma retentativa do SQS pode gerar envio
duplicado. Eventos de entrega, bounce e complaint não fazem parte desta integração.

## Como Executar Testes

Rodar a suíte completa:

```bash
./mvnw test
```

Rodar verificação completa, incluindo fase de integração do Maven:

```bash
./mvnw clean verify
```

Rodar grupos específicos usados pela pipeline:

```bash
./mvnw -B test -Dgroups="unit"
./mvnw -B test -Dgroups="web-slice"
./mvnw -B test -Dgroups="jpa-slice"
./mvnw -B test -Dgroups="redis-slice"
./mvnw -B verify -Dgroups="integration"
```

Tipos de teste presentes:

- Unitários (`@Tag("unit")`): domínio, use cases, adapters, mappers, JWT, handlers e utilitários sem Spring context completo.
- Web slice (`@Tag("web-slice")`): controllers com `@WebMvcTest`, validação, autorização e contratos HTTP.
- JPA slice (`@Tag("jpa-slice")`): repositórios/adapters relacionais com base `BaseDataJpaSliceTest`.
- Redis slice (`@Tag("redis-slice")`): idempotência, cache de redirecionamento, cooldown de reenvio e rate limiting com base `BaseRedisSliceTest`.
- Integração (`@Tag("integration")`): base `AbstractIntegrationTest` com Spring Boot, RestAssured, PostgreSQL, Redis e LocalStack/Testcontainers.

## Endpoints Principais

### Autenticação e IAM

Base path: `/api/v1/auth`

| Método | Rota | Descrição | Autenticação |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | Registra usuário e gera código de verificação por e-mail | Pública |
| `POST` | `/api/v1/auth/verify-email` | Valida código de 6 dígitos e ativa a conta | Pública |
| `POST` | `/api/v1/auth/login` | Autentica credenciais, retorna access token e define cookie `refreshToken` | Pública |
| `POST` | `/api/v1/auth/refresh` | Rotaciona refresh token e emite novo access token | Pública, via cookie |
| `POST` | `/api/v1/auth/logout` | Revoga refresh token e expira o cookie | Protegida |
| `POST` | `/api/v1/auth/resend-verification` | Reenvia código de verificação | Pública |

Operações protegidas pelo filtro de idempotência exigem o header:

```text
Idempotency-Key: <valor-unico-da-requisicao>
```

### URLs

Base path: `/api/v1/urls`

| Método | Rota | Descrição | Permissão |
|---|---|---|---|
| `POST` | `/api/v1/urls` | Cria uma URL encurtada | `url:create` |
| `GET` | `/api/v1/urls/{shortcode}` | Consulta detalhes de uma URL | `url:read:own` ou `url:read:any` |
| `DELETE` | `/api/v1/urls/{shortcode}` | Remove uma URL | `url:delete:own` ou `url:delete:any` |
| `GET` | `/api/v1/urls/me?limit=20&cursor=...` | Lista URLs do usuário autenticado com paginação por cursor | `url:list:own` |
| `GET` | `/api/v1/urls/users/{userId}?limit=20&cursor=...` | Lista URLs de um usuário específico | `url:list:any` |
| `GET` | `/api/v1/urls/me/ranking?rankingSize=3` | Lista as URLs ativas mais acessadas do usuário autenticado | `url:ranking:own` |

### Redirecionamento público

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/r/{shortCode}` | Resolve o short code e retorna `302 Found` para a URL original |

### Swagger/OpenAPI

Documentação interativa local:

```text
http://localhost:8080/swagger-ui.html
```

Também há uma página estática permitida pela configuração de segurança:

```text
http://localhost:8080/docs.html
```

Especificação OpenAPI:

```text
http://localhost:8080/v3/api-docs
```

## Decisões Técnicas

- **Clean Architecture e Ports and Adapters**: `iam` e `url` separam domínio, aplicação, apresentação e infraestrutura, reduzindo acoplamento com Spring, JPA, Redis e DynamoDB.
- **DDD nos agregados principais**: modelos como `UserAccount`, `RefreshToken` e `Url` encapsulam criação, restauração e invariantes básicas de negócio.
- **Autenticação stateless com JWT**: Spring Security usa OAuth2 Resource Server, `SessionCreationPolicy.STATELESS` e authorities lidas do claim `authorities` sem prefixo automático.
- **RBAC com roles e permissions**: migrations criam roles `USER` e `ADMIN`, permissões granulares para URLs e relacionamentos `role_permissions`.
- **Refresh token em cookie seguro**: login e refresh emitem cookie `refreshToken` `HttpOnly`, `Secure`, `SameSite=Strict` e escopo `/api/v1/auth`.
- **Refresh tokens persistidos como hash**: tokens brutos são gerados com `SecureRandom`, enviados ao cliente e armazenados no PostgreSQL apenas como SHA-256.
- **Rotina de limpeza agendada**: `RefreshTokenCleanupTask` remove tokens expirados/revogados antigos diariamente às 03:00.
- **Idempotência em endpoints críticos**: `IdempotencyFilter` exige `Idempotency-Key`, evita concorrência duplicada e reutiliza respostas concluídas por 24 horas via Redis.
- **Redis como componente operacional**: além de cache de redirecionamento, Redis sustenta idempotência, rate limiting com Bucket4j e alocação de blocos de IDs para geração de short codes.
- **Geração eficiente de short codes**: IDs numéricos são alocados em blocos no Redis e codificados com Hashids/Base62, reduzindo chamadas ao contador central.
- **DynamoDB para URLs**: registros de URL são persistidos em tabela DynamoDB por `shortCode`, com índices secundários para listagem por usuário, filtro por status e ranking de URLs ativas.
- **Cache de resolução por short code**: consultas por short code usam Redis com TTLs distintos para URLs ativas, removidas e não encontradas.
- **Outbox transacional com SQS**: eventos de verificação de e-mail são persistidos no PostgreSQL, publicados por scheduler e consumidos via SQS, reduzindo acoplamento entre transação de IAM e envio externo.
- **Contadores assíncronos de redirecionamento**: acessos públicos publicam eventos no SQS e o consumidor agrupa mensagens por `shortCode` para incrementar contadores no DynamoDB.
- **Rate limiting por política**: Bucket4j com Redis aplica limites para registro, login, verificação, reenvio e criação de URLs por plano.
- **Problem Details centralizado**: `GlobalExceptionHandler` e `ProblemDetailFactory` padronizam erros HTTP com `errorCode`, validações de campo e categorias como validation, conflict, forbidden e infrastructure.
- **Migrations versionadas**: PostgreSQL é versionado por Flyway em `src/main/resources/db/migration`.
- **Pipeline CI/CD**: GitHub Actions executa testes unitários, slice tests, integração e publica imagem Docker no GitHub Container Registry para a branch `main`.
- **Container runtime enxuto**: Dockerfile usa Temurin 21 JRE Alpine, layered JAR, usuário não-root e opções JVM configuradas.
