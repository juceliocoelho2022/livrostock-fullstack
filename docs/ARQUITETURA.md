# Arquitetura

## Visão geral

```mermaid
flowchart TD
  U[Usuário] --> F[Frontend React + TypeScript - 5173]
  F -->|Vite Proxy /api| B[API Java 21 Spring Boot - 8081]
  B --> J[Spring Data JPA / Hibernate]
  J --> P[(PostgreSQL - 5432)]
  B --> S[Swagger / OpenAPI]
  M[Flyway] --> P
```

## Decisões

- Monorepo: facilita revisão e entrega do frontend e backend juntos.
- Backend concentra validação de estoque e persistência; frontend apenas consome a API.
- O saldo é alterado exclusivamente por movimentações. Não permitir que o formulário de edição altere o saldo diretamente.
- Migrações SQL via Flyway e `ddl-auto: validate`.
- Proxy de desenvolvimento evita depender de CORS para `localhost:5173`.
- Para produção, usar proxy reverso, autenticação, HTTPS e banco gerenciado.

## Modelo de domínio

```mermaid
erDiagram
  LIVRO ||--o{ MOVIMENTACAO : possui
  LIVRO {
    bigint id PK
    string titulo
    string autor
    string isbn
    decimal preco
    integer quantidade
    integer estoque_minimo
    boolean ativo
  }
  MOVIMENTACAO {
    bigint id PK
    bigint livro_id FK
    string tipo
    integer quantidade
    integer saldo_apos
    timestamp criado_em
  }
```
