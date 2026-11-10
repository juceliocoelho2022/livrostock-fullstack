CREATE TABLE livros (
 id BIGSERIAL PRIMARY KEY,
 titulo VARCHAR(200) NOT NULL,
 autor VARCHAR(160) NOT NULL,
 isbn VARCHAR(20) NOT NULL UNIQUE,
 categoria VARCHAR(100) NOT NULL,
 preco NUMERIC(12,2) NOT NULL CHECK (preco >= 0),
 quantidade INTEGER NOT NULL DEFAULT 0 CHECK (quantidade >= 0),
 estoque_minimo INTEGER NOT NULL DEFAULT 0 CHECK (estoque_minimo >= 0),
 ativo BOOLEAN NOT NULL DEFAULT TRUE,
 criado_em TIMESTAMPTZ NOT NULL
);
CREATE TABLE movimentacoes (
 id BIGSERIAL PRIMARY KEY,
 livro_id BIGINT NOT NULL REFERENCES livros(id),
 tipo VARCHAR(10) NOT NULL CHECK (tipo IN ('ENTRADA','SAIDA')),
 quantidade INTEGER NOT NULL CHECK (quantidade > 0),
 saldo_apos INTEGER NOT NULL CHECK (saldo_apos >= 0),
 observacao VARCHAR(250),
 criado_em TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_livros_titulo ON livros (titulo);
CREATE INDEX idx_movimentacoes_livro_data ON movimentacoes (livro_id, criado_em DESC);
