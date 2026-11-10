CREATE TABLE exemplares_digitais (
 id BIGSERIAL PRIMARY KEY,
 livro_id BIGINT NOT NULL REFERENCES livros(id),
 nome_original VARCHAR(255) NOT NULL,
 nome_armazenado VARCHAR(80) NOT NULL UNIQUE,
 tipo_mime VARCHAR(80) NOT NULL,
 tamanho_bytes BIGINT NOT NULL CHECK (tamanho_bytes > 0),
 sha256 CHAR(64) NOT NULL,
 criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_exemplares_digitais_livro ON exemplares_digitais(livro_id);
