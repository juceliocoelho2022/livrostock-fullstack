-- v0.5: contas persistidas. Nao altera livros, PDFs nem eventos de auditoria.
CREATE TABLE usuarios (
    id BIGSERIAL PRIMARY KEY,
    nome_usuario VARCHAR(80) NOT NULL UNIQUE,
    senha_hash VARCHAR(100) NOT NULL,
    perfil VARCHAR(20) NOT NULL CHECK (perfil IN ('BIBLIOTECARIO','LEITOR')),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    versao_token INTEGER NOT NULL DEFAULT 0 CHECK (versao_token >= 0),
    criado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_usuarios_perfil_ativo ON usuarios (perfil, ativo);
