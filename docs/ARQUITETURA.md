# Arquitetura e DER — LivroStock

## Arquitetura lógica

```mermaid
flowchart LR
  U[Usuário no navegador] --> F[React + TypeScript / Vite :5173]
  F -->|Proxy /api| A[Spring Boot :8081]
  A --> S[Regras de negócio]
  S --> J[Spring Data JPA]
  J --> DB[(PostgreSQL :5432 no contêiner)]
  S --> FS[(Armazenamento persistente de PDFs)]
  F -->|GET application/pdf| A
  M[Flyway] --> DB
```

Em desenvolvimento, a publicação PostgreSQL usada para evitar conflito de portas no Windows foi `127.0.0.1:5434:5432`. Os arquivos digitais são armazenados fora do banco; o PostgreSQL mantém metadados e associação ao livro.

## DER — modelo físico existente (v0.1 + backend inicial v0.2)

```mermaid
erDiagram
  LIVROS ||--o{ MOVIMENTACOES : registra
  LIVROS ||--o{ EXEMPLARES_DIGITAIS : possui

  LIVROS {
    bigint id PK
    varchar titulo
    varchar autor
    varchar isbn UK
    varchar categoria
    numeric preco
    int quantidade
    int estoque_minimo
    boolean ativo
    timestamptz criado_em
  }

  MOVIMENTACOES {
    bigint id PK
    bigint livro_id FK
    varchar tipo
    int quantidade
    int saldo_apos
    varchar observacao
    timestamptz criado_em
  }

  EXEMPLARES_DIGITAIS {
    bigint id PK
    bigint livro_id FK
    varchar nome_original
    varchar nome_armazenado UK
    varchar tipo_mime
    bigint tamanho_bytes
    char sha256
    timestamptz criado_em
  }
```

> A tabela `EXEMPLARES_DIGITAIS` é introduzida pela migration `V2__create_exemplares_digitais.sql` na branch da v0.2. O DER não representa categorias/editoras/fornecedores como tabelas existentes: ainda são planejadas. A categoria de `LIVROS` é textual nesta etapa.

## DER conceitual — direção de evolução

```mermaid
erDiagram
  CATEGORIAS ||--o{ LIVROS : classifica
  EDITORAS ||--o{ LIVROS : publica
  LIVROS ||--o{ MOVIMENTACOES : registra
  LIVROS ||--o{ EXEMPLARES_DIGITAIS : possui
  FORNECEDORES o|--o{ MOVIMENTACOES : fornece
  USUARIOS o|--o{ MOVIMENTACOES : executa
  USUARIOS o|--o{ AUDITORIAS : gera
  USUARIOS o|--o{ ACESSOS_DIGITAIS : realiza
  EXEMPLARES_DIGITAIS ||--o{ ACESSOS_DIGITAIS : recebe
```

**Importante:** todo o segundo diagrama é uma proposta arquitetural. Nenhuma dessas novas tabelas deve ser criada sem migration e testes de compatibilidade.

## Decisões de engenharia

1. **Separação de responsabilidades:** frontend não calcula nem altera saldo de estoque diretamente; regras vivem na API.
2. **Evolução do banco:** Flyway cria estruturas incrementalmente; JPA com `ddl-auto: validate`.
3. **Armazenamento de arquivos:** filesystem configurável em dev; em produção, adotar armazenamento privado como S3/MinIO, com credenciais e acesso restritos.
4. **Segurança dos PDFs:** a v0.2 inicial valida tamanho, extensão e cabeçalho; isso **não é** inspeção antimalware nem autenticação. A API não deve ser exposta em rede pública.
5. **Consistência:** transações de banco e filesystem não são atômicas. Implementar fluxo de gravação temporária, confirmação transacional e limpeza periódica de órfãos.
6. **Auditoria e LGPD:** registrar ações e proteger dados pessoais quando usuários e permissões forem implementados.
7. **Concorrência:** futuras regras transacionais para impedir corrida entre saídas simultâneas.
8. **Observabilidade:** health, logs estruturados, métricas, rastreamento e alertas no roadmap.
9. **Deploy:** proxy reverso, HTTPS, segredos externos ao código e backups consistentes do banco + storage.

Veja [regras de negócio](REGRAS_DE_NEGOCIO.md) e [API](API.md).
